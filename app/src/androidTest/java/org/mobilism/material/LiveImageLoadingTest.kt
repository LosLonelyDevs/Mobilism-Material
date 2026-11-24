package org.mobilism.material

import android.content.Context
import android.content.ContextWrapper
import android.os.Bundle
import androidx.test.platform.app.InstrumentationRegistry
import coil3.SingletonImageLoader
import coil3.asDrawable
import coil3.request.CachePolicy
import coil3.request.ErrorResult
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.mobilism.material.images.SavedImageRetryWindow
import org.mobilism.material.data.ImageCacheControls

class LiveImageLoadingTest {
    @Test fun failedImageBudgetAndServerWaitSurviveReopeningTheStore() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "fixture-retry-window-${java.util.UUID.randomUUID()}"
        val prefs = context.getSharedPreferences(name, Context.MODE_PRIVATE)
        val start = 1_800_000_000_000L
        fun window() = SavedImageRetryWindow(prefs)
        try {
            for (key in listOf("a", "b", "c")) assertTrue(window().reserve(key, start))
            assertEquals(false, window().reserve("d", start + 59_999))
            assertTrue(window().reserve("d", start + 60_000))
            assertEquals(false, window().reserve("a", start + 299_999))
            assertTrue(window().reserve("a", start + 300_000))
            assertEquals(false, window().reserve("a", start + 2_099_999))
            assertTrue(window().reserve("a", start + 2_100_000))
            val resume = start + 12 * 3_600_000L
            window().postponeHost(resume)
            window().recovered("a")
            assertEquals(false, window().reserve("a", resume - 1))
            assertEquals(false, window().reserve("e", resume - 1))
            assertTrue(window().reserve("a", resume))
            assertTrue(window().reserve("a", resume + 300_000))
        } finally { context.deleteSharedPreferences(name) }
    }

    @Test fun aRemoteCommandShortensOnlyLocalBackoffOnceAcrossRestarts() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "fixture-image-command-${java.util.UUID.randomUUID()}"
        val prefs = context.getSharedPreferences(name, Context.MODE_PRIVATE)
        var generation = 0
        fun window() = SavedImageRetryWindow(prefs) { generation }
        val start = 1_800_000_000_000L
        try {
            window().failed("a", start)
            assertTrue(window().reserve("a", start + 60_000))
            generation = 10
            assertEquals(false, window().reserve("a", start + 60_001))
            window().postponeHost(start + 130_000)
            assertEquals(false, window().reserve("a", start + 120_000))
            assertTrue(window().reserve("a", start + 130_000))
            generation = 0
            assertEquals(false, window().reserve("a", start + 200_000))
            generation = 10
            assertEquals(false, window().reserve("a", start + 200_000))
            assertTrue(window().reserve("a", start + 430_000))
        } finally { context.deleteSharedPreferences(name) }
    }

    @Test fun badImagesStayOffDiskAndRemoteSwitchesControlLaterViews() = runBlocking {
        val base = InstrumentationRegistry.getInstrumentation().targetContext
        val preferenceName = "fixture-image-recovery-${java.util.UUID.randomUUID()}"
        val context = object : ContextWrapper(base) {
            override fun getApplicationContext(): Context = this
            override fun getSharedPreferences(name: String, mode: Int) =
                base.getSharedPreferences(if (name == "missing-image-recovery") preferenceName else name, mode)
        }
        val url = "https://images.mobilism.org/?dm=fixture-${java.util.UUID.randomUUID()}"
        val corruptUrl = "$url-corrupt"
        val expected = java.io.ByteArrayOutputStream().also { out ->
            val bitmap = android.graphics.Bitmap.createBitmap(2, 2, android.graphics.Bitmap.Config.ARGB_8888)
            bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, out)
            bitmap.recycle()
        }.toByteArray()
        var calls = 0
        var controls = ImageCacheControls()
        var time = System.currentTimeMillis()
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            val requested = chain.request().url.toString()
            assertTrue(requested == url || requested == corruptUrl)
            val missing = ++calls == 1
            val received = time
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                .receivedResponseAtMillis(received).sentRequestAtMillis(received)
                .header("Content-Type", if (missing) "text/html" else "image/png")
                .body((when {
                    missing -> "can't open image".toByteArray()
                    requested == corruptUrl -> expected.copyOf(12)
                    else -> expected
                }).toResponseBody()).build()
        }.build()
        val loader = org.mobilism.material.images.appImageLoader(context, client, imageControls = { controls }, now = { time })
        try {
            val request = ImageRequest.Builder(context).data(url).size(2, 2).memoryCachePolicy(CachePolicy.DISABLED).build()
            assertTrue(loader.execute(request) is ErrorResult)
            assertTrue(loader.diskCache?.openSnapshot(url)?.use { false } ?: true)
            time += 61_000
            controls = controls.copy(retriesEnabled = false)
            assertTrue(loader.execute(request) is ErrorResult)
            assertEquals(1, calls)
            controls = controls.copy(retriesEnabled = true)
            val recovered = loader.execute(request) as SuccessResult
            assertEquals(2, recovered.image.width)
            assertEquals(coil3.decode.DataSource.NETWORK, recovered.dataSource)
            assertEquals(coil3.decode.DataSource.DISK, (loader.execute(request) as SuccessResult).dataSource)
            assertEquals(2, calls)
            controls = controls.copy(cacheEnabled = false)
            assertEquals(coil3.decode.DataSource.NETWORK, (loader.execute(request) as SuccessResult).dataSource)
            assertEquals(3, calls)
            controls = controls.copy(cacheEnabled = true)
            assertEquals(coil3.decode.DataSource.DISK, (loader.execute(request) as SuccessResult).dataSource)
            assertEquals(3, calls)
            val corrupt = request.newBuilder().data(corruptUrl).build()
            assertTrue(loader.execute(corrupt) is ErrorResult)
            assertTrue(loader.diskCache?.openSnapshot(corruptUrl)?.use { false } ?: true)
            assertTrue(loader.execute(corrupt) is ErrorResult)
            assertEquals(4, calls)
        } finally {
            loader.diskCache?.remove(url)
            loader.diskCache?.remove(corruptUrl)
            loader.shutdown()
            base.deleteSharedPreferences(preferenceName)
        }
    }

    @Test fun sharedLoaderRetainsAnimatedAvatarFrames() = runBlocking {
        assumeTrue(InstrumentationRegistry.getArguments().getString("liveImages") == "true")
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val loader = withContext(Dispatchers.Main) { SingletonImageLoader.get(context) }
        val bytes = java.io.ByteArrayOutputStream()
        fun write(vararg values: Int) { values.forEach(bytes::write) }
        bytes.write("GIF89a".toByteArray(Charsets.US_ASCII))
        write(1, 0, 1, 0, 0x80, 0, 0, 0, 0, 0, 255, 255, 255)
        for (pixel in 0..1) {
            write(0x21, 0xf9, 4, 0, 10, 0, 0, 0)
            write(0x2c, 0, 0, 0, 0, 1, 0, 1, 0, 0)
            write(2, 2, if (pixel == 0) 0x44 else 0x4c, 1, 0)
        }
        write(0x3b)
        val result = loader.execute(ImageRequest.Builder(context).data(bytes.toByteArray()).build())
        assertTrue(result.toString(), result is SuccessResult)
        assertTrue("GIF must retain its frames", (result as SuccessResult).image.asDrawable(context.resources)
            is android.graphics.drawable.Animatable)
    }

    @Test fun sharedLoaderDecodesReportedReleaseImage() = runBlocking {
        assumeTrue(InstrumentationRegistry.getArguments().getString("liveImages") == "true")
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val loader = withContext(Dispatchers.Main) { SingletonImageLoader.get(context) }
        for (url in listOf("https://images.mobilism.org/?dm=UP6E", "https://images.mobilism.org/?dm=17UUNW1O",
            "http://images.mobilism.org/?dm=2W3WO076")) {
            val result = loader.execute(ImageRequest.Builder(context).data(url)
                .memoryCachePolicy(CachePolicy.DISABLED).diskCachePolicy(CachePolicy.DISABLED).build())
            val error = (result as? ErrorResult)?.throwable?.toString()
            instrumentation.sendStatus(0, Bundle().apply {
                putString("imageResult", if (result is SuccessResult)
                    "decoded ${result.image.width}x${result.image.height}" else error)
            })
            assertTrue(error ?: "Image did not decode", result is SuccessResult)
        }
    }
}
