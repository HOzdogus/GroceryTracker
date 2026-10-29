package uk.hozdogus.grocerytracker.fetch

import uk.hozdogus.grocerytracker.data.Shop
import java.net.URI

/** Finds a product link in shared or pasted text and tidies it into the address to track. */
object ProductUrl {

    sealed class Result {
        data class Ok(val url: String, val shop: Shop) : Result()
        data class Invalid(val reason: String) : Result()
    }

    private val link = Regex("""https?://\S+""", RegexOption.IGNORE_CASE)

    /** A link pasted without "https://", e.g. "www.asda.com/groceries/…". Needs a path so plain words don't match. */
    private val bareLink = Regex("""(?<![\w.@-])(?:[a-z0-9-]+\.)+[a-z]{2,}/\S*""", RegexOption.IGNORE_CASE)

    /** Query parameters that only track where a click came from; anything else may identify the product. */
    private val trackingParam = Regex(
        """^(utm_\w+|gclid|gclsrc|dclid|fbclid|msclkid|mc_cid|mc_eid|_ga|_gl|ref|ref_|referrer|srsltid|tag|igshid|si)$""",
        RegexOption.IGNORE_CASE,
    )

    fun parse(text: String): Result {
        val found = link.find(text)?.value ?: bareLink.find(text)?.value
            ?: return Result.Invalid("No link found.")

        // Drop punctuation that often trails a shared link.
        var raw = found.trimEnd('.', ',', ')', '"', '\'', '!', '>')
        if (!raw.startsWith("http", ignoreCase = true)) raw = "https://$raw"

        val uri = try {
            URI(raw)
        } catch (e: Exception) {
            return Result.Invalid("That link couldn't be read.")
        }
        val host = uri.host?.lowercase()
        if (host == null || !host.contains('.')) return Result.Invalid("That link couldn't be read.")

        val shop = Shop.forHost(host)
        val path = uri.rawPath.orEmpty().trimEnd('/').ifEmpty { "/" }
        shop.productPath?.let { isProduct ->
            if (!isProduct(path)) return Result.Invalid("That ${shop.label} link isn't a product page.")
        }

        val query = if (shop.dropsQuery) null else keptQuery(uri.rawQuery)
        val url = "https://" + (shop.canonicalHost ?: host) + path + (query?.let { "?$it" } ?: "")
        return Result.Ok(url, shop)
    }

    private fun keptQuery(rawQuery: String?): String? =
        rawQuery?.split('&')
            ?.filter { it.isNotEmpty() && !trackingParam.matches(it.substringBefore('=')) }
            ?.joinToString("&")
            ?.ifEmpty { null }
}
