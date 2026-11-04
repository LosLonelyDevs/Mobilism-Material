package org.mobilism.material.settings

import org.junit.Assert.*
import org.junit.Test

class ReleaseDisplayStoreTest {
    @Test fun lastDescriptionChoiceSurvivesStoreRecreationAndCanBeReversed() {
        var saved = true
        val first = ReleaseDisplayStore(saved) { saved = it }
        assertTrue(first.descriptionExpanded.value)
        first.setDescriptionExpanded(false)
        assertFalse(first.descriptionExpanded.value)
        val reopened = ReleaseDisplayStore(saved) { saved = it }
        assertFalse(reopened.descriptionExpanded.value)
        reopened.setDescriptionExpanded(true)
        assertTrue(ReleaseDisplayStore(saved) {}.descriptionExpanded.value)
    }

    @Test fun repeatedChoiceDoesNotWriteAgain() {
        var writes = 0
        val store = ReleaseDisplayStore(true) { writes++ }
        store.setDescriptionExpanded(true)
        store.setDescriptionExpanded(false)
        store.setDescriptionExpanded(false)
        assertEquals(1, writes)
    }
}
