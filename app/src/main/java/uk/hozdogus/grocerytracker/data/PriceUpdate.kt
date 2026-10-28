package uk.hozdogus.grocerytracker.data

import uk.hozdogus.grocerytracker.fetch.FetchResult

/** The item after a check, plus the history point to record (null when the check failed). */
data class CheckOutcome(val item: Item, val point: PricePoint?) {
    /** True when this check found a lower price than before. */
    val dropped: Boolean
        get() = point != null && item.previousPrice != null && item.currentPrice != null &&
            item.lastChangeAt == point.checkedAt && item.currentPrice < item.previousPrice
}

/**
 * Applies a page check to [item]. A failed check only marks the item as "couldn't check";
 * it never changes the stored prices.
 */
fun applyCheck(item: Item, result: FetchResult, now: Long): CheckOutcome = when (result) {
    is FetchResult.Success -> {
        val price = result.price
        val changed = item.currentPrice != null && item.currentPrice != price
        CheckOutcome(
            item.copy(
                name = result.name.ifBlank { item.name },
                imageUrl = result.imageUrl ?: item.imageUrl,
                currentPrice = price,
                previousPrice = if (changed) item.currentPrice else item.previousPrice,
                lastChangeAt = if (changed) now else item.lastChangeAt,
                lowestPrice = minOf(item.lowestPrice ?: price, price),
                highestPrice = maxOf(item.highestPrice ?: price, price),
                lastCheckedAt = now,
                lastCheckOk = true,
                lastCheckError = null,
            ),
            PricePoint(itemId = item.id, price = price, checkedAt = now),
        )
    }
    is FetchResult.Blocked -> CheckOutcome(
        item.copy(
            lastCheckedAt = now,
            lastCheckOk = false,
            lastCheckError = "Blocked by a robot check.\n${result.details}",
        ),
        null,
    )
    is FetchResult.Failed -> CheckOutcome(
        item.copy(lastCheckedAt = now, lastCheckOk = false, lastCheckError = "${result.reason}\n${result.details}"),
        null,
    )
}
