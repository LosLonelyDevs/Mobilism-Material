package org.mobilism.material.data

import java.util.Locale
import org.junit.Assert.*
import org.junit.Test

class AlphabeticalTest {
    @Test fun sortsCaseAndAccentsConsistentlyWithoutMutatingSource() {
        val input = listOf("zebra", "Éclair", " beta", "Apple")
        val previous = Locale.getDefault()
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"))
            assertEquals(listOf("Apple", " beta", "Éclair", "zebra"), Alphabetical.sorted(input) { it })
            assertEquals("zebra", input.first())
        } finally { Locale.setDefault(previous) }
    }

    @Test fun preservesIdentityAndOrderForEquivalentNames() {
        val categories = listOf(ReleaseCategory(3, "Tools"), ReleaseCategory(1, "Books"), ReleaseCategory(2, "books"))
        assertEquals(listOf(1L, 2L, 3L), Alphabetical.sorted(categories) { it.name }.map { it.id })
    }
}
