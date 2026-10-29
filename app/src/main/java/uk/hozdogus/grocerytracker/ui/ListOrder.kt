package uk.hozdogus.grocerytracker.ui

import uk.hozdogus.grocerytracker.data.Item
import uk.hozdogus.grocerytracker.data.ProductGroup
import java.time.ZoneId

/** Price change to show on the list: only when it happened today or yesterday. Negative means cheaper. */
fun recentChange(item: Item, now: Long, zone: ZoneId = ZoneId.systemDefault()): Int? {
    val changedAt = item.lastChangeAt ?: return null
    val current = item.currentPrice ?: return null
    val previous = item.previousPrice ?: return null
    if (current == previous) return null
    return if (localDate(changedAt, zone) >= localDate(now, zone).minusDays(1)) current - previous else null
}

fun droppedToday(item: Item, now: Long, zone: ZoneId = ZoneId.systemDefault()): Boolean {
    val changedAt = item.lastChangeAt ?: return false
    val change = recentChange(item, now, zone) ?: return false
    return change < 0 && localDate(changedAt, zone) == localDate(now, zone)
}

/** The items with the lowest current price (several when shops tie). Items without a price never count. */
fun cheapest(items: List<Item>): List<Item> {
    val low = items.mapNotNull { it.currentPrice }.minOrNull() ?: return emptyList()
    return items.filter { it.currentPrice == low }
}

/** One row on the main list: a single item, or a group shown by its cheapest shop. */
sealed class ListEntry {
    abstract val key: String
    abstract val name: String

    data class Single(val item: Item) : ListEntry() {
        override val key get() = "item-${item.id}"
        override val name get() = item.name
    }

    data class Group(val group: ProductGroup, val items: List<Item>) : ListEntry() {
        override val key get() = "group-${group.id}"
        override val name get() = group.name

        /** The item the row shows: the cheapest, preferring one whose last check worked. */
        val lead: Item get() = cheapest(items).maxByOrNull { it.lastCheckOk } ?: items.first()
        val tied: List<Item> get() = cheapest(items)
    }
}

/** The item whose price change an entry shows. */
private fun ListEntry.shownItem(): Item = when (this) {
    is ListEntry.Single -> item
    is ListEntry.Group -> lead
}

/**
 * Builds the main list: grouped items collapse into one entry each, then today's drops come first
 * (biggest drop first), then everything else by name.
 */
fun buildEntries(
    items: List<Item>,
    groups: List<ProductGroup>,
    now: Long,
    zone: ZoneId = ZoneId.systemDefault(),
): List<ListEntry> {
    val byGroup = items.filter { it.groupId != null }.groupBy { it.groupId }
    val entries = items.filter { it.groupId == null || groups.none { g -> g.id == it.groupId } }
        .map { ListEntry.Single(it) } +
        groups.mapNotNull { g -> byGroup[g.id]?.let { ListEntry.Group(g, it) } }

    val (drops, rest) = entries.partition { droppedToday(it.shownItem(), now, zone) }
    return drops.sortedBy { recentChange(it.shownItem(), now, zone) } + rest.sortedBy { it.name.lowercase() }
}
