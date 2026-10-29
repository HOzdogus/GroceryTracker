package uk.hozdogus.grocerytracker.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import uk.hozdogus.grocerytracker.data.Item
import uk.hozdogus.grocerytracker.data.ProductGroup
import uk.hozdogus.grocerytracker.data.Shop
import java.time.ZoneId
import java.time.ZonedDateTime

class ListOrderTest {

    private val zone = ZoneId.of("Europe/London")
    private val now = ZonedDateTime.of(2026, 10, 28, 9, 0, 0, 0, zone).toInstant().toEpochMilli()
    private val day = 24 * 60 * 60 * 1000L
    private var nextId = 1L

    private fun item(
        name: String, current: Int, previous: Int? = null, changedAt: Long? = null,
        shop: Shop = Shop.ASDA, groupId: Long? = null, ok: Boolean = true,
    ) = Item(
        id = nextId++, url = name + nextId, shop = shop, name = name, addedAt = 0,
        currentPrice = current, previousPrice = previous, lastChangeAt = changedAt,
        groupId = groupId, lastCheckOk = ok,
    )

    private fun keys(entries: List<ListEntry>) = entries.map { it.name }

    @Test fun dropsTodayComeFirstBiggestFirst() {
        val a = item("Apples", 100)
        val b = item("Bread", 90, 100, now - 1000)
        val c = item("Cheese", 200, 300, now - 2000)
        val d = item("Dates", 150, 100, now - 1000)
        assertEquals(
            listOf("Cheese", "Bread", "Apples", "Dates"),
            keys(buildEntries(listOf(a, b, c, d), emptyList(), now, zone)),
        )
    }

    @Test fun yesterdaysChangeStillShowsButIsNotOnTop() {
        val b = item("Bread", 90, 100, now - day)
        assertEquals(-10, recentChange(b, now, zone))
        assertEquals(listOf("Apples", "Bread"), keys(buildEntries(listOf(b, item("Apples", 1)), emptyList(), now, zone)))
    }

    @Test fun olderChangeIsHidden() =
        assertNull(recentChange(item("Bread", 90, 100, now - 3 * day), now, zone))

    @Test fun groupCollapsesToOneEntryLedByCheapest() {
        val g = ProductGroup(id = 5, name = "Yogurts", createdAt = 0)
        val asda = item("Yeo Valley 4x85g", 126, shop = Shop.ASDA, groupId = 5)
        val sains = item("Little Yeos 4x85g", 215, shop = Shop.SAINSBURYS, groupId = 5)
        val other = item("Bread", 100)
        val entries = buildEntries(listOf(asda, sains, other), listOf(g), now, zone)
        assertEquals(listOf("Bread", "Yogurts"), keys(entries))
        val group = entries[1] as ListEntry.Group
        assertEquals(asda, group.lead)
        assertEquals(2, group.items.size)
    }

    @Test fun groupDropTodayGoesToTop() {
        val g = ProductGroup(id = 5, name = "Yogurts", createdAt = 0)
        val asda = item("Yeo Valley", 126, 215, now - 1000, groupId = 5)
        val sains = item("Little Yeos", 215, shop = Shop.SAINSBURYS, groupId = 5)
        val entries = buildEntries(listOf(item("Apples", 1), asda, sains), listOf(g), now, zone)
        assertEquals(listOf("Yogurts", "Apples"), keys(entries))
    }

    @Test fun tiePrefersShopWhoseCheckWorked() {
        val g = ProductGroup(id = 5, name = "Yogurts", createdAt = 0)
        val failed = item("A", 126, groupId = 5, ok = false)
        val fine = item("B", 126, shop = Shop.SAINSBURYS, groupId = 5)
        val group = buildEntries(listOf(failed, fine), listOf(g), now, zone).single() as ListEntry.Group
        assertEquals(fine, group.lead)
        assertEquals(2, group.tied.size)
    }

    @Test fun itemInMissingGroupStillShows() =
        assertEquals(listOf("Orphan"), keys(buildEntries(listOf(item("Orphan", 1, groupId = 99)), emptyList(), now, zone)))

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
