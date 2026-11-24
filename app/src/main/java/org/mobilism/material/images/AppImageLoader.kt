package org.mobilism.material.images

import android.content.Context
import coil3.ImageLoader
import coil3.EventListener
import coil3.Extras
import coil3.annotation.ExperimentalCoilApi
import coil3.decode.DataSource
import coil3.intercept.Interceptor
import coil3.request.SuccessResult
import coil3.request.ErrorResult
import coil3.request.ImageRequest
import coil3.request.Options
import coil3.request.CachePolicy
import coil3.fetch.Fetcher
import coil3.fetch.FetchResult
import coil3.fetch.SourceFetchResult
import coil3.decode.Decoder
import coil3.decode.DecodeResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.mobilism.material.data.ImageCacheControls
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.mobilism.material.data.AppCache
import org.mobilism.material.data.CacheOutcome
import org.mobilism.material.data.DiagnosticLogger
import org.mobilism.material.data.cacheLookup
import coil3.network.NetworkFetcher
import coil3.network.DeDupeConcurrentRequestStrategy
import okhttp3.OkHttpClient
import okhttp3.HttpUrl
import org.mobilism.material.data.AppUserAgent
import org.mobilism.material.data.MobilismResponseInterceptor
import org.mobilism.material.data.AppHttp

internal val releaseArtworkKey = Extras.Key(default = false)
private class ImageDecode { var source: DataSource? = null }
private val decodeKey = Extras.Key<ImageDecode?>(default = null)

private object ImageDecodeListener : EventListener() {
    override fun fetchEnd(request: ImageRequest, fetcher: Fetcher, options: Options, result: FetchResult?) {
        options.extras[decodeKey]?.source = (result as? SourceFetchResult)?.dataSource
    }

    override fun decodeEnd(request: ImageRequest, decoder: Decoder, options: Options, result: DecodeResult?) {
        if (result != null) options.extras[decodeKey]?.source = null
    }
}

@OptIn(ExperimentalCoilApi::class)
internal fun appImageLoader(context: Context, baseClient: OkHttpClient = AppHttp.client,
    diagnostics: DiagnosticLogger = DiagnosticLogger.None, imageControls: () -> ImageCacheControls = { ImageCacheControls() },
    now: () -> Long = System::currentTimeMillis): ImageLoader {
    val application = context.applicationContext
    val client = AppHttp.builder(baseClient).addInterceptor { chain ->
        val request = chain.request()
        chain.proceed(request.newBuilder().url(secureMobilismImageUrl(request.url)).build())
    }.addNetworkInterceptor(MobilismResponseInterceptor { AppUserAgent.response(application, it) }).addNetworkInterceptor(org.mobilism.material.data.RequestTrafficInterceptor(
        (application as? org.mobilism.material.data.RequestTrafficProvider)?.requestTraffic
            ?: org.mobilism.material.data.RequestTrafficSink.None, image = true)).build()
    lateinit var loader: ImageLoader
    val recovery = MissingImageRecovery(
        diskCache = { loader.diskCache },
        attempts = SavedImageRetryWindow(application.getSharedPreferences("missing-image-recovery", Context.MODE_PRIVATE)) { imageControls().retryGeneration },
        retriesEnabled = { imageControls().retriesEnabled },
        now = now,
    )
    loader = ImageLoader.Builder(application).eventListener(ImageDecodeListener).components {
        add(Interceptor { chain ->
            val url = chain.request.data.toString().toHttpUrlOrNull()
            if (url == null) return@Interceptor chain.proceed()
            val mobilism = url.host == "mobilism.org" || url.host.endsWith(".mobilism.org")
            val artwork = url.host == "images.mobilism.org" || chain.request.extras[releaseArtworkKey] == true
            if (!mobilism && !artwork) return@Interceptor chain.proceed()
            val canonical = secureMobilismImageUrl(url)
            val decode = ImageDecode()
            val request = chain.request.newBuilder().apply {
                if (canonical != url) data(canonical.toString())
                if (artwork) {
                    extras[decodeKey] = decode
                    if (!imageControls().cacheEnabled && chain.request.networkCachePolicy.readEnabled) {
                        memoryCachePolicy(CachePolicy.DISABLED)
                        diskCachePolicy(CachePolicy.DISABLED)
                    }
                }
            }.build()
            val result = chain.withRequest(request).proceed()
            if (artwork && result is ErrorResult && decode.source != null && result.throwable is Exception) {
                withContext(Dispatchers.IO) {
                    val cached = decode.source == DataSource.DISK || request.diskCachePolicy.writeEnabled
                    recovery.decodeFailed(canonical.toString(), (request.diskCacheKey ?: canonical.toString()).takeIf { cached })
                }
            }
            if (result is SuccessResult) {
                if (artwork && result.dataSource == DataSource.NETWORK) {
                    recovery.imageLoaded(canonical.toString())
                }
                val outcome = when (result.dataSource) {
                    DataSource.MEMORY_CACHE -> CacheOutcome.MemoryHit
                    DataSource.DISK -> CacheOutcome.DiskHit
                    DataSource.NETWORK -> CacheOutcome.Miss
                    else -> null
                }
                if (outcome != null) diagnostics.cacheLookup(AppCache.Images, outcome)
            }
            result
        })
        add(NetworkFetcher.Factory(
            networkClient = { recovery.networkClient(client) },
            cacheStrategy = { recovery },
            concurrentRequestStrategy = { DeDupeConcurrentRequestStrategy() },
        ))
    }.build()
    return loader
}

internal fun secureMobilismImageUrl(url: HttpUrl): HttpUrl =
    if (url.scheme == "http" && url.port == 80 && url.username.isEmpty() && url.password.isEmpty() &&
        (url.host == "mobilism.org" || url.host.endsWith(".mobilism.org")))
        url.newBuilder().scheme("https").port(443).build() else url
