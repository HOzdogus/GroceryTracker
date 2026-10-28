package uk.hozdogus.grocerytracker.ui

import uk.hozdogus.grocerytracker.data.Item
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

/** Today's drops first (biggest drop first), then everything else by name. */
fun orderForList(items: List<Item>, now: Long, zone: ZoneId = ZoneId.systemDefault()): List<Item> {
    val (drops, rest) = items.partition { droppedToday(it, now, zone) }
    return drops.sortedBy { recentChange(it, now, zone) } + rest.sortedBy { it.name.lowercase() }
}
