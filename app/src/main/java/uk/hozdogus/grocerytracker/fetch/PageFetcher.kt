package uk.hozdogus.grocerytracker.fetch

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.webkit.CookieManager
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import org.json.JSONTokener
import uk.hozdogus.grocerytracker.data.Shop
import kotlin.coroutines.resume
import kotlin.math.roundToInt

/** What a page load produced. Prices are in pence. */
sealed class FetchResult {
    data class Success(
        val name: String,
        val imageUrl: String?,
        val price: Int,
        /** Asda Rollback (or other) "was" price, when the page shows one. */
        val wasPrice: Int? = null,
        /** Sainsbury's Nectar price, when the page shows one. */
        val nectarPrice: Int? = null,
        /** What the offer-price detection saw, for the diagnostics screen. */
        val debug: String = "",
    ) : FetchResult()

    /** The shop showed a block or robot check instead of the product. */
    data class Blocked(val details: String) : FetchResult()

    data class Failed(val reason: String, val details: String) : FetchResult()
}

/**
 * Loads a product page in an off-screen WebView and reads the price from its JSON-LD product data.
 * Uses the same WebView setup the test app proved works for both shops.
 */
class PageFetcher(context: Context) {

    private val appContext = context.applicationContext

    suspend fun fetch(url: String, shop: Shop): FetchResult = withContext(Dispatchers.Main) {
        // Backstop in case the WebView stops answering; normal give-up happens after about 30 seconds.
        withTimeoutOrNull(OVERALL_TIMEOUT_MS) {
            suspendCancellableCoroutine<FetchResult> { cont ->
                val session = Session(url, shop) { result -> if (cont.isActive) cont.resume(result) }
                cont.invokeOnCancellation { session.finish(null) }
                session.start()
            }
        } ?: FetchResult.Failed("The page took too long to load.", "Link: $url")
    }

    private inner class Session(val url: String, val shop: Shop, val onDone: (FetchResult) -> Unit) {
        private val handler = Handler(Looper.getMainLooper())
        private var web: WebView? = null
        private var tries = 0
        private var apiWaits = 0
        private var httpStatus = 0
        private var networkError: String? = null
        private var lastRead: JSONObject? = null
        private var finished = false

        @SuppressLint("SetJavaScriptEnabled")
        fun start() {
            val w = WebView(appContext)
            web = w
            // Give the page a phone-sized viewport even though it is never shown.
            w.layout(0, 0, 1080, 2400)
            w.settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                userAgentString = userAgentString.replace("; wv", "")
            }
            CookieManager.getInstance().setAcceptCookie(true)
            CookieManager.getInstance().setAcceptThirdPartyCookies(w, true)
            w.webViewClient = object : WebViewClient() {
                override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
                    httpStatus = 0
                    networkError = null
                }

                override fun onReceivedHttpError(
                    view: WebView, request: WebResourceRequest, response: WebResourceResponse,
                ) {
                    if (request.isForMainFrame) httpStatus = response.statusCode
                }

                override fun onReceivedError(
                    view: WebView, request: WebResourceRequest, error: WebResourceError,
                ) {
                    if (request.isForMainFrame) networkError = error.description?.toString()
                }
            }
            w.loadUrl(url)
            // Poll rather than wait for onPageFinished: tracking scripts can keep a page "loading" for ages.
            handler.postDelayed(::poll, FIRST_POLL_MS)
        }

        private fun poll() {
            val w = web ?: return
            tries++
            w.evaluateJavascript(EXTRACT_JS) { value ->
                if (finished) return@evaluateJavascript
                val read = decode(value)
                if (read != null) lastRead = read
                val success = read?.let(::toSuccess)
                // On Sainsbury's, give the in-page product data request a few seconds to bring the Nectar price.
                val waitForApi = success != null && shop == Shop.SAINSBURYS &&
                    read?.isNull("api") == true && apiWaits < MAX_API_WAITS
                when {
                    waitForApi -> {
                        apiWaits++
                        handler.postDelayed(::poll, API_POLL_MS)
                    }
                    success != null -> finish(success)
                    tries >= MAX_TRIES -> finish(giveUp())
                    else -> handler.postDelayed(::poll, POLL_MS)
                }
            }
        }

        private fun giveUp(): FetchResult {
            val read = lastRead
            val details = buildString {
                append("Link: ").append(url)
                if (httpStatus != 0) append("\nHTTP status: ").append(httpStatus)
                networkError?.let { append("\nNetwork error: ").append(it) }
                append("\nPage title: ").append(read?.str("title") ?: "(none)")
                val product = read?.optJSONObject("product")
                if (product != null) append("\nProduct data: ").append(product.toString().take(300))
            }
            return when {
                read?.optBoolean("blocked") == true || httpStatus == 403 -> FetchResult.Blocked(details)
                networkError != null -> FetchResult.Failed("Couldn't load the page.", details)
                read?.optJSONObject("product")?.let(::foreignCurrency) != null -> FetchResult.Failed(
                    "The price isn't in pounds (${read.optJSONObject("product")?.let(::foreignCurrency)}).", details,
                )
                read?.optJSONObject("product") != null ->
                    FetchResult.Failed("Found the product but no price (it may be out of stock).", details)
                else -> FetchResult.Failed(
                    "Couldn't find a price on the page. This app reads the product data shops publish for search " +
                        "engines; this page may not have any, or may need you to pick a store first.",
                    details,
                )
            }
        }

        fun finish(result: FetchResult?) {
            if (finished) return
            finished = true
            handler.removeCallbacksAndMessages(null)
            web?.apply {
                stopLoading()
                destroy()
            }
            web = null
            CookieManager.getInstance().flush()
            if (result != null) onDone(result)
        }
    }

    private fun decode(value: String?): JSONObject? = try {
        JSONObject(JSONTokener(value).nextValue() as String)
    } catch (e: Exception) {
        null
    }

    private fun toSuccess(read: JSONObject): FetchResult.Success? {
        val product = read.optJSONObject("product") ?: return null
        if (foreignCurrency(product) != null) return null
        val price = product.str("price")?.let(::toPence) ?: return null
        if (price <= 0) return null
        val name = product.str("name") ?: read.str("ogTitle") ?: read.str("title") ?: "Unnamed item"
        val image = product.str("image")?.takeIf { it.startsWith("http") }
            ?: read.str("ogImage")?.takeIf { it.startsWith("http") }

        val offers = read.optJSONObject("offers")
        val api = read.optJSONObject("api")
        // A "was" price must be above the current price and a Nectar price below it; anything else
        // is a unit price or another product's price that happened to be nearby.
        val was = (product.str("ldWas") ?: offers?.str("was"))?.let(::toPence)?.takeIf { it > price }
        val nectar = (api?.str("nectar") ?: offers?.str("nectar"))?.let(::toPence)?.takeIf { it in 1 until price }

        val debug = buildString {
            append("Price (").append(product.str("source") ?: "JSON-LD").append("): ").append(product.str("price"))
            append("\nWas price (JSON-LD): ").append(product.str("ldWas") ?: "none")
            append("\nText around the price: ").append(offers?.str("context") ?: "(price element not found)")
            append("\nWas price in that text: ").append(offers?.str("was") ?: "none")
            append("\nNectar price in that text: ").append(offers?.str("nectar") ?: "none")
            if (!read.isNull("api")) append("\nSainsbury's product data: ").append(api.toString().take(400))
            append("\n\nSaved: price ").append(price).append("p, was ")
                .append(was?.let { "${it}p" } ?: "none").append(", Nectar ")
                .append(nectar?.let { "${it}p" } ?: "none")
        }
        return FetchResult.Success(name.trim(), image, price, was, nectar, debug)
    }

    /** "£1.26", "1,234.00" and "1.26" all read as pounds. */
    private fun toPence(text: String): Int? =
        text.replace(Regex("[^0-9.]"), "").toDoubleOrNull()?.let { (it * 100).roundToInt() }

    /** The currency code when the page says the price isn't in pounds, else null. */
    private fun foreignCurrency(product: JSONObject): String? =
        product.str("currency")?.uppercase()?.takeIf { it != "GBP" && it != "£" }

    /** optString() turns JSON null into "null"; this returns real null for missing or blank values. */
    private fun JSONObject.str(key: String): String? =
        if (isNull(key)) null else optString(key).takeIf { it.isNotBlank() && it != "null" }

    companion object {
        private const val FIRST_POLL_MS = 3_000L
        private const val POLL_MS = 2_000L
        private const val MAX_TRIES = 12
        private const val API_POLL_MS = 1_000L
        private const val MAX_API_WAITS = 4
        private const val OVERALL_TIMEOUT_MS = 45_000L

        /**
         * Returns {title, blocked, product: {name, image, price, ldWas}, offers: {context, was, nectar},
         * api: {status, nectar, price, promos, error}, ogTitle, ogImage} as a JSON string.
         *
         * "offers" is read from the text around the first element showing the JSON-LD price, which is
         * the main product's price box rather than the carousels further down the page. "api" is the
         * Sainsbury's product data request, made from the page itself; it stays null until it answers.
         */
        private val EXTRACT_JS = """
            (function(){
              var r = {title: document.title || '', blocked: false, product: null, offers: null, api: null, ogTitle: null, ogImage: null};
              var t = document.body ? document.body.innerText.slice(0, 3000) : '';
              r.blocked = /access denied|are you a robot|captcha|request blocked|verify you are human|pardon our interruption|unusual traffic/i.test(t + ' ' + r.title);
              function first(x){ return Array.isArray(x) ? x[0] : x; }
              function list(x){ return x == null ? [] : (Array.isArray(x) ? x : [x]); }
              function hasType(n, t){ var ty = n['@type']; return ty === t || (Array.isArray(ty) && ty.indexOf(t) >= 0); }
              function walk(n){
                if (!n || typeof n !== 'object') return null;
                if (Array.isArray(n)) { for (var i = 0; i < n.length; i++) { var f = walk(n[i]); if (f) return f; } return null; }
                if (hasType(n, 'Product')) return n;
                // A product with sizes or flavours: use the first variant's offer, keeping the group's name.
                if (hasType(n, 'ProductGroup')) {
                  var v = first(n.hasVariant);
                  if (n.offers || !v) return n;
                  return {name: n.name || v.name, image: n.image || v.image, offers: v.offers};
                }
                return walk(n['@graph']) || walk(n.mainEntity);
              }
              var scripts = document.querySelectorAll('script[type="application/ld+json"]');
              for (var i = 0; i < scripts.length && !r.product; i++) {
                try {
                  var p = walk(JSON.parse(scripts[i].textContent));
                  if (!p) continue;
                  var o = first(p.offers) || {};
                  var price = o.price != null ? o.price : o.lowPrice;
                  if (price == null && o.priceSpecification) price = first(o.priceSpecification).price;
                  var was = null;
                  list(p.offers).forEach(function(of){
                    list(of.priceSpecification).forEach(function(s){
                      if (s && s.price != null && /strikethrough|listprice/i.test(String(s.priceType || ''))) was = String(s.price);
                    });
                  });
                  var img = first(p.image);
                  if (img && typeof img === 'object') img = img.url || img.contentUrl;
                  r.product = {name: p.name || null, image: img || null, price: price != null ? String(price) : null,
                               ldWas: was, currency: o.priceCurrency || null, source: 'JSON-LD'};
                } catch (e) {}
              }
              // Pages without JSON-LD often still carry standard price tags.
              if (!r.product || !r.product.price) {
                var attr = function(sel){ var e = document.querySelector(sel); return e ? (e.getAttribute('content') || '').trim() : ''; };
                var mp = attr('meta[property="product:price:amount"]') || attr('meta[property="og:price:amount"]') ||
                         attr('meta[itemprop="price"]') || attr('[itemprop="price"][content]');
                if (mp) {
                  r.product = {
                    name: (r.product && r.product.name) || null, image: (r.product && r.product.image) || null,
                    price: mp, ldWas: null, source: 'meta tag',
                    currency: attr('meta[property="product:price:currency"]') || attr('meta[property="og:price:currency"]') ||
                              attr('meta[itemprop="priceCurrency"]') || attr('[itemprop="priceCurrency"][content]') || null
                  };
                }
              }
              if (r.product && r.product.price && document.body) {
                var v = Number(String(r.product.price).replace(/[^\d.]/g, ''));
                var shown = ['£' + v.toFixed(2)];
                if (v < 1) shown.push(Math.round(v * 100) + 'p');
                var el = null, all = document.body.getElementsByTagName('*');
                for (var j = 0; j < all.length && !el; j++) {
                  var tc = all[j].textContent;
                  if (tc && tc.length <= 24 && shown.indexOf(tc.replace(/\s/g, '')) >= 0) el = all[j];
                }
                if (el) {
                  var box = el;
                  for (var k = 0; k < 5 && box.parentElement; k++) {
                    if ((box.parentElement.innerText || '').length > 600) break;
                    box = box.parentElement;
                  }
                  var ctx = (box.innerText || '').replace(/\s+/g, ' ').trim();
                  r.offers = {context: ctx.slice(0, 400), was: null, nectar: null};
                  var m = ctx.match(/\bwas\s*£\s?(\d+(?:\.\d{1,2})?)/i);
                  if (m) r.offers.was = m[1];
                  else if ((m = ctx.match(/\bwas\s*(\d{1,2})p\b/i))) r.offers.was = String(Number(m[1]) / 100);
                  m = ctx.match(/£\s?(\d+\.\d{2})\s*(?:with\s+)?nectar/i) || ctx.match(/nectar\s*(?:price)?\s*:?\s*£\s?(\d+\.\d{2})/i);
                  if (m) r.offers.nectar = m[1];
                  else if ((m = ctx.match(/\b(\d{1,2})p\s*(?:with\s+)?nectar/i))) r.offers.nectar = String(Number(m[1]) / 100);
                }
              }
              if (location.hostname.indexOf('sainsburys.co.uk') >= 0 && r.product) {
                var g = window.__gtApi || (window.__gtApi = {state: 'new'});
                if (g.state === 'new') {
                  g.state = 'loading';
                  var slug = location.pathname.split('/').filter(Boolean).pop();
                  fetch('/groceries-api/gol-services/product/v1/product?filter[product_seo_url]=gb%2Fgroceries%2F' + encodeURIComponent(slug),
                        {credentials: 'include', headers: {'Accept': 'application/json'}})
                    .then(function(res){ g.status = res.status; return res.json(); })
                    .then(function(d){
                      var p = d && d.products && d.products[0];
                      if (!p) { g.error = 'no product in response'; }
                      else {
                        g.nectar = p.nectar_price && p.nectar_price.retail_price != null ? String(p.nectar_price.retail_price) : null;
                        g.price = p.retail_price && p.retail_price.price != null ? String(p.retail_price.price) : null;
                        g.promos = (p.promotions || []).map(function(x){ return x.strap_line; }).join(' | ').slice(0, 200);
                      }
                      g.state = 'done';
                    })
                    .catch(function(e){ g.error = String(e).slice(0, 120); g.state = 'done'; });
                }
                if (g.state === 'done') r.api = {status: g.status || 0, nectar: g.nectar || null, price: g.price || null, promos: g.promos || null, error: g.error || null};
              }
              var mt = document.querySelector('meta[property="og:title"]');
              if (mt) r.ogTitle = mt.getAttribute('content');
              mt = document.querySelector('meta[property="og:image"]');
              if (mt) r.ogImage = mt.getAttribute('content');
              return JSON.stringify(r);
            })()
        """.trimIndent()
    }
}
