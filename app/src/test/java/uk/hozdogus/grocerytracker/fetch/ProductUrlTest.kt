package uk.hozdogus.grocerytracker.fetch

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import uk.hozdogus.grocerytracker.data.Shop

class ProductUrlTest {

    private val asda = "https://www.asda.com/groceries/product/kids-yogurts-fromage-frais/" +
        "yeo-valley-organic-fruity-favourites-whole-milk-yogurt-4-x-85g/7518557"
    private val sainsburys = "https://www.sainsburys.co.uk/groceries/product/" +
        "yeo-valley-organic-little-yeos-fruity-favourites-kids-yogurt-selection-4x85g"

    @Test fun plainAsdaLink() =
        assertEquals(ProductUrl.Result.Ok(asda, Shop.ASDA), ProductUrl.parse(asda))

    @Test fun stripsTrackingParameters() =
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
        assertEquals(
            ProductUrl.Result.Ok(asda, Shop.ASDA),
            ProductUrl.parse(asda.removePrefix("https://www.")),
        )

    @Test fun rejectsOtherShops() =
        assertTrue(ProductUrl.parse("https://www.tesco.com/groceries/en-GB/products/1") is ProductUrl.Result.Invalid)

    @Test fun rejectsNonProductPages() =
        assertTrue(ProductUrl.parse("https://www.asda.com/groceries/search/yogurt") is ProductUrl.Result.Invalid)

    @Test fun rejectsLookalikeHosts() =
        assertTrue(ProductUrl.parse("https://asda.com.evil.example/groceries/product/x/1") is ProductUrl.Result.Invalid)

    @Test fun rejectsHostsEndingInShopName() =
        assertTrue(ProductUrl.parse("https://evil-asda.com/groceries/product/x/1") is ProductUrl.Result.Invalid)
}
