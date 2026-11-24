package org.mobilism.material.images

import android.content.SharedPreferences
import coil3.Extras
import coil3.annotation.ExperimentalCoilApi
import coil3.disk.DiskCache
import coil3.network.CacheStrategy
import coil3.network.NetworkClient
import coil3.network.NetworkRequest
import coil3.network.NetworkResponse
import coil3.network.okhttp.asNetworkClient
import coil3.request.Options
import okhttp3.Call
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okio.ByteString.Companion.encodeUtf8
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import org.mobilism.material.data.retryAfterMillis
import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean

// This image host sometimes returns a successful HTTP response containing only this error.
internal val missingImageBody = "can't open image".encodeUtf8()
internal const val firstImageRetryMs = 60_000L
private const val retrySlots = 3
private const val historyRetentionMs = 24 * 60 * 60_000L
private val delaysAfterRetry = listOf(5 * 60_000L, 30 * 60_000L, 60 * 60_000L)

internal data class ImageRetryReservation(val slot: Int, val nextStep: Int, val nextAllowed: Long)

internal fun imageRetryReservation(now: Long, blockedUntil: Long, step: Int, recent: List<Long>): ImageRetryReservation? {
    if (blockedUntil > now) return null
    val slot = recent.indices.minBy { recent[it] }
    if (recent[slot] > 0 && recent[slot] > now - firstImageRetryMs) return null
    val index = step.coerceIn(delaysAfterRetry.indices)
    return ImageRetryReservation(slot, (index + 1).coerceAtMost(delaysAfterRetry.lastIndex), now + delaysAfterRetry[index])
}

internal interface ImageRetryWindow {
    fun reserve(key: String, now: Long, host: String = "images.mobilism.org"): Boolean
    fun postponeHost(until: Long, host: String = "images.mobilism.org")
    fun failed(key: String, time: Long)
    fun knownFailure(key: String): Boolean
    fun recovered(key: String)
}

internal class SavedImageRetryWindow(private val preferences: SharedPreferences, private val retryGeneration: () -> Int = { 0 }) : ImageRetryWindow {
    override fun knownFailure(key: String) = preferences.contains(key)

    override fun failed(key: String, time: Long) {
        synchronized(preferences) {
            if (!preferences.contains(key)) {
                val edit = preferences.edit()
                prune(edit, time)
                edit.putLong(key, time + firstImageRetryMs).putLong("last/$key", time).commit()
            }
        }
    }

    override fun reserve(key: String, now: Long, host: String): Boolean = synchronized(preferences) {
        val until = preferences.getLong(key, 0)
        val previousGeneration = preferences.getInt("generation/$key", 0)
        val generation = maxOf(previousGeneration, retryGeneration())
        val newCommand = generation > previousGeneration
        val localDeadline = if (newCommand) preferences.getLong("last/$key", 0) + firstImageRetryMs else until
        val stepKey = "step/$key"
        val step = if (newCommand || until <= now - historyRetentionMs) 0 else preferences.getInt(stepKey, 0)
        val recent = List(retrySlots) { preferences.getLong("slot/$it", 0L) }
        val reservation = imageRetryReservation(now, maxOf(localDeadline, preferences.getLong("host/$host", 0)), step, recent) ?: return false
        val edit = preferences.edit()
        prune(edit, now)
        edit.putLong(key, reservation.nextAllowed).putInt(stepKey, reservation.nextStep)
            .putLong("last/$key", now).putInt("generation/$key", generation)
            .putLong("slot/${reservation.slot}", now).commit()
    }

    private fun prune(edit: SharedPreferences.Editor, now: Long) {
        if (preferences.getLong("prunedAt", 0) <= now - historyRetentionMs) {
            for ((stored, deadline) in preferences.all) {
                if (stored.length == 64 && deadline is Long && deadline <= now - historyRetentionMs) {
                    edit.remove(stored).remove("step/$stored").remove("generation/$stored").remove("last/$stored")
                }
                if (stored.startsWith("host/") && deadline is Long && deadline <= now) edit.remove(stored)
            }
            edit.putLong("prunedAt", now)
        }
    }

    override fun postponeHost(until: Long, host: String) {
        synchronized(preferences) {
            val key = "host/$host"
            if (until > preferences.getLong(key, 0)) preferences.edit().putLong(key, until).commit()
        }
    }

    override fun recovered(key: String) {
        synchronized(preferences) {
            if (preferences.contains(key)) preferences.edit().remove(key).remove("step/$key")
                .remove("generation/$key").remove("last/$key").apply()
        }
    }
}

@OptIn(ExperimentalCoilApi::class)
internal class MissingImageRecovery(
    private val diskCache: () -> DiskCache?,
    private val attempts: ImageRetryWindow,
    private val retriesEnabled: () -> Boolean = { true },
    private val now: () -> Long = System::currentTimeMillis,
) : CacheStrategy by CacheStrategy.DEFAULT {
    private val recoveryRequest = Extras.Key(default = false)

    private fun imageId(url: String) = url.encodeUtf8().sha256().hex()

    fun imageLoaded(url: String) = attempts.recovered(imageId(url))

    fun decodeFailed(url: String, diskKey: String?) {
        attempts.failed(imageId(url), now())
        if (diskKey != null) diskCache()?.remove(diskKey)
    }

    override suspend fun read(cacheResponse: NetworkResponse, networkRequest: NetworkRequest, options: Options): CacheStrategy.ReadResult {
        val cached = CacheStrategy.ReadResult(cacheResponse)
        val time = now()
        val age = time - cacheResponse.responseMillis
        if (cacheResponse.code != 200 || networkRequest.url.toHttpUrlOrNull()?.host != "images.mobilism.org") return cached
        val length = cacheResponse.headers["Content-Length"]?.toLongOrNull()
        if (length != null && length != missingImageBody.size.toLong()) return cached
        val key = options.diskCacheKey ?: networkRequest.url
        val cache = diskCache() ?: return cached
        val unavailable = try {
            cache.openSnapshot(key)?.use { entry ->
                cache.fileSystem.metadata(entry.data).size == missingImageBody.size.toLong() &&
                    cache.fileSystem.read(entry.data) { readByteString() == missingImageBody }
            } == true
        } catch (_: IOException) { false }
        if (!unavailable) return cached
        val id = imageId(networkRequest.url)
        attempts.failed(id, cacheResponse.responseMillis)
        cacheResponse.headers["Retry-After"]?.let { header ->
            val delay = retryAfterMillis(header, cacheResponse.responseMillis)
            if (delay > age) {
                attempts.postponeHost(cacheResponse.responseMillis + delay.coerceAtMost(Long.MAX_VALUE - cacheResponse.responseMillis))
                return cached
            }
        }
        if (age < firstImageRetryMs || !retriesEnabled() || !options.networkCachePolicy.readEnabled ||
            networkRequest.headers["Cache-Control"].orEmpty().contains("only-if-cached") || !attempts.reserve(id, time)) return cached
        val extras = networkRequest.extras.newBuilder().apply { set(recoveryRequest, true) }.build()
        return CacheStrategy.ReadResult(networkRequest.copy(extras = extras))
    }

    fun networkClient(base: OkHttpClient): NetworkClient {
        val normal = base.asNetworkClient()
        val retryClient = base.newBuilder().retryOnConnectionFailure(false).followRedirects(false)
            .addNetworkInterceptor { chain ->
                // OkHttp can still follow up a 503 with Retry-After: 0 on the same call.
                val started = requireNotNull(chain.call().request().tag(AtomicBoolean::class.java))
                if (!started.compareAndSet(false, true)) throw IOException("Image recovery already attempted")
                chain.proceed(chain.request())
            }.build()
        val singleAttempt = Call.Factory { request ->
            retryClient.newCall(request.newBuilder().tag(AtomicBoolean::class.java, AtomicBoolean()).build())
        }.asNetworkClient()
        return object : NetworkClient {
            override suspend fun <T> executeRequest(request: NetworkRequest, block: suspend (NetworkResponse) -> T): T {
                val host = request.url.toHttpUrlOrNull()?.host ?: return normal.executeRequest(request, block)
                if (host != "images.mobilism.org" && request.extras[releaseArtworkKey] != true) return normal.executeRequest(request, block)
                val id = imageId(request.url)
                val offline = request.headers["Cache-Control"].orEmpty().contains("only-if-cached")
                val reserved = request.extras[recoveryRequest] == true
                val retry = reserved || attempts.knownFailure(id)
                if (offline) {
                    if (retry) throw IOException("Image unavailable offline")
                    return normal.executeRequest(request, block)
                }
                if (retry && (!retriesEnabled() ||
                    (!reserved && !attempts.reserve(id, now(), host)))) throw IOException("Image retry is deferred")
                val client = if (retry) singleAttempt else normal
                var handedToCache = false
                return try {
                    client.executeRequest(request) { response ->
                        checkImageResponse(response, host)
                        handedToCache = true
                        block(response)
                    }
                } catch (error: IOException) {
                    currentCoroutineContext().ensureActive()
                    if (!handedToCache) attempts.failed(id, now())
                    throw error
                }
            }
        }
    }

    private fun checkImageResponse(response: NetworkResponse, host: String) {
        val prefix = (response.delegate as okhttp3.Response).peekBody(128).bytes()
        val start = prefix.toString(Charsets.UTF_8).trimStart()
        if (response.code in 200..299 && prefix.isNotEmpty() && !prefix.contentEquals(missingImageBody.toByteArray()) &&
            !start.startsWith("<!doctype html", ignoreCase = true) && !start.startsWith("<html", ignoreCase = true)) return
        val time = now()
        response.headers["Retry-After"]?.let { header ->
            val delay = retryAfterMillis(header, time)
            if (delay > 0) attempts.postponeHost(time + delay.coerceAtMost(Long.MAX_VALUE - time), host)
        }
        throw IOException("Image unavailable (HTTP ${response.code})")
    }
}
