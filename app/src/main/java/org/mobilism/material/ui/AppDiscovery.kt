package org.mobilism.material.ui

import org.mobilism.material.data.AppLibrary
import org.mobilism.material.data.LibraryEntry
import org.mobilism.material.data.Release
import org.mobilism.material.data.ReleaseCategory
import org.mobilism.material.data.ReleaseVersions
import org.mobilism.material.data.latestAppKey

internal fun Release.discoveryKey() = latestAppKey(appTitle.name)

private val postingOrder = compareBy<Release> { it.publishedAt }.thenBy { it.id }

/** Keep the first popularity position, but open the newest known post for that app. */
internal fun newestPerApp(ranked: List<Release>): List<Release> {
    val byApp = linkedMapOf<String, Release>()
    for (release in ranked) {
        val key = release.discoveryKey()
        val previous = byApp[key]
        if (previous == null || postingOrder.compare(release, previous) > 0) byApp[key] = release
    }
    return byApp.values.toList()
}

internal data class DailySelection(val discover: List<String>, val categoryIds: List<Long>, val featured: List<String>)
internal data class AppHomeShelves(val discover: List<Release>, val featured: List<Release>, val categoryNames: List<String>,
    val latest: List<Release>, val popular: List<Release>, val selection: DailySelection)

private fun <T> List<T>.rotated(offset: Int): List<T> {
    if (isEmpty()) return this
    val start = Math.floorMod(offset, size)
    return drop(start) + take(start)
}

internal fun discoverPicks(ranked: List<Release>, library: List<LibraryEntry>, latest: List<Release>,
    popular: List<Release>, categories: List<ReleaseCategory>, day: Int, installed: Map<String, String> = emptyMap(),
    saved: DailySelection? = null, seed: Int = 0): AppHomeShelves {
    val keys = mutableMapOf<String, String>()
    fun key(release: Release) = keys.getOrPut(release.title) { release.discoveryKey() }
    val catalog = mutableMapOf<String, Release>()
    for (release in ranked + popular + latest) {
        val app = key(release)
        val old = catalog[app]
        if (old == null || postingOrder.compare(release, old) > 0) {
            catalog[app] = release
        } else if (release.id == old.id) {
            // Feed cards carry artwork and counts. The shared ranking can supply their category.
            val category = release.categoryId ?: old.categoryId
            catalog[app] = release.copy(categoryId = category, storePackage = release.storePackage ?: old.storePackage,
                categoryName = release.categoryName ?: old.categoryName.takeIf { category == old.categoryId })
        }
    }

    val excluded = mutableSetOf<String>()
    val interests = mutableMapOf<String, Long>()
    val installedNames = installed.values.mapTo(mutableSetOf(), ::latestAppKey)
    val linkedPackages = AppLibrary.linkedFollowPackages(library)
    for ((name, release) in catalog) {
        val storePackage = release.storePackage ?: release.takeIf { it.contentHtml.isNotEmpty() }?.let(ReleaseVersions::packageName)
        if (name in installedNames || storePackage in installed || storePackage in linkedPackages) {
            excluded.add(name)
            release.categoryId?.let { interests[name] = it }
        }
    }
    val followedCategories = mutableSetOf<Long>()
    for (entry in library.filter { it.watched }) {
        val releases = listOfNotNull(entry.latest, entry.update, entry.release)
        excluded.addAll(releases.map(::key))
        val category = releases.firstNotNullOfOrNull { it.categoryId ?: catalog[key(it)]?.categoryId } ?: continue
        interests[key(entry.release)] = category
        followedCategories += category
    }
    val preferences = interests.values.groupingBy { it }.eachCount()
    val available = (ranked + popular).map(::key).distinct().filter { it !in excluded }.map(catalog::getValue)
    val labels = categories.associate { it.id to it.name }.toMutableMap()
    for (release in available) {
        val id = release.categoryId ?: continue
        val name = release.categoryName ?: continue
        labels.putIfAbsent(id, name)
    }
    val byCategory = available.filter { it.categoryId in labels }.groupBy { requireNotNull(it.categoryId) }
    val preferred = byCategory.keys.sortedWith(compareByDescending<Long> { it in followedCategories }
        .thenByDescending { preferences[it] ?: 0 }.thenBy { it })
    val rotation = day + (seed and 0xffff)
    val favorites = preferred.filter { it in preferences }.take(4)
    val topTen = popular.distinctBy(::key).take(10).mapTo(mutableSetOf(), ::key)
    val discoveryPool = available.filter { key(it) !in topTen }.ifEmpty {
        latest.distinctBy(::key).filter { key(it) !in excluded && key(it) !in topTen }.sortedByDescending { it.views }.map { catalog.getValue(key(it)) }
    }
    val familiarCategories = discoveryPool.filter { it.categoryId in favorites }
    val discoverOrder = (familiarCategories.take(20).rotated(rotation) + discoveryPool.take(25).rotated(rotation)).distinctBy(::key)
    val eligible = catalog.filterKeys { it !in excluded }
    val keptHero = saved?.discover.orEmpty().filterNot { it in topTen }.mapNotNull(eligible::get)
    val keptFeatures = saved?.featured.orEmpty().mapNotNull(eligible::get).filter { it.categoryId in saved?.categoryIds.orEmpty() }
    val heroOrder = keptHero.ifEmpty { discoverOrder }
    val heroFirst = heroOrder.firstOrNull { key(it) !in saved?.featured.orEmpty() }
    val reserved = (keptHero + listOfNotNull(heroFirst)).mapTo(mutableSetOf(), ::key)

    // Epoch day zero was a Thursday. Give each Monday-based week one eligible two-category day.
    val mondayIndex = day + 3
    val paired = Math.floorMod(mondayIndex, 7) == Math.floorMod(Math.floorDiv(mondayIndex, 7) + (seed and 0xffff), 7)
    val categoryOrder = (favorites.ifEmpty { preferred }.rotated(rotation) + preferred).distinct()
    val candidates = categoryOrder.filter { id -> byCategory[id].orEmpty().any { key(it) !in reserved } }
    val chosen = saved?.categoryIds?.takeIf { it.isNotEmpty() } ?: candidates.take(if (paired) 2 else 1)
    val featureHistory = saved?.featured.orEmpty().toSet()
    val protected = reserved.toSet()
    heroOrder.filter { key(it) !in featureHistory }.take(5).mapTo(reserved, ::key)
    for (id in chosen) {
        val group = byCategory[id].orEmpty()
        if (group.none { key(it) !in reserved }) {
            group.filter { key(it) !in protected }.takeLast(2).forEach { reserved.remove(key(it)) }
        }
    }
    val decks = chosen.map { id -> byCategory[id].orEmpty().filter { key(it) !in reserved } }
    val alternated = (0 until 5).flatMap { index -> decks.mapNotNull { it.getOrNull(index) } }
    val featured = keptFeatures.ifEmpty { alternated }.distinctBy(::key).filter { key(it) !in reserved }.take(5)
    val featureKeys = featured.mapTo(mutableSetOf(), ::key)
    val hero = heroOrder.filter { key(it) !in featureKeys }.take(5)
    val shown = (hero + featured).mapTo(mutableSetOf(), ::key)
    fun remaining(source: List<Release>) = source.filter { shown.add(key(it)) }.map { catalog.getValue(key(it)) }
    val top = remaining(popular)
    val activeCategories = chosen.filter { id -> featured.any { it.categoryId == id } && id in labels }
    return AppHomeShelves(hero, featured, activeCategories.map(labels::getValue).distinct(), remaining(latest), top,
        DailySelection(hero.map(::key), chosen, featured.map(::key)))
}
