package org.mobilism.material.data

import java.text.Collator
import java.util.Locale

/** One ordering for named lists, independent of API order and device casing rules. */
object Alphabetical {
    fun <T> sorted(items: Iterable<T>, label: (T) -> String): List<T> {
        val collator = Collator.getInstance(Locale.ROOT).apply { strength = Collator.PRIMARY }
        return items.sortedWith { left, right -> collator.compare(label(left).trim(), label(right).trim()) }
    }
}
