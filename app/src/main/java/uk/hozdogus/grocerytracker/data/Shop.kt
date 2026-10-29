package uk.hozdogus.grocerytracker.data

import androidx.room.TypeConverter

/**
 * The shop an item comes from. Well-known shops have a fixed [key] (Asda and Sainsbury's keep the
 * "ASDA" and "SAINSBURYS" keys they were stored with before any website could be added); any other
 * website's key is its domain, such as "boots.com".
 */
data class Shop(val key: String) {

    private val known: Known? get() = KNOWN.firstOrNull { it.key == key }

    val label: String get() = known?.label ?: key

    /** Badge and chart colour (ARGB). Other websites get a steady colour picked from their domain. */
    val color: Int get() = known?.color ?: PALETTE[Math.floorMod(key.hashCode(), PALETTE.size)]

    /** Whether this shop's product links have a known shape, so other pages can be turned away early. */
    internal val productPath: ((String) -> Boolean)? get() = known?.productPath

    /** Whether this shop's links are known to work without their query string (tracking parameters). */
    internal val dropsQuery: Boolean get() = known?.dropsQuery ?: false

    /** The address to save for a product page on this shop, e.g. always "www." for Asda. */
    internal val canonicalHost: String? get() = known?.canonicalHost

    private class Known(
        val key: String,
        val label: String,
        color: Long,
        val domains: List<String>,
        val canonicalHost: String? = null,
        val dropsQuery: Boolean = false,
        val productPath: ((String) -> Boolean)? = null,
    ) {
        val color: Int = color.toInt()
    }

    companion object {
        val ASDA = Shop("ASDA")
        val SAINSBURYS = Shop("SAINSBURYS")

        private val KNOWN = listOf(
            Known(
                "ASDA", "Asda", 0xFF68A51C, listOf("asda.com"), "www.asda.com", dropsQuery = true,
                productPath = { it.startsWith("/groceries/product/") },
            ),
            Known(
                "SAINSBURYS", "Sainsbury's", 0xFFF06C00, listOf("sainsburys.co.uk"), "www.sainsburys.co.uk",
                dropsQuery = true, productPath = { it.contains("/product/") },
            ),
            Known("TESCO", "Tesco", 0xFF00539F, listOf("tesco.com")),
            Known("MORRISONS", "Morrisons", 0xFF00563F, listOf("morrisons.com")),
            Known("WAITROSE", "Waitrose", 0xFF4E7A27, listOf("waitrose.com")),
            Known("OCADO", "Ocado", 0xFF6C2C91, listOf("ocado.com")),
            Known("ALDI", "Aldi", 0xFF00005F, listOf("aldi.co.uk")),
            Known("LIDL", "Lidl", 0xFF0050AA, listOf("lidl.co.uk")),
            Known("ICELAND", "Iceland", 0xFFD2212E, listOf("iceland.co.uk")),
            Known("COOP", "Co-op", 0xFF00A1CC, listOf("coop.co.uk")),
            Known("MS", "M&S", 0xFF000000, listOf("marksandspencer.com")),
            Known("BOOTS", "Boots", 0xFF05054B, listOf("boots.com")),
            Known("SUPERDRUG", "Superdrug", 0xFFE6007E, listOf("superdrug.com")),
            Known("AMAZON", "Amazon", 0xFFE47911, listOf("amazon.co.uk")),
        )

        /** Mid-tone colours that read with white badge text in light and dark themes. */
        private val PALETTE = intArrayOf(
            0xFF5E35B1.toInt(), 0xFF00897B.toInt(), 0xFF6D4C41.toInt(), 0xFF3949AB.toInt(),
            0xFFC2185B.toInt(), 0xFF546E7A.toInt(), 0xFF7CB342.toInt(), 0xFFEF6C00.toInt(),
        )

        /** The shop for a web address's host, e.g. "groceries.morrisons.com" is Morrisons. */
        fun forHost(host: String): Shop {
            val h = host.lowercase().removeSuffix(".")
            KNOWN.firstOrNull { k -> k.domains.any { h == it || h.endsWith(".$it") } }?.let { return Shop(it.key) }
            return Shop(h.removePrefix("www."))
        }
    }

    /** Stores a shop as its key, which is what the column held before shops became open-ended. */
    class Converter {
        @TypeConverter fun toKey(shop: Shop): String = shop.key
        @TypeConverter fun fromKey(key: String): Shop = Shop(key)
    }
}
