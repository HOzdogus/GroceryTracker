package uk.hozdogus.grocerytracker.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import uk.hozdogus.grocerytracker.data.Item
import uk.hozdogus.grocerytracker.data.Shop
import java.time.ZoneId
import java.time.ZonedDateTime

class ListOrderTest {

    private val zone = ZoneId.of("Europe/London")
    private val now = ZonedDateTime.of(2026, 10, 28, 9, 0, 0, 0, zone).toInstant().toEpochMilli()
    private val day = 24 * 60 * 60 * 1000L

    private fun item(name: String, current: Int, previous: Int? = null, changedAt: Long? = null) = Item(
        url = name, shop = Shop.ASDA, name = name,
        currentPrice = current, previousPrice = previous, lastChangeAt = changedAt,
    )

    @Test fun dropsTodayComeFirstBiggestFirst() {
        val a = item("Apples", 100)
        val b = item("Bread", 90, 100, now - 1000)
        val c = item("Cheese", 200, 300, now - 2000)
        val d = item("Dates", 150, 100, now - 1000)
        assertEquals(listOf(c, b, a, d), orderForList(listOf(a, b, c, d), now, zone))
    }

    @Test fun yesterdaysChangeStillShowsButIsNotOnTop() {
        val a = item("Apples", 1)
        val b = item("Bread", 90, 100, now - day)
        assertEquals(-10, recentChange(b, now, zone))
        assertEquals(listOf(a, b), orderForList(listOf(b, a), now, zone))
    }

    @Test fun olderChangeIsHidden() =
        assertNull(recentChange(item("Bread", 90, 100, now - 3 * day), now, zone))

    @Test fun formatsChanges() {
        assertEquals("↓ 89p", formatChange(-89))
        assertEquals("↑ £1.10", formatChange(110))
    }

    @Test fun parsesTargetPrices() {
        assertEquals(120, parsePence("1.20"))
        assertEquals(120, parsePence("£1.2"))
        assertEquals(95, parsePence("95p"))
        assertNull(parsePence("abc"))
        assertNull(parsePence("0"))
    }
}
