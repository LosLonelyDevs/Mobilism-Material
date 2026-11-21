package org.mobilism.material.images

import android.content.Context
import coil3.ImageLoader
import coil3.decode.DataSource
import coil3.intercept.Interceptor
import coil3.request.SuccessResult
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.mobilism.material.data.AppCache
import org.mobilism.material.data.CacheOutcome
import org.mobilism.material.data.DiagnosticLogger
import org.mobilism.material.data.cacheLookup
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import okhttp3.OkHttpClient
import okhttp3.HttpUrl
import org.mobilism.material.data.AppUserAgent
import org.mobilism.material.data.MobilismResponseInterceptor
import org.mobilism.material.data.AppHttp

internal fun appImageLoader(context: Context, baseClient: OkHttpClient = AppHttp.client,
    diagnostics: DiagnosticLogger = DiagnosticLogger.None): ImageLoader {
    val application = context.applicationContext
    val client = AppHttp.builder(baseClient).addInterceptor { chain ->
        val request = chain.request()
        chain.proceed(request.newBuilder().url(secureMobilismImageUrl(request.url)).build())
    }.addNetworkInterceptor(MobilismResponseInterceptor { AppUserAgent.response(application, it) }).addNetworkInterceptor(org.mobilism.material.data.RequestTrafficInterceptor(
        (application as? org.mobilism.material.data.RequestTrafficProvider)?.requestTraffic
            ?: org.mobilism.material.data.RequestTrafficSink.None, image = true)).build()
    return ImageLoader.Builder(application).components {
        add(Interceptor { chain ->
            val url = chain.request.data.toString().toHttpUrlOrNull()
            if (url == null || !(url.host == "mobilism.org" || url.host.endsWith(".mobilism.org")))
                return@Interceptor chain.proceed()
            val canonical = secureMobilismImageUrl(url)
            val request = if (canonical == url) chain.request else chain.request.newBuilder().data(canonical.toString()).build()
            val result = chain.withRequest(request).proceed()
            if (result is SuccessResult) {
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
        add(OkHttpNetworkFetcherFactory(callFactory = { client }))
    }.build()
}

internal fun secureMobilismImageUrl(url: HttpUrl): HttpUrl =
    if (url.scheme == "http" && url.port == 80 && url.username.isEmpty() && url.password.isEmpty() &&
        (url.host == "mobilism.org" || url.host.endsWith(".mobilism.org")))
        url.newBuilder().scheme("https").port(443).build() else url
