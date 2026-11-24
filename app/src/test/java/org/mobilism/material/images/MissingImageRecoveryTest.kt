package org.mobilism.material.images

import android.content.ContextWrapper
import coil3.annotation.ExperimentalCoilApi
import coil3.disk.DiskCache
import coil3.network.NetworkHeaders
import coil3.network.NetworkRequest
import coil3.network.NetworkResponse
import coil3.request.CachePolicy
import coil3.request.Options
import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import okio.ByteString.Companion.encodeUtf8
import okio.FileSystem
import okio.Path.Companion.toOkioPath
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Proxy
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

@OptIn(ExperimentalCoilApi::class)
class MissingImageRecoveryTest {
    @get:Rule val folder = TemporaryFolder()

    private class RetryTimes : ImageRetryWindow {
        private val reservations = mutableMapOf<String, ImageRetryReservation>()
        private val recent = MutableList(3) { 0L }
        private var hostUntil = 0L

        private val failures = mutableSetOf<String>()
        override fun knownFailure(key: String) = key in failures || key in reservations
        override fun failed(key: String, time: Long) {
            if (!knownFailure(key)) reservations[key] = ImageRetryReservation(0, 0, time + firstImageRetryMs)
            failures.add(key)
        }
        override fun reserve(key: String, now: Long, host: String): Boolean {
            val prior = reservations[key]
            val reservation = imageRetryReservation(now, maxOf(hostUntil, prior?.nextAllowed ?: 0L),
                prior?.nextStep ?: 0, recent) ?: return false
            recent[reservation.slot] = now
            reservations[key] = reservation
            return true
        }
        override fun postponeHost(until: Long, host: String) { hostUntil = maxOf(hostUntil, until) }
        override fun recovered(key: String) { reservations.remove(key); failures.remove(key) }
    }

    @Test fun repeatedFailuresBackOffPerImageAndShareARollingMinuteBudget() {
        val attempts = RetryTimes()
        val start = 1_800_000_000_000L
        val minute = 60_000L
        assertTrue(attempts.reserve("a", start))
        assertTrue(attempts.reserve("b", start + 1_000))
        assertTrue(attempts.reserve("c", start + 2_000))
        assertFalse(attempts.reserve("d", start + minute - 1))
        assertTrue(attempts.reserve("d", start + minute))
        assertFalse(attempts.reserve("e", start + minute))
        assertTrue(attempts.reserve("e", start + minute + 1_000))

        var time = start
        for (wait in listOf(5, 30, 60, 60)) {
            time += wait * minute
            assertFalse(attempts.reserve("a", time - 1))
            assertTrue(attempts.reserve("a", time))
        }
        attempts.recovered("a")
        time += minute
        assertTrue(attempts.reserve("a", time))
        assertTrue(attempts.reserve("a", time + 5 * minute))
    }

    @Test fun badResponsesNeverReachTheCacheAndPausedRetriesMakeNoCalls() = runBlocking {
        var time = 1_800_000_000_000L
        var enabled = true
        var calls = 0
        var payload = ""
        val attempts = RetryTimes()
        val recovery = MissingImageRecovery({ null }, attempts, { enabled }) { time }
        val client = recovery.networkClient(OkHttpClient.Builder().addInterceptor { chain ->
            calls++
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                .body(payload.toResponseBody()).build()
        }.build())
        for ((index, invalid) in listOf("", "can't open image", "<html>Image unavailable</html>").withIndex()) {
            val request = NetworkRequest("https://images.mobilism.org/?dm=broken$index")
            payload = invalid
            suspend fun failedLoad() {
                val error = runCatching { client.executeRequest(request) { fail("Invalid bytes reached the cache writer") } }
                assertTrue(error.exceptionOrNull() is IOException)
            }
            val before = calls
            failedLoad()
            failedLoad()
            time += firstImageRetryMs
            enabled = false
            failedLoad()
            assertEquals(before + 1, calls)
            enabled = true
            payload = "recovered bytes"
            client.executeRequest(request) { assertEquals(200, it.code) }
            recovery.imageLoaded(request.url)
            assertEquals(before + 2, calls)
        }
    }

    @Test fun decoderFailureEvictsOnlyTheBadBytesAndKeepsItsRetryDeadline() {
        val disk = DiskCache.Builder().fileSystem(FileSystem.SYSTEM).directory(folder.root.toOkioPath())
            .maxSizeBytes(1024 * 1024).build()
        val time = 1_800_000_000_000L
        val attempts = RetryTimes()
        val recovery = MissingImageRecovery({ disk }, attempts) { time }
        try {
            for (key in listOf("bad", "good")) {
                val entry = requireNotNull(disk.openEditor(key))
                disk.fileSystem.write(entry.data) { writeUtf8("fixture") }
                entry.commit()
            }
            recovery.decodeFailed("https://images.mobilism.org/?dm=decode", "bad")
            assertNull(disk.openSnapshot("bad"))
            assertNotNull(disk.openSnapshot("good")?.use { it.data })
            val id = "https://images.mobilism.org/?dm=decode".encodeUtf8().sha256().hex()
            assertTrue(attempts.knownFailure(id))
            assertFalse(attempts.reserve(id, time))
            assertTrue(attempts.reserve(id, time + firstImageRetryMs))
        } finally { disk.shutdown() }
    }

    @Test fun aLaterViewCanRecoverARealImageWhileHealthyDiskEntriesStayLocal() = runBlocking {
        val disk = DiskCache.Builder().fileSystem(FileSystem.SYSTEM).directory(folder.root.toOkioPath())
            .maxSizeBytes(1024 * 1024).build()
        val url = "https://images.mobilism.org/?dm=legacy"
        var time = 1_800_000_000_000L
        val old = NetworkResponse(code = 200, responseMillis = time)
        val request = NetworkRequest(url)
        val options = Options(ContextWrapper(null), fileSystem = FileSystem.SYSTEM)
        var calls = 0
        val png = java.io.ByteArrayOutputStream().apply {
            javax.imageio.ImageIO.write(java.awt.image.BufferedImage(2, 2, java.awt.image.BufferedImage.TYPE_INT_RGB), "png", this)
        }.toByteArray()
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            assertEquals(url, chain.request().url.toString())
            calls++
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                .sentRequestAtMillis(time).receivedResponseAtMillis(time)
                .header("Content-Type", "image/png").body(png.toResponseBody()).build()
        }.build()
        val recovery = MissingImageRecovery({ disk }, RetryTimes()) { time }
        fun store(bytes: ByteArray) {
            val editor = requireNotNull(disk.openEditor(url))
            disk.fileSystem.write(editor.data) { write(bytes) }
            editor.commit()
        }
        try {
            store(missingImageBody.toByteArray())
            time += firstImageRetryMs - 1
            assertSame(old, recovery.read(old, request, options).response)
            time++
            val retry = requireNotNull(recovery.read(old, request, options).request)
            val valid = recovery.networkClient(client).executeRequest(retry) { response ->
                val saved = requireNotNull(recovery.write(old, retry, response, options).response)
                val buffer = Buffer()
                requireNotNull(saved.body).writeTo(buffer)
                val bytes = buffer.readByteArray()
                assertEquals(2, javax.imageio.ImageIO.read(bytes.inputStream()).width)
                store(bytes)
                recovery.imageLoaded(url)
                saved.copy(body = null)
            }
            time += 2 * 3_600_000L
            assertSame(valid, recovery.read(valid, request, options).response)
            assertEquals(1, calls)
        } finally { disk.shutdown() }
    }

    @Test fun onlyAnExpiredExactErrorGetsOneRecoveryAndRetryAfterSurvivesAnotherLoad() = runBlocking {
        val disk = DiskCache.Builder().fileSystem(FileSystem.SYSTEM).directory(folder.root.toOkioPath())
            .maxSizeBytes(1024 * 1024).build()
        val url = "https://images.mobilism.org/?dm=fixture"
        val request = NetworkRequest(url)
        val options = Options(ContextWrapper(null), fileSystem = FileSystem.SYSTEM)
        val cachedAt = 1_800_000_000_000L
        val cached = NetworkResponse(code = 200, responseMillis = cachedAt)
        var time = cachedAt
        val attempts = RetryTimes()
        val recovery = MissingImageRecovery({ disk }, attempts) { time }
        fun store(text: String) {
            val editor = requireNotNull(disk.openEditor(url))
            disk.fileSystem.write(editor.data) { writeUtf8(text) }
            editor.commit()
        }
        try {
            store("can't open image")
            time += firstImageRetryMs - 1
            assertSame(cached, recovery.read(cached, request, options).response)
            time++
            assertSame(cached, recovery.read(cached, request, options.copy(networkCachePolicy = CachePolicy.DISABLED)).response)
            val offline = request.copy(headers = NetworkHeaders.Builder().apply { set("Cache-Control", "only-if-cached") }.build())
            assertSame(cached, recovery.read(cached, offline, options).response)
            val external = request.copy(url = "https://elsewhere.example/image")
            assertSame(cached, recovery.read(cached, external, options.copy(diskCacheKey = url)).response)
            store("0123456789abcdef")
            assertSame(cached, recovery.read(cached, request, options).response)
            store("can't open image")
            val retry = requireNotNull(recovery.read(cached, request, options).request)
            assertSame(cached, recovery.read(cached, request, options).response)

            val calls = AtomicInteger()
            val retryAfter = AtomicReference("43200")
            val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
            server.createContext("/") { exchange ->
                calls.incrementAndGet()
                exchange.responseHeaders.add("Retry-After", retryAfter.get())
                exchange.sendResponseHeaders(503, -1)
                exchange.close()
            }
            server.start()
            try {
                val client = OkHttpClient.Builder().proxy(Proxy.NO_PROXY).addInterceptor { chain ->
                    assertEquals(url, chain.request().url.toString())
                    chain.proceed(chain.request().newBuilder().url("http://127.0.0.1:${server.address.port}/").build())
                }.build()
                suspend fun expectFailure(next: NetworkRequest) {
                    val result = runCatching { recovery.networkClient(client).executeRequest(next) { fail("Must preserve cached body") } }
                    assertTrue(result.exceptionOrNull() is IOException)
                }
                expectFailure(retry)
                assertEquals(1, calls.get())
                time += 12 * 3_600_000L - 1
                assertSame(cached, MissingImageRecovery({ disk }, attempts) { time }.read(cached, request, options).response)
                assertFalse(attempts.reserve("different failed thumbnail", time))
                time++
                retryAfter.set("0")
                expectFailure(requireNotNull(recovery.read(cached, request, options).request))
                assertEquals(2, calls.get())
            } finally { server.stop(0) }
        } finally { disk.shutdown() }
    }

    @Test fun removingALegacyErrorKeepsItsServerWaitEvenWhileRetriesArePaused() = runBlocking {
        val disk = DiskCache.Builder().fileSystem(FileSystem.SYSTEM).directory(folder.root.toOkioPath())
            .maxSizeBytes(1024 * 1024).build()
        val url = "https://images.mobilism.org/?dm=previous-release"
        val start = 1_800_000_000_000L
        var time = start + firstImageRetryMs
        var enabled = false
        val attempts = RetryTimes()
        val recovery = MissingImageRecovery({ disk }, attempts, { enabled }) { time }
        val cache = NetworkResponse(code = 200, responseMillis = start, headers = NetworkHeaders.Builder()
            .apply { set("Retry-After", "7200") }.build())
        val options = Options(ContextWrapper(null), fileSystem = FileSystem.SYSTEM)
        try {
            val editor = requireNotNull(disk.openEditor(url))
            disk.fileSystem.write(editor.data) { write(missingImageBody) }
            editor.commit()
            assertSame(cache, recovery.read(cache, NetworkRequest(url), options).response)
            recovery.decodeFailed(url, url)
            enabled = true
            time = start + 7_200_000 - 1
            val id = url.encodeUtf8().sha256().hex()
            assertFalse(attempts.reserve(id, time))
            assertTrue(attempts.reserve(id, time + 1))
        } finally { disk.shutdown() }
    }
}
