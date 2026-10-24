package org.mobilism.material.data

import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import okhttp3.FormBody
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

class DebridException(message: String, val invalidatesAccount: Boolean = false) : Exception(message)

internal class DebridApi(client: OkHttpClient = OkHttpClient()) {
    private val client = client.newBuilder()
        .connectTimeout(15, TimeUnit.SECONDS).readTimeout(30, TimeUnit.SECONDS)
        .callTimeout(45, TimeUnit.SECONDS)
        // MegaDebrid requires its token in the query. Never forward it through redirects.
        .followRedirects(false).followSslRedirects(false).build()

    fun validate(provider: DebridProvider, key: String) {
        checkKey(key)
        when (provider) {
            DebridProvider.RealDebrid -> {
                val user = request(provider, realRequest("user", key).build())
                if (user.optLong("id") <= 0 || !user.has("type")) throw invalidResponse()
                if (user.optString("type") != "premium" || user.optLong("premium") <= 0) {
                    throw DebridException("An active Real-Debrid Premium subscription is required.", true)
                }
            }
            DebridProvider.MegaDebrid -> {
                // The documented token check is authenticated history. Discard its contents.
                val url = megaUrl("getUserHistory", key).newBuilder().addQueryParameter("limit", "1").build()
                val result = request(provider, Request.Builder().url(url).build())
                if (!result.has("history")) throw invalidResponse()
            }
        }
    }

    fun availableHosts(provider: DebridProvider, key: String, catalogDomains: List<String> = emptyList()): List<SupportedHost> {
        checkKey(key)
        return when (provider) {
            DebridProvider.RealDebrid -> {
                val result = request(provider, realRequest("hosts/status", key).build())
                // Real-Debrid omits some working hosts from status; keep catalog entries unless status rules them out.
                val domains = catalogDomains.ifEmpty { result.keys().asSequence().toList() }
                domains.mapNotNull { value ->
                    val domain = HostMatcher.domain(value) ?: return@mapNotNull null
                    val host = result.optJSONObject(domain) ?: result.optJSONObject(HostMatcher.key(domain).orEmpty())
                    if (host != null && (host.optInt("supported") != 1 || host.optString("status") != "up")) return@mapNotNull null
                    SupportedHost(domain, host?.optString("name")?.takeIf(String::isNotBlank)
                        ?: HostMatcher.displayName(domain))
                }
            }
            DebridProvider.MegaDebrid -> {
                val result = request(provider, Request.Builder()
                    .url("https://www.mega-debrid.eu/api.php?action=getHostersList").build())
                val hosts = result.optJSONArray("hosters") ?: throw invalidResponse()
                buildList {
                    for (index in 0 until hosts.length()) {
                        val host = hosts.getJSONObject(index)
                        if (host.optString("status") != "up") continue
                        val domains = host.optJSONArray("domains") ?: continue
                        for (entry in 0 until domains.length()) {
                            val domain = HostMatcher.domain(domains.getString(entry)) ?: continue
                            add(SupportedHost(domain, host.optString("url").ifBlank {
                                host.optString("name").ifBlank { HostMatcher.displayName(domain) }
                            }))
                        }
                    }
                }
            }
        }.distinctBy { it.domain }
    }

    fun resolve(provider: DebridProvider, key: String, original: String): String {
        checkKey(key)
        val source = webUrl(original) ?: throw DebridException("This download link is not a valid web address.")
        val body = FormBody.Builder().add("link", source).build()
        val request = when (provider) {
            DebridProvider.RealDebrid -> realRequest("unrestrict/link", key).post(body).build()
            DebridProvider.MegaDebrid -> Request.Builder().url(megaUrl("getLink", key)).post(body).build()
        }
        val result = request(provider, request)
        val field = if (provider == DebridProvider.RealDebrid) "download" else "debridLink"
        val resolved = webUrl(result.optString(field)) ?: throw invalidResponse()
        if (resolved == source || resolved.contains(key)) throw invalidResponse()
        return resolved
    }

    private fun realRequest(method: String, key: String) = Request.Builder()
        .url("https://api.real-debrid.com/rest/1.0/$method")
        .header("Authorization", "Bearer $key")

    private fun megaUrl(action: String, key: String) = "https://www.mega-debrid.eu/api.php".toHttpUrl()
        .newBuilder().addQueryParameter("action", action).addQueryParameter("token", key).build()

    private fun request(provider: DebridProvider, request: Request): JSONObject {
        try {
            return client.newCall(request.newBuilder()
                .header("User-Agent", "MobilismMaterial/0.1 (Android)").build()).execute().use { response ->
                if (response.code in 300..399) throw DebridException("The service redirected the request. Please try again later.")
                val source = response.body?.source() ?: throw invalidResponse()
                source.request(MAX_RESPONSE_BYTES + 1)
                if (source.buffer.size > MAX_RESPONSE_BYTES) throw invalidResponse()
                val json = runCatching { JSONObject(source.readUtf8()) }.getOrNull()
                when (provider) {
                    DebridProvider.RealDebrid -> {
                        if (!response.isSuccessful || json?.has("error") == true || json?.has("error_code") == true) {
                            throw realError(response.code, json?.optInt("error_code", -1) ?: -1)
                        }
                    }
                    DebridProvider.MegaDebrid -> {
                        if (!response.isSuccessful || json?.optString("response_code") != "ok") {
                            throw megaError(response.code, json)
                        }
                    }
                }
                json ?: throw invalidResponse()
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: DebridException) {
            throw failure
        } catch (_: IOException) {
            throw DebridException("Could not reach the service. Check your connection and try again.")
        } catch (_: Exception) {
            // Provider responses and exception messages can contain credentials or private URLs.
            throw invalidResponse()
        }
    }

    private fun realError(status: Int, code: Int): DebridException = when (code) {
        8, 12, 13 -> DebridException("The key is invalid or expired. Remove it and add a valid key in Settings.", true)
        9, 14, 15 -> DebridException("The account is locked or does not have permission to download.", true)
        10, 11 -> DebridException("Complete two-factor authentication on Real-Debrid, then try again.")
        5 -> DebridException("Too many requests. Wait a moment and try again.")
        6, 7, 24 -> DebridException("The file is unavailable or the link has expired.")
        16 -> DebridException("This file host is not supported by Real-Debrid.")
        17, 19 -> DebridException("This file host is temporarily unavailable on Real-Debrid.")
        18, 21, 23 -> DebridException("The account's download or traffic limit has been reached.")
        20 -> DebridException("An active Real-Debrid Premium subscription is required.", true)
        22 -> DebridException("Real-Debrid does not allow downloads from this network.")
        else -> httpError(status)
    }

    private fun megaError(status: Int, response: JSONObject?): DebridException {
        // Use provider text only for classification; never display it or log it.
        val reason = (response?.optString("response_code").orEmpty() + " " +
            response?.optString("response_text").orEmpty()).lowercase()
        return when {
            listOf("token", "log-in", "login", "auth", "credential").any(reason::contains) ->
                DebridException("The token is invalid or expired. Remove it and add a valid token in Settings.", true)
            listOf("vip", "premium", "subscription").any(reason::contains) ->
                DebridException("An active MegaDebrid Premium subscription is required.", true)
            "password" in reason -> DebridException("This file needs a password. Open the original host to continue.")
            listOf("quota", "traffic", "limit").any(reason::contains) ->
                DebridException("The account's download or traffic limit has been reached.")
            listOf("host", "maintenance").any(reason::contains) ->
                DebridException("This file host is unsupported or temporarily unavailable on MegaDebrid.")
            "unrestrict" in reason ->
                DebridException("MegaDebrid could not resolve this link. Try another host, or open the original link.")
            listOf("link", "file", "deleted", "not found").any(reason::contains) ->
                DebridException("The file is unavailable or the link could not be resolved.")
            else -> httpError(status)
        }
    }

    private fun httpError(status: Int) = when (status) {
        401 -> DebridException("The key or token is invalid or expired. Update it in Settings.", true)
        403 -> DebridException("The service denied access. Check your account and network.")
        429 -> DebridException("Too many requests. Wait a moment and try again.")
        in 500..599 -> DebridException("The service is temporarily unavailable. Try again later.")
        else -> DebridException("The service could not complete this request. Try again or open the original host.")
    }

    private fun invalidResponse() = DebridException("The service returned an invalid download response. Try again later.")

    private fun checkKey(key: String) {
        if (key.isBlank() || key.length > 4096 || key.any { it.code !in 33..126 }) {
            throw DebridException("Enter a valid key or token without spaces.", true)
        }
    }

    private fun webUrl(value: String): String? = value.toHttpUrlOrNull()
        ?.takeIf { it.username.isEmpty() && it.password.isEmpty() }
        ?.toString()

    private companion object { const val MAX_RESPONSE_BYTES = 512L * 1024 }
}
