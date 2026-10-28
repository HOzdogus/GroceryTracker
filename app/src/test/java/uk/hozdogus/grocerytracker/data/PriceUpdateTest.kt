package uk.hozdogus.grocerytracker.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import uk.hozdogus.grocerytracker.fetch.FetchResult

class PriceUpdateTest {

    private val item = Item(
        id = 7, url = "u", shop = Shop.ASDA, name = "Yogurt",
        currentPrice = 215, lowestPrice = 215, highestPrice = 215, lastCheckedAt = 1,
    )

    @Test fun dropUpdatesPricesAndHistory() {
        val out = applyCheck(item, FetchResult.Success("Yogurt", null, 126), now = 100)
        assertEquals(126, out.item.currentPrice)
        assertEquals(215, out.item.previousPrice)
        assertEquals(126, out.item.lowestPrice)
        assertEquals(215, out.item.highestPrice)
        assertEquals(100L, out.item.lastChangeAt)
        assertEquals(PricePoint(itemId = 7, price = 126, checkedAt = 100), out.point)
        assertTrue(out.dropped)
    }

    @Test fun samePriceKeepsLastChange() {
        val changed = applyCheck(item, FetchResult.Success("Yogurt", null, 126), now = 100).item
        val out = applyCheck(changed, FetchResult.Success("Yogurt", null, 126), now = 200)
        assertEquals(215, out.item.previousPrice)
        assertEquals(100L, out.item.lastChangeAt)
        assertFalse(out.dropped)
    }

    @Test fun riseIsNotADrop() {
        val out = applyCheck(item, FetchResult.Success("Yogurt", null, 250), now = 100)
        assertEquals(250, out.item.highestPrice)
        assertFalse(out.dropped)
    }

    @Test fun failureKeepsPrices() {
        val out = applyCheck(item, FetchResult.Failed("No price", "details"), now = 100)
        assertNull(out.point)
        assertEquals(215, out.item.currentPrice)
        assertFalse(out.item.lastCheckOk)
        assertEquals(100L, out.item.lastCheckedAt)
        assertFalse(out.dropped)
    }

    @Test fun blockKeepsPrices() {
        val out = applyCheck(item, FetchResult.Blocked("robot"), now = 100)
        assertEquals(215, out.item.currentPrice)
        assertFalse(out.item.lastCheckOk)
    }
}
