package org.mobilism.material.data

import android.content.Context
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.json.JSONArray
import org.json.JSONObject

enum class DebridProvider(val label: String) {
    RealDebrid("Real-Debrid"), MegaDebrid("MegaDebrid"),
}

data class SupportedHost(val domain: String, val name: String)

data class DebridAccount(
    val provider: DebridProvider,
    val configured: Boolean = false,
    val hosts: List<SupportedHost> = emptyList(),
    val busy: Boolean = false,
    val error: String? = null,
) {
    val hostNames: List<String>
        get() = hosts.map { it.name }.distinctBy { it.lowercase() }.sortedBy { it.lowercase() }
}

data class DebridState(val accounts: List<DebridAccount> = DebridProvider.entries.map { DebridAccount(it) }) {
    fun supporting(url: String): List<DebridAccount> = accounts.filter { account ->
        account.configured && account.hosts.any { HostMatcher.matches(url, it.domain) }
    }

    fun hostName(url: String): String {
        val host = accounts.asSequence().filter { it.configured }.flatMap { it.hosts.asSequence() }
            .firstOrNull { HostMatcher.matches(url, it.domain) }
        return host?.name ?: HostMatcher.displayName(url)
    }
}

object HostMatcher {
    fun key(value: String): String? = domain(value)?.let { host ->
        when (displayName(host)) {
            "Rapidgator" -> "rapidgator.net"
            "Uploadrar" -> "uploadrar.com"
            "UpFiles" -> "upfiles.com"
            "Filedot" -> "filedot.to"
            "ZincDrive" -> "zincdrive.com"
            else -> host
        }
    }

    fun domain(value: String): String? =
        (if (value.contains("://")) value else "https://$value").toHttpUrlOrNull()
            ?.host?.lowercase()?.trimEnd('.')?.removePrefix("www.")

    fun matches(url: String, supported: String): Boolean {
        val host = domain(url) ?: return false
        val expected = domain(supported) ?: return false
        return host == expected || host.endsWith(".$expected")
    }

    fun displayName(url: String): String {
        val host = domain(url) ?: return "Download"
        return when {
            matches(url, "rapidgator.net") || matches(url, "rg.to") -> "Rapidgator"
            matches(url, "mega.nz") -> "MEGA"
            matches(url, "mediafire.com") -> "MediaFire"
            matches(url, "1fichier.com") -> "1fichier"
            matches(url, "upfilesgo.com") || matches(url, "upfiles.com") -> "UpFiles"
            matches(url, "uploadrar.com") || matches(url, "uploadrar.net") -> "Uploadrar"
            matches(url, "filedot.to") || matches(url, "filedot.pw") -> "Filedot"
            matches(url, "zincdrive.com") || matches(url, "zdrive.to") -> "ZincDrive"
            matches(url, "devuploads.com") -> "DevUploads"
            matches(url, "send.now") -> "Send.now"
            matches(url, "sendit.cloud") -> "Sendit.cloud"
            matches(url, "katfile.com") -> "KatFile"
            matches(url, "send.cm") -> "Send"
            matches(url, "uploady.io") -> "Uploady"
            else -> host
        }
    }
}

/**
 * Keys stay encrypted locally. Highlighting intersects Mobilism's file-host directory
 * with the providers' published host catalogs;
 * it does not validate account entitlement or generate an unrestricted download.
 */
class DebridRepository(context: Context) {
    private val keys = DebridKeyStore(context)
    private val cache = context.getSharedPreferences("material-debrid-hosts", Context.MODE_PRIVATE)
    private val mutex = Mutex()
    private val mutableState = MutableStateFlow(DebridState())
    val state = mutableState.asStateFlow()
    private val client = OkHttpClient.Builder().connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS).callTimeout(30, TimeUnit.SECONDS)
        .followRedirects(false).followSslRedirects(false).build()

    suspend fun fileHosts(): List<SupportedHost> = withContext(Dispatchers.IO) {
        MobilismHosts.parse(get(MobilismHosts.URL)).map { domain ->
            SupportedHost(checkNotNull(HostMatcher.key(domain)), HostMatcher.displayName(domain))
        }.distinctBy { it.domain }.sortedBy { it.name.lowercase() }
    }

    suspend fun load() = withContext(Dispatchers.IO) {
        mutex.withLock {
            mutableState.value = DebridState(DebridProvider.entries.map { provider ->
                try {
                    val configured = !keys.read(provider).isNullOrBlank()
                    DebridAccount(provider, configured, if (configured) cachedHosts(provider) else emptyList())
                } catch (_: Exception) {
                    DebridAccount(provider, error = "The saved key could not be read. Enter it again.")
                }
            })
        }
        for (account in state.value.accounts.filter { it.configured }) {
            if (cache.getInt(account.provider.name + "-format", 0) != CACHE_FORMAT ||
                System.currentTimeMillis() - cache.getLong(account.provider.name + "-updated", 0) > CACHE_AGE) {
                refresh(account.provider)
            }
        }
    }

    suspend fun saveKey(provider: DebridProvider, value: String) = withContext(Dispatchers.IO) {
        require(value.isNotBlank() && value.length <= 4096) { "Enter a valid key." }
        mutex.withLock {
            keys.write(provider, value.trim())
            update(provider) { it.copy(configured = true, error = null) }
        }
        refresh(provider)
    }

    suspend fun removeKey(provider: DebridProvider) = withContext(Dispatchers.IO) {
        mutex.withLock {
            keys.remove(provider)
            cache.edit().remove(provider.name).remove(provider.name + "-updated")
                .remove(provider.name + "-format").apply()
            update(provider) { DebridAccount(provider) }
        }
    }

    suspend fun refresh(provider: DebridProvider) = withContext(Dispatchers.IO) {
        mutex.withLock {
            if (state.value.accounts.none { it.provider == provider && it.configured }) return@withLock
            update(provider) { it.copy(busy = true, error = null) }
            try {
                val mobilismDomains = MobilismHosts.parse(get(MobilismHosts.URL))
                val providerHosts = when (provider) {
                    DebridProvider.RealDebrid -> {
                        val names = JSONObject(get("https://api.real-debrid.com/rest/1.0/hosts"))
                        val domains = JSONArray(get("https://api.real-debrid.com/rest/1.0/hosts/domains"))
                        (0 until domains.length()).mapNotNull { index ->
                            val domain = HostMatcher.domain(domains.getString(index)) ?: return@mapNotNull null
                            SupportedHost(domain, names.optJSONObject(domain)?.optString("name")
                                ?.takeIf(String::isNotBlank) ?: HostMatcher.displayName(domain))
                        }
                    }
                    DebridProvider.MegaDebrid -> {
                        val response = JSONObject(get("https://www.mega-debrid.eu/api.php?action=getHostersList"))
                        check(response.optString("response_code") == "ok")
                        val hosters = response.getJSONArray("hosters")
                        buildList {
                            for (index in 0 until hosters.length()) {
                                val host = hosters.getJSONObject(index)
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
                check(providerHosts.isNotEmpty())
                val hosts = MobilismHosts.shortlist(mobilismDomains, providerHosts)
                cache.edit().putString(provider.name, JSONArray().apply {
                    hosts.forEach { put(JSONObject().put("domain", it.domain).put("name", it.name)) }
                }.toString()).putLong(provider.name + "-updated", System.currentTimeMillis())
                    .putInt(provider.name + "-format", CACHE_FORMAT).apply()
                update(provider) { it.copy(hosts = hosts, busy = false, error = null) }
            } catch (cancelled: CancellationException) {
                update(provider) { it.copy(busy = false) }
                throw cancelled
            } catch (failure: Exception) {
                // Only exception types are logged; requests and saved credentials are never logged.
                android.util.Log.w("DebridHosts", provider.name + ": " +
                    generateSequence<Throwable>(failure) { it.cause }
                        .take(4).joinToString(" / ") { it.javaClass.simpleName })
                update(provider) { it.copy(busy = false, error =
                    if (it.hosts.isEmpty()) "Host support could not be loaded. Try again."
                    else "Using the saved host list. Refresh could not complete."
                ) }
            }
        }
    }

    private fun get(url: String): String = client.newCall(Request.Builder().url(url)
        .header("User-Agent", "MobilismMaterial/0.1 (Android)")
        .build()).execute().use { response ->
        check(response.isSuccessful)
        val source = checkNotNull(response.body).source()
        source.request(MAX_CATALOG_BYTES + 1)
        check(source.buffer.size <= MAX_CATALOG_BYTES)
        source.readUtf8()
    }

    private fun cachedHosts(provider: DebridProvider): List<SupportedHost> = runCatching {
        if (cache.getInt(provider.name + "-format", 0) != CACHE_FORMAT) return@runCatching emptyList()
        val array = JSONArray(cache.getString(provider.name, "[]"))
        (0 until array.length()).map { array.getJSONObject(it) }
            .map { SupportedHost(it.getString("domain"), it.getString("name")) }
    }.getOrDefault(emptyList())

    private fun update(provider: DebridProvider, transform: (DebridAccount) -> DebridAccount) {
        mutableState.update { it.copy(accounts = it.accounts.map { account ->
            if (account.provider == provider) transform(account) else account
        }) }
    }

    private companion object {
        const val MAX_CATALOG_BYTES = 2L * 1024 * 1024
        const val CACHE_AGE = 24L * 60 * 60 * 1000
        const val CACHE_FORMAT = 2
    }
}
