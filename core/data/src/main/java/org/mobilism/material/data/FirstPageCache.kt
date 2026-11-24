package org.mobilism.material.data

import android.content.SharedPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** Keep private pages in memory and serialize reads of the same page, including refreshes. */
internal class RecentPages<K : Any, V : Any>(private val cache: AppCache, private val logger: DiagnosticLogger,
    private val capacity: Int, private val now: () -> Long = { System.nanoTime() / 1_000_000 }) {
    private data class Entry<V>(val value: V, val savedAt: Long)
    private val entries = linkedMapOf<K, Entry<V>>()
    private val locks = Array(8) { Mutex() }
    private var generation = 0L

    @Synchronized fun invalidate(matches: (K) -> Boolean) {
        generation++
        entries.keys.removeAll(matches)
    }

    suspend fun find(matches: (K, V) -> Boolean): Pair<K, V>? {
        val hit = synchronized(this) {
            entries.entries.lastOrNull { now() - it.value.savedAt in 0 until 60_000L && matches(it.key, it.value.value) }
                ?.let { it.key to it.value.value }
        }
        if (hit != null) logger.cacheLookup(cache, CacheOutcome.MemoryHit)
        return hit
    }

    @Synchronized fun revision(): Long = generation

    @Synchronized fun store(key: K, value: V, revision: Long) {
        if (generation != revision) return
        entries.remove(key)
        entries[key] = Entry(value, now())
        while (entries.size > capacity) entries.remove(entries.keys.first())
    }

    fun clear() = invalidate { true }

    suspend fun get(key: K, fresh: Boolean, load: suspend () -> V): V =
        locks[Math.floorMod(key.hashCode(), locks.size)].withLock {
            val (revision, entry) = synchronized(this) {
                generation to entries[key]?.takeIf { !fresh && now() - it.savedAt in 0 until 60_000L }
            }
            if (!fresh) logger.cacheLookup(cache, if (entry == null) CacheOutcome.Miss else CacheOutcome.MemoryHit)
            entry?.value ?: load().also { value -> store(key, value, revision) }
        }
}

// A filtered first page can scan several source pages. Replay those pages to rebuild its cursors.
internal class FirstFeedPage(val fresh: Boolean) : kotlin.coroutines.AbstractCoroutineContextElement(Key) {
    companion object Key : kotlin.coroutines.CoroutineContext.Key<FirstFeedPage>
    private var oldestSavedAt = Long.MAX_VALUE

    @Synchronized fun include(savedAt: Long) { oldestSavedAt = minOf(oldestSavedAt, savedAt) }
    @Synchronized fun cachedAgeMs() = (System.currentTimeMillis() - oldestSavedAt).coerceAtLeast(0)
}

/** Each value is a validated server page. Filters that run locally are reapplied on every read. */
internal class FeedPageStore(private val storage: SharedPreferences, private val now: () -> Long = System::currentTimeMillis) {
    fun generation(order: FeedOrder, requested: Int): Int = synchronized(storage) {
        val key = "generation-${order.name}"
        val previous = storage.getInt(key, 0)
        if (requested > previous) storage.edit().putInt(key, requested).apply()
        maxOf(requested, previous)
    }

    data class SavedPage(val json: String, val savedAt: Long)

    suspend fun read(key: String, ttl: Long): SavedPage? = withContext(Dispatchers.IO) {
        runCatching {
            val value = storage.getString(key, null) ?: return@runCatching null
            val timestamp = value.substringBefore('\n').toLong()
            if (now() - timestamp in 0 until ttl) SavedPage(value.substringAfter('\n'), timestamp) else null
        }.getOrNull()
    }

    suspend fun save(key: String, page: String) = withContext(Dispatchers.IO) {
        runCatching {
            if (page.length > 384_000) {
                storage.edit().remove(key).apply()
                return@runCatching
            }
            synchronized(storage) {
                val pages = storage.all.entries.filter { it.key != key && it.value is String }
                    .sortedByDescending { (it.value as String).substringBefore('\n').toLongOrNull() ?: 0L }
                val editor = storage.edit()
                var length = page.length
                pages.forEachIndexed { index, item ->
                    length += (item.value as String).length
                    if (index >= 23 || length > 2_000_000) editor.remove(item.key)
                }
                editor.putString(key, "${now()}\n$page").apply()
            }
        }
        Unit
    }
}
