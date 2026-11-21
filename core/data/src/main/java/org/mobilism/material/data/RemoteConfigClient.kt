package org.mobilism.material.data

import java.io.IOException
import java.net.URI
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

class RemoteConfigClient(usageEndpoint: String,
    private val client: OkHttpClient = AppHttp.builder().callTimeout(10, TimeUnit.SECONDS)
        .followRedirects(false).retryOnConnectionFailure(false).build()) {
    private val endpoint = endpoint(usageEndpoint)

    suspend fun fetch(): RemoteAppConfig = withContext(Dispatchers.IO) {
        val url = endpoint ?: throw IOException("Remote configuration is not configured")
        client.newCall(Request.Builder().url(url).header("Accept", "application/json")
            .build()).execute().use { response ->
            if (!response.isSuccessful) throw IOException("Remote configuration unavailable")
            val source = response.body?.source() ?: throw IOException("Empty configuration")
            source.request(65_537)
            if (source.buffer.size > 65_536) throw IOException("Configuration too large")
            RemoteAppConfig.parse(source.readUtf8())
        }
    }

    companion object {
        fun endpoint(usageEndpoint: String): String? = serviceEndpoint(usageEndpoint, "/api/app-config")
    }
}

internal fun serviceEndpoint(usageEndpoint: String, path: String): String? = runCatching {
    val uri = URI(usageEndpoint)
    require(uri.scheme == "https" && !uri.host.isNullOrBlank() && uri.userInfo == null && uri.query == null && uri.fragment == null)
    uri.resolve(path).toString()
}.getOrNull()
