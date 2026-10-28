package uk.hozdogus.grocerytracker.ui

import java.util.Locale

fun formatPence(pence: Int?): String =
    if (pence == null) "–" else String.format(Locale.UK, "£%d.%02d", pence / 100, pence % 100)
