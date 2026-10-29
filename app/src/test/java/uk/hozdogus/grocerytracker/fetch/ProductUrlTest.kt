package uk.hozdogus.grocerytracker.fetch

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import uk.hozdogus.grocerytracker.data.Shop

class ProductUrlTest {

    private val asda = "https://www.asda.com/groceries/product/kids-yogurts-fromage-frais/" +
        "yeo-valley-organic-fruity-favourites-whole-milk-yogurt-4-x-85g/7518557"
    private val sainsburys = "https://www.sainsburys.co.uk/groceries/product/" +
        "yeo-valley-organic-little-yeos-fruity-favourites-kids-yogurt-selection-4x85g"

    private fun ok(text: String) = ProductUrl.parse(text) as ProductUrl.Result.Ok

    @Test fun plainAsdaLink() =
        assertEquals(ProductUrl.Result.Ok(asda, Shop.ASDA), ProductUrl.parse(asda))

    @Test fun stripsAllParametersForAsdaAndSainsburys() =
        assertEquals(
            ProductUrl.Result.Ok(sainsburys, Shop.SAINSBURYS),
            ProductUrl.parse("$sainsburys?utm_source=app&cid=123#reviews"),
        )

    @Test fun findsLinkInsideSharedText() =
        assertEquals(
            ProductUrl.Result.Ok(asda, Shop.ASDA),
            ProductUrl.parse("Check out Yeo Valley yogurts at ASDA! $asda"),
        )

    @Test fun addsSchemeAndWww() =
        assertEquals(ProductUrl.Result.Ok(asda, Shop.ASDA), ProductUrl.parse(asda.removePrefix("https://www.")))

    @Test fun rejectsAsdaNonProductPages() =
        assertTrue(ProductUrl.parse("https://www.asda.com/groceries/search/yogurt") is ProductUrl.Result.Invalid)

    @Test fun rejectsTextWithoutALink() =
        assertTrue(ProductUrl.parse("Yeo Valley yogurts") is ProductUrl.Result.Invalid)

    @Test fun knownShopFromSubdomain() {
        val r = ok("https://groceries.morrisons.com/products/yeo-valley-123")
        assertEquals("MORRISONS", r.shop.key)
        assertEquals("Morrisons", r.shop.label)
        assertEquals("https://groceries.morrisons.com/products/yeo-valley-123", r.url)
    }

    @Test fun otherWebsitesAreNamedByDomain() {
        val r = ok("https://www.hollandandbarrett.com/shop/product/vitamin-c-60010001")
        assertEquals("hollandandbarrett.com", r.shop.key)
        assertEquals("hollandandbarrett.com", r.shop.label)
    }

    @Test fun otherWebsitesKeepProductParametersButDropTracking() =
        assertEquals(
            "https://shop.example.co.uk/item.php?id=42&size=500g",
            ok("https://shop.example.co.uk/item.php?utm_source=x&id=42&gclid=abc&size=500g#top").url,
        )

    @Test fun lookalikeHostsAreNotTheRealShop() {
        assertNotEquals(Shop.ASDA, ok("https://asda.com.evil.example/groceries/product/x/1").shop)
        assertNotEquals(Shop.ASDA, ok("https://evil-asda.com/groceries/product/x/1").shop)
    }

    @Test fun sameDomainAlwaysGetsSameColour() =
        assertEquals(Shop.forHost("www.example.com").color, Shop.forHost("example.com").color)
}
