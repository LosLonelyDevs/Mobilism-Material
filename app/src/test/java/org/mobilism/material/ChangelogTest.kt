package org.mobilism.material

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class ChangelogTest {
    private val entries = File(requireNotNull(System.getProperty("mobilism.changelog.path")))
        .readText().split(Regex("(?m)^## ")).drop(1).map { section ->
            section.substringBefore('\n').trim() to section.substringAfter('\n', "").trim()
        }

    @Test fun entriesRespectWordBudget() {
        val budget = 40
        entries.filterNot { it.first in publishedVersions }.forEach { (version, notes) ->
            val words = notes.replace(Regex("(?m)^-\\s+"), "")
                .split(Regex("\\s+")).count { it.isNotBlank() }
            assertTrue("Changelog $version: $words words, budget is $budget. Trim.", words <= budget)
        }
    }

    @Test fun entriesAreNonEmpty() {
        val releases = entries.filter { it.first != "Unreleased" }
        assertTrue("CHANGELOG.md has no releases", releases.isNotEmpty())
        releases.forEach { (version, notes) ->
            assertTrue("Changelog $version is empty", notes.isNotBlank())
        }
    }

    private companion object {
        // Published notes predate the word budget.
        val publishedVersions = setOf(
            "0.1.16", "0.1.14", "0.1.13", "0.1.12", "0.1.11", "0.1.10", "0.1.9", "0.1.8",
            "0.1.7", "0.1.6", "0.1.5", "0.1.4", "0.1.3", "0.1.2", "0.1.1",
        )
    }
}
