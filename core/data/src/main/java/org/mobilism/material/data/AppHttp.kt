package org.mobilism.material.data

import android.content.Context
import android.webkit.WebView
import java.net.HttpURLConnection
import java.net.URL
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response

object AppHttp {
    @Volatile private var application: Context? = null
    private val transport = OkHttpClient()

    fun initialize(context: Context) {
        application = context.applicationContext
    }

    private fun currentUserAgent() = AppUserAgent.value(checkNotNull(application) { "AppHttp is not initialized" })

    val client: OkHttpClient by lazy { builder().build() }

    fun builder(base: OkHttpClient = transport): OkHttpClient.Builder = builder(base, ::currentUserAgent)

    internal fun builder(base: OkHttpClient, userAgent: () -> String): OkHttpClient.Builder = base.newBuilder().apply {
        if (interceptors().none { it is Identity }) interceptors().add(0, Identity(userAgent))
    }

    fun openConnection(url: URL): HttpURLConnection = (url.openConnection() as HttpURLConnection).apply {
        setRequestProperty("User-Agent", currentUserAgent())
    }

    fun webView(context: Context): WebView = WebView(context).also(::applyBrowserIdentity)

    fun applyBrowserIdentity(view: WebView) {
        view.settings.userAgentString = currentUserAgent()
    }

    private class Identity(private val userAgent: () -> String) : Interceptor {
        override fun intercept(chain: Interceptor.Chain): Response =
            chain.proceed(chain.request().newBuilder().header("User-Agent", userAgent()).build())
    }
}
