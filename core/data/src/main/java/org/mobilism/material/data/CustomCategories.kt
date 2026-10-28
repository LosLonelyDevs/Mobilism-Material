package org.mobilism.material.data

import java.util.Locale
import java.util.UUID
import org.json.JSONArray
import org.json.JSONObject

data class CustomCategory(val id: String, val section: ContentSection, val name: String)

data class CategorizedItem(
    val id: String,
    val releases: List<Release>,
    val categoryIds: Set<String>,
) {
    val release: Release get() = releases.last()

    fun matches(candidate: Release): Boolean {
        if (candidate.section != release.section) return false
        if (releases.any { it.id == candidate.id }) return true
        val knownPackages = releases.mapNotNull(ReleaseVersions::packageName).toSet()
        val candidatePackage = ReleaseVersions.packageName(candidate)
        if (candidatePackage != null && knownPackages.isNotEmpty() && candidatePackage !in knownPackages) return false
        return releases.any { CustomCategories.sameItem(it, candidate) }
    }
}

data class CustomCategoryState(
    val categories: List<CustomCategory> = emptyList(),
    val items: List<CategorizedItem> = emptyList(),
) {
    fun categories(section: ContentSection) = Alphabetical.sorted(categories.filter { it.section == section }) { it.name }
    fun memberships(release: Release): Set<String> = items.filter { it.matches(release) }.flatMap { it.categoryIds }.toSet()
    fun matches(release: Release, selected: Set<String>): Boolean =
        items.any { it.categoryIds.any(selected::contains) && it.matches(release) }
    fun retain(section: ContentSection, selected: Set<String>): Set<String> =
        selected.intersect(categories.filter { it.section == section }.map { it.id }.toSet())
}

object CustomCategories {
    private fun normalized(value: String?) = value.orEmpty().lowercase(Locale.ROOT).replace(Regex("\\s+"), " ").trim()

    fun sameItem(first: Release, second: Release): Boolean {
        if (first.section == null || first.section != second.section) return false
        if (first.id == second.id) return true
        if (first.section != ContentSection.Books) return ReleaseVersions.sameApp(first, second)
        val a = ReleaseTitles.parse(first.title, first.section)
        val b = ReleaseTitles.parse(second.title, second.section)
        // A title alone cannot distinguish books by different authors.
        return !a.bookAuthor.isNullOrBlank() && !b.bookAuthor.isNullOrBlank() &&
            normalized(a.name) == normalized(b.name) && normalized(a.bookAuthor) == normalized(b.bookAuthor) &&
            normalized(a.series) == normalized(b.series)
    }

    fun validateName(state: CustomCategoryState, section: ContentSection, value: String, except: String? = null): String {
        val name = value.trim()
        require(name.isNotEmpty()) { "Enter a category name." }
        require(state.categories.none { it.id != except && it.section == section && normalized(it.name) == normalized(name) }) {
            "A category with this name already exists."
        }
        return name
    }

    fun assign(state: CustomCategoryState, release: Release, selected: Set<String>): CustomCategoryState {
        val section = requireNotNull(release.section) { "The release section is unavailable." }
        val ids = state.retain(section, selected)
        val previous = state.items.filter { it.matches(release) }
        val compact = AppLibrary.compact(release).let { candidate ->
            val known = previous.flatMap { it.releases }.firstOrNull { it.id == release.id && ReleaseVersions.packageName(it) != null }
            if (ReleaseVersions.packageName(candidate) == null && known != null) candidate.copy(contentHtml = known.contentHtml) else candidate
        }
        val item = CategorizedItem(previous.firstOrNull()?.id ?: UUID.randomUUID().toString(),
            (previous.flatMap { it.releases }.filterNot { it.id == release.id && it.title == release.title } + compact).distinct(), ids)
        return state.copy(items = state.items.filterNot { it in previous } + listOfNotNull(item.takeIf { ids.isNotEmpty() }))
    }

    fun observe(state: CustomCategoryState, release: Release): CustomCategoryState {
        val matching = state.items.filter { it.matches(release) }
        if (matching.isEmpty()) return state
        if (matching.size == 1 && matching.single().releases.any { identityDetails(it) == identityDetails(release) ||
            (ReleaseVersions.packageName(release) == null && identityDetails(it.copy(contentHtml = "")) == identityDetails(release)) }) return state
        return assign(state, release, matching.flatMap { it.categoryIds }.toSet())
    }

    private fun identityDetails(release: Release): List<String> {
        val title = ReleaseTitles.parse(release.title, release.section)
        return listOf(release.section?.name.orEmpty(), normalized(title.name), ReleaseVersions.packageName(release).orEmpty(),
            normalized(title.bookAuthor), normalized(title.series),
            if (release.section == ContentSection.Books && title.bookAuthor.isNullOrBlank()) release.id.toString()
            else Regex("(?i)\\b(?:android\\s*)?tv\\b").containsMatchIn((listOf(title.name) + title.tags).joinToString(" ")).toString())
    }

    fun delete(state: CustomCategoryState, id: String) = state.copy(
        categories = state.categories.filterNot { it.id == id },
        items = state.items.map { it.copy(categoryIds = it.categoryIds - id) }.filter { it.categoryIds.isNotEmpty() },
    )
}

internal object CustomCategoryJson {
    fun encode(state: CustomCategoryState): String = JSONObject().apply {
        put("version", 1)
        put("categories", JSONArray().apply { state.categories.forEach { category -> put(JSONObject().apply {
            put("id", category.id); put("section", category.section.name); put("name", category.name)
        }) } })
        put("items", JSONArray().apply { state.items.forEach { item -> put(JSONObject().apply {
            put("id", item.id); put("categories", JSONArray(item.categoryIds.toList()))
            put("releases", JSONArray().apply { item.releases.forEach { release -> put(JSONObject().apply {
                put("id", release.id); put("title", release.title); put("section", release.section?.name)
                put("author", release.author); put("thumbnail", release.thumbnail); put("date", release.publishedAt)
                put("views", release.views); put("replies", release.replies); put("url", release.browserUrl)
                put("category", release.categoryId); put("content", AppLibrary.compact(release).contentHtml)
            }) } })
        }) } })
    }.toString()

    fun decode(value: String): CustomCategoryState {
        val root = JSONObject(value)
        require(root.getInt("version") == 1)
        val categories = root.getJSONArray("categories").objects().mapNotNull { item -> runCatching {
            CustomCategory(item.getString("id").also { require(it.isNotBlank()) },
                ContentSection.valueOf(item.getString("section")), item.getString("name").also { require(it.isNotBlank()) })
        }.getOrNull() }.distinctBy { it.id }
        val items = root.getJSONArray("items").objects().mapNotNull { item -> runCatching {
            val releases = item.getJSONArray("releases").objects().map { release -> Release(
                id = release.getLong("id"), title = release.getString("title"), author = release.optString("author"),
                thumbnail = release.text("thumbnail"), publishedAt = release.optLong("date"),
                views = release.optLong("views"), replies = release.optLong("replies"),
                browserUrl = release.text("url"), section = ContentSection.valueOf(release.getString("section")),
                categoryId = if (release.isNull("category")) null else release.getLong("category"), contentHtml = release.optString("content"),
            ).also { require(it.id > 0) } }
            require(releases.isNotEmpty() && releases.all { it.section == releases.first().section })
            val array = item.getJSONArray("categories")
            val ids = (0 until array.length()).map { array.getString(it) }.toSet()
                .intersect(categories.filter { it.section == releases.first().section }.map { it.id }.toSet())
            CategorizedItem(item.getString("id"), releases, ids).takeIf { ids.isNotEmpty() }
        }.getOrNull() }.distinctBy { it.id }
        return CustomCategoryState(categories, items)
    }

    private fun JSONObject.text(key: String): String? = if (isNull(key)) null else optString(key).takeIf { it.isNotBlank() }
    private fun JSONArray.objects() = (0 until length()).mapNotNull { optJSONObject(it) }
}
