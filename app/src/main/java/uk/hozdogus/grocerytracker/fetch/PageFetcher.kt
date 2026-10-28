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
import org.json.JSONObject
import org.json.JSONTokener
import kotlin.coroutines.resume
import kotlin.math.roundToInt

/** What a page load produced. Prices are in pence. */
sealed class FetchResult {
    data class Success(val name: String, val imageUrl: String?, val price: Int) : FetchResult()

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

    suspend fun fetch(url: String): FetchResult = withContext(Dispatchers.Main) {
        suspendCancellableCoroutine { cont ->
            val session = Session(url) { result -> if (cont.isActive) cont.resume(result) }
            cont.invokeOnCancellation { session.finish(null) }
            session.start()
        }
    }

    private inner class Session(val url: String, val onDone: (FetchResult) -> Unit) {
        private val handler = Handler(Looper.getMainLooper())
        private var web: WebView? = null
        private var tries = 0
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
                when {
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
                read?.optJSONObject("product") != null ->
                    FetchResult.Failed("Found the product but no price (it may be out of stock).", details)
                else -> FetchResult.Failed("Couldn't find the product's price on the page.", details)
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
        val price = product.str("price")?.toDoubleOrNull() ?: return null
        if (price <= 0) return null
        val name = product.str("name") ?: read.str("ogTitle") ?: read.str("title") ?: "Unnamed item"
        val image = product.str("image")?.takeIf { it.startsWith("http") }
            ?: read.str("ogImage")?.takeIf { it.startsWith("http") }
        return FetchResult.Success(name.trim(), image, (price * 100).roundToInt())
    }

    /** optString() turns JSON null into "null"; this returns real null for missing or blank values. */
    private fun JSONObject.str(key: String): String? =
        if (isNull(key)) null else optString(key).takeIf { it.isNotBlank() }

    companion object {
        private const val FIRST_POLL_MS = 3_000L
        private const val POLL_MS = 2_000L
        private const val MAX_TRIES = 12

        /** Returns {title, blocked, product: {name, image, price}, ogTitle, ogImage} as a JSON string. */
        private val EXTRACT_JS = """
            (function(){
              var r = {title: document.title || '', blocked: false, product: null, ogTitle: null, ogImage: null};
              var t = document.body ? document.body.innerText.slice(0, 3000) : '';
              r.blocked = /access denied|are you a robot|captcha|request blocked|verify you are human|pardon our interruption|unusual traffic/i.test(t + ' ' + r.title);
              function first(x){ return Array.isArray(x) ? x[0] : x; }
              function isProduct(n){ var ty = n['@type']; return ty === 'Product' || (Array.isArray(ty) && ty.indexOf('Product') >= 0); }
              function walk(n){
                if (!n || typeof n !== 'object') return null;
                if (Array.isArray(n)) { for (var i = 0; i < n.length; i++) { var f = walk(n[i]); if (f) return f; } return null; }
                if (isProduct(n)) return n;
                return n['@graph'] ? walk(n['@graph']) : null;
              }
              var scripts = document.querySelectorAll('script[type="application/ld+json"]');
              for (var i = 0; i < scripts.length && !r.product; i++) {
                try {
                  var p = walk(JSON.parse(scripts[i].textContent));
                  if (!p) continue;
                  var o = first(p.offers) || {};
                  var price = o.price != null ? o.price : o.lowPrice;
                  if (price == null && o.priceSpecification) price = first(o.priceSpecification).price;
                  var img = first(p.image);
                  if (img && typeof img === 'object') img = img.url || img.contentUrl;
                  r.product = {name: p.name || null, image: img || null, price: price != null ? String(price) : null};
                } catch (e) {}
              }
              var m = document.querySelector('meta[property="og:title"]');
              if (m) r.ogTitle = m.getAttribute('content');
              m = document.querySelector('meta[property="og:image"]');
              if (m) r.ogImage = m.getAttribute('content');
              return JSON.stringify(r);
            })()
        """.trimIndent()
    }
}
