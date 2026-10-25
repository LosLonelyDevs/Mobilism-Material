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
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.parser.Parser

internal class MobilismPremiumApi(client: OkHttpClient) {
    private val client = client.newBuilder().followRedirects(false).followSslRedirects(false)
        .callTimeout(60, TimeUnit.SECONDS).readTimeout(45, TimeUnit.SECONDS).build()

    fun credentials(username: String, password: String): String {
        if (username.isBlank() || password.isEmpty() || username.length > 256 || password.length > 4096 ||
            username.any { it.isISOControl() } || password.any { it.isISOControl() }) {
            throw DebridException("Enter your Mobilism username and Premium password.")
        }
        return JSONObject().put("username", username.trim()).put("password", password).toString()
    }

    fun validate(key: String) {
        val page = post(key)
        checkPage(page)
        if (page.selectFirst("form[name=transload] input[name=link]") == null ||
            page.selectFirst("input[name=premium_acc]") == null) throw invalidResponse()
    }

    fun hosts(): List<SupportedHost> {
        val xml = request(Request.Builder().url(HOSTS).build())
        val document = Jsoup.parse(xml, "", Parser.xmlParser())
        if (document.selectFirst("supported-filehosts") == null) throw invalidResponse()
        val hosts = document.select("supported-filehosts > host[url]").mapNotNull { element ->
            val value = element.attr("url").trim()
            val url = "https://$value".toHttpUrlOrNull() ?: return@mapNotNull null
            if (url.username.isNotEmpty() || url.password.isNotEmpty() || url.encodedPath != "/" ||
                url.query != null || url.fragment != null || url.port != 443 || '.' !in url.host) return@mapNotNull null
            val domain = HostMatcher.domain(value) ?: return@mapNotNull null
            SupportedHost(domain, HostMatcher.displayName(domain))
        }.distinctBy { it.domain }
        if (hosts.isEmpty()) throw invalidResponse()
        return Alphabetical.sorted(hosts) { it.name }
    }

    fun resolve(key: String, original: String): String {
        val source = webUrl(original) ?: throw DebridException("This download link is not a valid web address.")
        if (hosts().none { HostMatcher.matches(source, it.domain) }) {
            throw DebridException("This file host is not supported by Mobilism Premium.")
        }
        var page = post(key, mapOf("link" to source, "premium_acc" to "on"), source)
        val seen = mutableSetOf<Map<String, String>>()
        repeat(4) {
            checkPage(page)
            val fields = page.select("input[name]").associate { it.attr("name") to it.attr("value") }
            // The legacy resolver uses hidden forms before returning the final download anchor.
            val next = when {
                fields["step"] != null && fields["dllink"] != null ->
                    listOf("link", "cookie", "referer", "step", "dllink")
                !fields["link"].isNullOrBlank() && !fields["host"].isNullOrBlank() && !fields["path"].isNullOrBlank() ->
                    listOf("link", "cookie", "referer", "filename", "host", "port", "path")
                else -> emptyList()
            }
            if (next.isNotEmpty()) {
                val values = next.associateWith { name ->
                    val value = fields[name].orEmpty()
                    if (name in listOf("link", "referer") && Regex("(?i)^https?%3a").containsMatchIn(value)) {
                        runCatching { java.net.URLDecoder.decode(value, "UTF-8") }.getOrElse { throw invalidResponse() }
                    } else value
                }
                if (!seen.add(values)) throw invalidResponse()
                // Ignore the form action. Credentials only go to the fixed HTTPS resolver.
                page = post(key, values)
            } else {
                val secrets = decode(key)
                val links = page.select("a[href]").mapNotNull {
                    ENDPOINT.toHttpUrl().resolve(it.attr("href"))?.toString()?.let(::webUrl)
                }
                    .filter { value ->
                        val url = value.toHttpUrl()
                        value != source && url.host != "forum.mobilism.org" &&
                            !(url.host == "mblservices.org" && (url.encodedPath == ENDPOINT.toHttpUrl().encodedPath ||
                                url.encodedPath.contains("/login"))) &&
                            listOf("username", "password").none { name -> url.queryParameter(name) != null } &&
                            !value.contains(secrets.getString("password")) &&
                            runCatching { java.net.URLDecoder.decode(value, "UTF-8") }.getOrNull()
                                ?.contains(secrets.getString("password")) == false
                    }.distinct()
                if (links.size == 1) return links.single()
                throw invalidResponse()
            }
        }
        throw DebridException("Mobilism Premium needs more steps to resolve this link. Try another host or open the original link.")
    }

    private fun post(key: String, fields: Map<String, String> = emptyMap(), link: String? = null): Document {
        val credentials = decode(key)
        val url = ENDPOINT.toHttpUrl().newBuilder().apply { link?.let { addQueryParameter("dl", it) } }.build()
        val body = FormBody.Builder().apply {
            fields.forEach { (name, value) -> add(name, value) }
            add("username", credentials.getString("username"))
            add("password", credentials.getString("password"))
            add("mobile_app", "true")
        }.build()
        return Jsoup.parse(request(Request.Builder().url(url).post(body).build()), ENDPOINT)
    }

    private fun decode(key: String): JSONObject = try {
        JSONObject(key).also { credentials(it.getString("username"), it.getString("password")) }
    } catch (_: Exception) {
        throw DebridException("Sign in to Mobilism Premium again.", true)
    }

    private fun checkPage(page: Document) {
        val text = page.text().lowercase()
        when {
            page.selectFirst("input[type=password]") != null ||
                listOf("incorrect password", "invalid password", "wrong password", "incorrect username", "invalid login").any(text::contains) ->
                throw DebridException("The Mobilism Premium username or password was rejected. Use your Premium password, which can differ from your forum password.", true)
            listOf("subscription expired", "premium account expired", "no active subscription").any(text::contains) ->
                throw DebridException("An active Mobilism Premium subscription is required.", true)
            listOf("limit reached", "daily limit", "traffic limit").any(text::contains) ->
                throw DebridException("The Mobilism Premium download limit has been reached. Try again later.")
            listOf("file not found", "file was deleted", "file has been deleted").any(text::contains) ->
                throw DebridException("The file is unavailable on this host. Try another mirror.")
            // Successful responses also use htmlerror, with a download anchor inside the bold text.
            page.select("span > b").any { it.text().isNotBlank() && it.selectFirst("a[download][href]") == null } ->
                throw DebridException("Mobilism Premium could not resolve this link. Try another host or open the original link.")
        }
    }

    private fun request(request: Request): String = try {
        client.newCall(request.newBuilder().header("User-Agent", "MobilismMaterial/0.1 (Android)").build())
            .execute().use { response ->
                if (!response.isSuccessful) throw DebridException(when (response.code) {
                    401 -> "Mobilism Premium rejected the account. Sign in again."
                    403 -> "Mobilism Premium denied access. Check your account or try again later."
                    429 -> "Too many requests. Wait a moment and try again."
                    else -> "Mobilism Premium is unavailable. Try again later."
                }, response.code == 401)
                val source = response.body?.source() ?: throw invalidResponse()
                source.request(MAX_BYTES + 1)
                if (source.buffer.size > MAX_BYTES) throw invalidResponse()
                source.readUtf8()
            }
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (failure: DebridException) {
        throw failure
    } catch (_: IOException) {
        throw DebridException("Could not reach Mobilism Premium. Check your connection and try again.")
    } catch (_: Exception) {
        throw invalidResponse()
    }

    private fun webUrl(value: String): String? = value.toHttpUrlOrNull()
        ?.takeIf { it.username.isEmpty() && it.password.isEmpty() }?.toString()

    private fun invalidResponse() = DebridException("Mobilism Premium returned an unrecognized response. Try another host or open the original link.")

    private companion object {
        const val ENDPOINT = "https://mblservices.org/amember/downloader/downloader/app/index_mob_app_secure.php"
        const val HOSTS = "https://forum.mobilism.org/filehosts.xml"
        const val MAX_BYTES = 512L * 1024
    }
}
