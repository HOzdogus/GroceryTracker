package uk.hozdogus.grocerytracker.fetch

import uk.hozdogus.grocerytracker.data.Shop
import java.net.URI

/** Finds an Asda or Sainsbury's product link in shared or pasted text. */
object ProductUrl {

    sealed class Result {
        data class Ok(val url: String, val shop: Shop) : Result()
        data class Invalid(val reason: String) : Result()
    }

    private val shopLink = Regex(
        """(?<![a-z0-9.-])(?:https?://)?(?:[a-z0-9-]+\.)*(?:asda\.com|sainsburys\.co\.uk)/\S*""",
        RegexOption.IGNORE_CASE,
    )
    private val anyLink = Regex("""https?://\S+""", RegexOption.IGNORE_CASE)

    fun parse(text: String): Result {
        val found = shopLink.find(text)?.value
            ?: return Result.Invalid(
                if (anyLink.containsMatchIn(text)) "That link isn't from Asda or Sainsbury's."
                else "No link found."
            )

        // Drop tracking parameters and fragments, plus punctuation that often trails a shared link.
        var raw = found.substringBefore('?').substringBefore('#').trimEnd('.', ',', ')', '"', '\'', '/')
        if (!raw.startsWith("http", ignoreCase = true)) raw = "https://$raw"

        val uri = try {
            URI(raw)
        } catch (e: Exception) {
            return Result.Invalid("That link couldn't be read.")
        }
        val host = uri.host?.lowercase() ?: return Result.Invalid("That link couldn't be read.")
        val path = uri.rawPath.orEmpty()

        return when {
            host == "asda.com" || host == "www.asda.com" ->
                if (path.startsWith("/groceries/product/")) Result.Ok("https://www.asda.com$path", Shop.ASDA)
                else Result.Invalid("That Asda link isn't a product page.")

            host == "sainsburys.co.uk" || host == "www.sainsburys.co.uk" ->
                if (path.contains("/product/")) Result.Ok("https://www.sainsburys.co.uk$path", Shop.SAINSBURYS)
                else Result.Invalid("That Sainsbury's link isn't a product page.")

            else -> Result.Invalid("That link isn't from Asda or Sainsbury's.")
        }
    }
}
