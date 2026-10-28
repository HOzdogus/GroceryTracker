package uk.hozdogus.grocerytracker.ui

import uk.hozdogus.grocerytracker.data.Item
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs

fun formatPence(pence: Int?): String =
    if (pence == null) "–" else String.format(Locale.UK, "£%d.%02d", pence / 100, pence % 100)

/** "Nectar £1.75" and/or "was £2.15", or null when the item has no offer. */
fun offerText(item: Item): String? = listOfNotNull(
    item.nectarPrice?.let { "Nectar ${formatPence(it)}" },
    item.wasPrice?.let { "was ${formatPence(it)}" },
).joinToString(" · ").ifEmpty { null }

/** "↓ 89p" or "↑ £1.10". */
fun formatChange(diff: Int): String {
    val arrow = if (diff < 0) "↓" else "↑"
    val amount = abs(diff)
    return if (amount < 100) "$arrow ${amount}p" else "$arrow ${formatPence(amount)}"
}

/** Parses "1.20", "£1.20" or "120p" into pence. */
fun parsePence(text: String): Int? {
    val t = text.trim().removePrefix("£").trim()
    if (t.endsWith("p")) return t.removeSuffix("p").trim().toIntOrNull()?.takeIf { it > 0 }
    val pounds = t.toBigDecimalOrNull() ?: return null
    return pounds.movePointRight(2).toInt().takeIf { it > 0 }
}

/** "today 08:12", "yesterday 08:12" or "3 Oct 08:12". */
fun formatWhen(millis: Long, now: Long = System.currentTimeMillis()): String {
    val zone = ZoneId.systemDefault()
    val at = Instant.ofEpochMilli(millis).atZone(zone)
    val today = localDate(now, zone)
    val time = at.format(TIME)
    return when (at.toLocalDate()) {
        today -> "today $time"
        today.minusDays(1) -> "yesterday $time"
        else -> "${at.format(DAY)} $time"
    }
}

private val TIME = DateTimeFormatter.ofPattern("HH:mm", Locale.UK)
private val DAY = DateTimeFormatter.ofPattern("d MMM", Locale.UK)

fun localDate(millis: Long, zone: ZoneId = ZoneId.systemDefault()): LocalDate =
    Instant.ofEpochMilli(millis).atZone(zone).toLocalDate()
