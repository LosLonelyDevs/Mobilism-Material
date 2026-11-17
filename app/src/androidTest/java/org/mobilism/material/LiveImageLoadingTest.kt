package org.mobilism.material

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
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

class LiveImageLoadingTest {
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
