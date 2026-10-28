package uk.hozdogus.grocerytracker.data

import uk.hozdogus.grocerytracker.fetch.FetchResult

/** An item before and after a check, plus the history point recorded (null when the check failed). */
data class CheckOutcome(val before: Item, val item: Item, val point: PricePoint?) {
    val ok: Boolean get() = point != null

    /** This check found a lower price than the last one. */
    val dropped: Boolean
        get() = point != null && before.currentPrice != null && point.price < before.currentPrice

    /** This check brought the price to or below the target, from above it. */
    val hitTarget: Boolean
        get() {
            val target = item.targetPrice ?: return false
            if (point == null || point.price > target) return false
            return before.currentPrice == null || before.currentPrice > target
        }
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
            item,
            item.copy(
                name = result.name.ifBlank { item.name },
                imageUrl = result.imageUrl ?: item.imageUrl,
                currentPrice = price,
                previousPrice = if (changed) item.currentPrice else item.previousPrice,
                lastChangeAt = if (changed) now else item.lastChangeAt,
                lowestPrice = minOf(item.lowestPrice ?: price, price),
                highestPrice = maxOf(item.highestPrice ?: price, price),
                // Offers come and go, so these follow the latest page rather than keeping old values.
                wasPrice = result.wasPrice,
                nectarPrice = result.nectarPrice,
                lastCheckedAt = now,
                lastCheckOk = true,
                lastCheckError = null,
            ),
            PricePoint(
                itemId = item.id, price = price,
                wasPrice = result.wasPrice, nectarPrice = result.nectarPrice, checkedAt = now,
            ),
        )
    }
    is FetchResult.Blocked -> CheckOutcome(
        item,
        item.copy(
            lastCheckedAt = now,
            lastCheckOk = false,
            lastCheckError = "Blocked by a robot check.\n${result.details}",
        ),
        null,
    )
    is FetchResult.Failed -> CheckOutcome(
        item,
        item.copy(lastCheckedAt = now, lastCheckOk = false, lastCheckError = "${result.reason}\n${result.details}"),
        null,
    )
}
