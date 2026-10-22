package org.mobilism.material.data

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class CategoryJsonTest {
    @Test fun flattensLeafCategoriesAndPreservesTheirGroup() {
        val categories = CategoryJson.parse(JSONObject("""
            {"books":[{"id":"100","name":"Non-Fiction","child":[
              {"id":"101","name":"Health &amp; Fitness"},{"id":"102","name":"History"}]},
              {"id":"103","name":"Audiobooks"}]}
        """.trimIndent()), ContentSection.Books)
        assertEquals(listOf(101L, 102L, 103L), categories.map { it.id })
        assertEquals("Health & Fitness", categories.first().name)
        assertEquals("Non-Fiction", categories.first().group)
        assertNull(categories.last().group)
    }
}
