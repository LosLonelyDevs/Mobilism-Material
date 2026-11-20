package org.mobilism.material.images

import android.content.Context
import coil3.ImageLoader
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import okhttp3.OkHttpClient
import okhttp3.HttpUrl
import org.mobilism.material.data.AppUserAgent
import org.mobilism.material.data.AppUserAgentInterceptor

internal fun appImageLoader(context: Context, baseClient: OkHttpClient = OkHttpClient()): ImageLoader {
    val application = context.applicationContext
    val headers = AppUserAgentInterceptor(AppUserAgent.defaultValue(application),
        { AppUserAgent.value(application) }, { AppUserAgent.response(application, it) })
    val client = baseClient.newBuilder().addInterceptor { chain ->
        val request = chain.request()
        chain.proceed(request.newBuilder().url(secureMobilismImageUrl(request.url)).build())
    }.addNetworkInterceptor(headers).addNetworkInterceptor(org.mobilism.material.data.RequestTrafficInterceptor(
        (application as? org.mobilism.material.data.RequestTrafficProvider)?.requestTraffic
            ?: org.mobilism.material.data.RequestTrafficSink.None, image = true)).build()
    return ImageLoader.Builder(application).components {
        add(OkHttpNetworkFetcherFactory(callFactory = { client }))
    }.build()
}

internal fun secureMobilismImageUrl(url: HttpUrl): HttpUrl =
    if (url.scheme == "http" && url.port == 80 && url.username.isEmpty() && url.password.isEmpty() &&
        (url.host == "mobilism.org" || url.host.endsWith(".mobilism.org")))
        url.newBuilder().scheme("https").port(443).build() else url
