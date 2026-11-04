package org.mobilism.material.ui

import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

internal fun formatWrzBalance(value: String): String {
    val number = value.trim().replace(",", "").toBigDecimalOrNull()?.stripTrailingZeros() ?: return value
    return DecimalFormat("#,##0", DecimalFormatSymbols(Locale.US)).apply {
        if (number.scale() > 0) {
            minimumFractionDigits = 2
            maximumFractionDigits = number.scale().coerceAtLeast(2)
        }
    }.format(number)
}
