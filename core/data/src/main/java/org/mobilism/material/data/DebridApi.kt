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

class DebridException(message: String, val invalidatesAccount: Boolean = false,
    val sessionExpired: Boolean = false, val fileUnavailable: Boolean = false) : Exception(message)

internal class DebridApi(client: OkHttpClient,
    private val diagnostics: DiagnosticLogger = DiagnosticLogger.None, private val message: (Int) -> String) {
    private val client = client.newBuilder()
        .connectTimeout(15, TimeUnit.SECONDS).readTimeout(30, TimeUnit.SECONDS)
        .callTimeout(45, TimeUnit.SECONDS)
        // MegaDebrid requires its token in the query. Never forward it through redirects.
        .followRedirects(false).followSslRedirects(false).build()
    private val mobilism = MobilismPremiumApi(this.client, message)

    fun connectMobilism(username: String, password: String): String =
        mobilism.credentials(username, password).also(mobilism::validate)

    fun connectMegaDebrid(username: String, password: String): MegaDebridSession {
        if (username.isBlank() || password.isEmpty() || username.length > 256 || password.length > 4096 ||
            username.any { it.isISOControl() } || password.any { it.isISOControl() }) {
            throw DebridException(message(R.string.mega_credentials_required))
        }
        val url = "https://www.mega-debrid.eu/api.php".toHttpUrl().newBuilder()
            .addQueryParameter("action", "connectUser")
            .addQueryParameter("login", username.trim())
            .addQueryParameter("password", password).build()
        val result = request(DebridProvider.MegaDebrid, Request.Builder().url(url).build())
        val token = result.optString("token")
        if (token.isBlank()) throw invalidResponse()
        checkKey(token)
        val premiumUntil = result.optLong("vip_end")
        if (premiumUntil <= System.currentTimeMillis() / 1000) {
            throw DebridException(message(R.string.mega_subscription_required), true)
        }
        return MegaDebridSession(token, premiumUntil)
    }

    fun validate(provider: DebridProvider, key: String) {
        if (provider == DebridProvider.Mobilism) return mobilism.validate(key)
        checkKey(key)
        when (provider) {
            DebridProvider.Mobilism -> error("Handled above")
            DebridProvider.RealDebrid -> {
                val user = request(provider, realRequest("user", key).build())
                if (user.optLong("id") <= 0 || !user.has("type")) throw invalidResponse()
                if (user.optString("type") != "premium" || user.optLong("premium") <= 0) {
                    throw DebridException(message(R.string.real_subscription_required), true)
                }
            }
            DebridProvider.MegaDebrid -> {
                // History also accepts application keys, so Premium proof must come from connectUser.
                val url = megaUrl("getUserHistory", key).newBuilder().addQueryParameter("limit", "1").build()
                val result = request(provider, Request.Builder().url(url).build())
                if (!result.has("history")) throw invalidResponse()
            }
        }
    }

    fun availableHosts(provider: DebridProvider, key: String, catalogDomains: List<String> = emptyList()): List<SupportedHost> {
        if (provider == DebridProvider.Mobilism) return mobilism.hosts()
        checkKey(key)
        return when (provider) {
            DebridProvider.Mobilism -> error("Handled above")
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
                buildList<SupportedHost> {
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
        if (provider == DebridProvider.Mobilism) return mobilism.resolve(key, original)
        checkKey(key)
        val source = webUrl(original) ?: throw DebridException(message(R.string.debrid_invalid_link))
        val body = FormBody.Builder().add("link", source).build()
        val request = when (provider) {
            DebridProvider.Mobilism -> error("Handled above")
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
            return client.newCall(request).execute().use { response ->
                if (response.code in 300..399) throw DebridException(message(R.string.debrid_redirect))
                val source = response.body?.source() ?: throw invalidResponse()
                source.request(MAX_RESPONSE_BYTES + 1)
                if (source.buffer.size > MAX_RESPONSE_BYTES) throw invalidResponse()
                val json = runCatching { JSONObject(source.readUtf8()) }.getOrNull()
                when (provider) {
                    DebridProvider.Mobilism -> error("Mobilism uses its HTML resolver")
                    DebridProvider.RealDebrid -> {
                        if (!response.isSuccessful || json?.has("error") == true || json?.has("error_code") == true) {
                            diagnostics.record(DiagnosticEvent(DiagnosticSource.RealDebrid, DiagnosticOperation.Other,
                                DiagnosticPhase.Failed, httpStatus = response.code, failure = DiagnosticFailure.ProviderRejected))
                            throw realError(response.code, json?.optInt("error_code", -1) ?: -1)
                        }
                    }
                    DebridProvider.MegaDebrid -> {
                        if (!response.isSuccessful || json?.optString("response_code") != "ok") {
                            val failure = megaError(response.code, json)
                            diagnostics.record(DiagnosticEvent(DiagnosticSource.MegaDebrid, DiagnosticOperation.Other,
                                DiagnosticPhase.Failed, httpStatus = response.code,
                                failure = when {
                                    failure.sessionExpired -> DiagnosticFailure.SessionExpired
                                    json?.optString("response_code")?.startsWith("UNRESTRICTING_") == true -> DiagnosticFailure.Unrestricting
                                    else -> DiagnosticFailure.ProviderRejected
                                }))
                            throw failure
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
            throw DebridException(message(R.string.debrid_unreachable))
        } catch (_: Exception) {
            // Provider responses and exception messages can contain credentials or private URLs.
            throw invalidResponse()
        }
    }

    private fun realError(status: Int, code: Int): DebridException = when (code) {
        8, 12, 13 -> DebridException(message(R.string.real_invalid_key), true)
        9, 14, 15 -> DebridException(message(R.string.real_permission), true)
        10, 11 -> DebridException(message(R.string.real_two_factor))
        5 -> DebridException(message(R.string.debrid_rate_limit))
        6, 7, 24 -> DebridException(message(R.string.real_file_unavailable), fileUnavailable = code == 7 || code == 24)
        16 -> DebridException(message(R.string.real_unsupported_host))
        17, 19 -> DebridException(message(R.string.real_host_unavailable))
        18, 21, 23 -> DebridException(message(R.string.debrid_account_limit))
        20 -> DebridException(message(R.string.real_subscription_required), true)
        22 -> DebridException(message(R.string.real_network_denied))
        else -> httpError(status)
    }

    private fun megaError(status: Int, response: JSONObject?): DebridException {
        // Use provider text only for classification; never display it or log it.
        val reason = (response?.optString("response_code").orEmpty() + " " +
            response?.optString("response_text").orEmpty()).lowercase()
        return when {
            "unknown_user" in reason ->
                DebridException(message(R.string.mega_credentials_rejected), true)
            response?.optString("response_code").orEmpty().contains("token", ignoreCase = true) ||
                "token error" in reason || "invalid token" in reason || "expired token" in reason ->
                DebridException(message(R.string.mega_session_expired), true, sessionExpired = true)
            listOf("vip", "premium", "subscription").any(reason::contains) ->
                DebridException(message(R.string.mega_subscription_required), true)
            "password" in reason -> DebridException(message(R.string.debrid_file_password))
            listOf("quota", "traffic", "limit").any(reason::contains) ->
                DebridException(message(R.string.debrid_account_limit))
            listOf("file not found", "file deleted", "file was deleted", "file has been deleted", "link deleted",
                "link not found").any(reason.replace('_', ' ')::contains) ->
                DebridException(message(R.string.mega_file_unavailable), fileUnavailable = true)
            listOf("host", "maintenance").any(reason::contains) ->
                DebridException(message(R.string.mega_host_unavailable))
            "unrestrict" in reason ->
                DebridException(message(R.string.mega_unresolved_link))
            listOf("link", "file", "deleted", "not found").any(reason::contains) ->
                DebridException(message(R.string.mega_file_unavailable))
            status == 401 -> DebridException(message(R.string.mega_session_expired), true, sessionExpired = true)
            else -> httpError(status)
        }
    }

    private fun httpError(status: Int) = when (status) {
        401 -> DebridException(message(R.string.debrid_invalid_token), true)
        403 -> DebridException(message(R.string.debrid_access_denied))
        429 -> DebridException(message(R.string.debrid_rate_limit))
        in 500..599 -> DebridException(message(R.string.debrid_service_unavailable))
        else -> DebridException(message(R.string.debrid_request_failed))
    }

    private fun invalidResponse() = DebridException(message(R.string.debrid_invalid_response))

    private fun checkKey(key: String) {
        if (key.isBlank() || key.length > 4096 || key.any { it.code !in 33..126 }) {
            throw DebridException(message(R.string.debrid_key_format), true)
        }
    }

    private fun webUrl(value: String): String? = value.toHttpUrlOrNull()
        ?.takeIf { it.username.isEmpty() && it.password.isEmpty() }
        ?.toString()

    private companion object { const val MAX_RESPONSE_BYTES = 512L * 1024 }
}

internal class MegaDebridSession(val token: String, val premiumUntil: Long)
