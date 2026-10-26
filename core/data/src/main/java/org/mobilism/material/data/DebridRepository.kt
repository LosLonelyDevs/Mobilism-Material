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
    RealDebrid("Real-Debrid"), MegaDebrid("MegaDebrid"), Mobilism("Mobilism Premium"),
}

data class SupportedHost(val domain: String, val name: String)

data class DebridAccount(
    val provider: DebridProvider,
    val configured: Boolean = false,
    val hosts: List<SupportedHost> = emptyList(),
    val busy: Boolean = false,
    val error: String? = null,
    val validated: Boolean = false,
    val hostsVerified: Boolean = false,
) {
    val hostNames: List<String>
        get() = Alphabetical.sorted(hosts.map { it.name }.distinctBy { it.lowercase(java.util.Locale.ROOT) }) { it }
}

data class DebridState(val accounts: List<DebridAccount> = DebridProvider.entries.map { DebridAccount(it) },
    val enabled: Boolean = true) {
    fun supporting(url: String): List<DebridAccount> = accounts.filter { account ->
        enabled && account.configured && account.validated && account.hostsVerified &&
            account.hosts.any { HostMatcher.matches(url, it.domain) }
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

class DebridRepository(context: Context, client: OkHttpClient = OkHttpClient(),
    private val isEnabled: () -> Boolean = { true },
    private val diagnostics: DiagnosticLogger = DiagnosticLogger.None) {
    private val keys = DebridKeyStore(context)
    private val cache = context.getSharedPreferences("material-debrid-hosts", Context.MODE_PRIVATE)
    private val mutex = Mutex()
    private val mutableState = MutableStateFlow(DebridState(enabled = isEnabled()))
    val state = mutableState.asStateFlow()
    private val client = client.newBuilder().connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS).callTimeout(30, TimeUnit.SECONDS)
        .followRedirects(false).followSslRedirects(false)
        .addInterceptor { chain ->
            if (!isEnabled()) throw java.io.IOException("Premium services disabled")
            chain.proceed(chain.request())
        }.addInterceptor(DiagnosticInterceptor(diagnostics)).build()
    private val api = DebridApi(this.client, diagnostics)
    private val megaSessions = MegaDebridSessions(api,
        read = { keys.read(DebridProvider.MegaDebrid) },
        save = { value -> requireEnabled(); keys.write(DebridProvider.MegaDebrid, value) },
        legacyPremiumUntil = { cache.getLong("mega-premium-until", 0) }, diagnostics = diagnostics)

    fun deactivate() {
        client.dispatcher.cancelAll()
        mutableState.value = DebridState(enabled = false)
    }

    private fun requireEnabled() {
        if (!isEnabled()) throw DebridException("Experimental premium services are disabled.")
    }

    suspend fun fileHosts(): List<SupportedHost> = withContext(Dispatchers.IO) {
        Alphabetical.sorted(MobilismHosts.domains.map { domain ->
            SupportedHost(checkNotNull(HostMatcher.key(domain)), HostMatcher.displayName(domain))
        }.distinctBy { it.domain }) { it.name }
    }

    suspend fun load() = withContext(Dispatchers.IO) {
        if (!isEnabled()) {
            deactivate()
            return@withContext
        }
        mutex.withLock {
            mutableState.value = DebridState(DebridProvider.entries.map { provider ->
                try {
                    val configured = !keys.read(provider).isNullOrBlank()
                    DebridAccount(provider, configured, if (configured) cachedHosts(provider) else emptyList())
                } catch (_: Exception) {
                    DebridAccount(provider, error = "The saved key could not be read. Enter it again.")
                }
            }, enabled = isEnabled())
        }
        for (account in state.value.accounts.filter { it.configured }) {
            refresh(account.provider)
        }
    }

    suspend fun saveKey(provider: DebridProvider, value: String) = withContext(Dispatchers.IO) {
        mutex.withLock {
            requireEnabled()
            if (provider != DebridProvider.RealDebrid) {
                throw DebridException("Sign in to ${provider.label} to connect your Premium account.", true)
            }
            update(provider) { it.copy(busy = true, error = null) }
            try {
                api.validate(provider, value.trim())
                requireEnabled()
                keys.write(provider, value.trim())
                update(provider) { it.copy(configured = true, validated = true, hostsVerified = false, error = null) }
            } finally {
                update(provider) { it.copy(busy = false) }
            }
        }
        refreshHosts(provider, validate = false)
    }

    suspend fun connectMegaDebrid(username: String, password: String) = withContext(Dispatchers.IO) {
        val provider = DebridProvider.MegaDebrid
        mutex.withLock {
            requireEnabled()
            update(provider) { it.copy(busy = true, error = null) }
            try {
                megaSessions.connect(username, password)
                update(provider) { it.copy(configured = true, validated = true, hostsVerified = false, error = null) }
            } finally {
                update(provider) { it.copy(busy = false) }
            }
        }
        refreshHosts(provider, validate = false)
    }

    suspend fun removeKey(provider: DebridProvider) = withContext(Dispatchers.IO) {
        mutex.withLock {
            keys.remove(provider)
            if (provider == DebridProvider.MegaDebrid) cache.edit().remove("mega-premium-until").apply()
            cache.edit().remove(provider.name).remove(provider.name + "-updated")
                .remove(provider.name + "-format").apply()
            update(provider) { DebridAccount(provider) }
        }
    }

    suspend fun connectMobilism(username: String, password: String) = withContext(Dispatchers.IO) {
        val provider = DebridProvider.Mobilism
        mutex.withLock {
            requireEnabled()
            update(provider) { it.copy(busy = true, error = null) }
            try {
                val credentials = api.connectMobilism(username, password)
                requireEnabled()
                keys.write(provider, credentials)
                update(provider) { it.copy(configured = true, validated = true, hostsVerified = false, error = null) }
            } finally {
                update(provider) { it.copy(busy = false) }
            }
        }
        refreshHosts(provider, validate = false)
    }

    suspend fun refresh(provider: DebridProvider) = refreshHosts(provider, validate = true)

    private suspend fun refreshHosts(provider: DebridProvider, validate: Boolean) = withContext(Dispatchers.IO) {
        mutex.withLock {
            if (!isEnabled()) return@withLock
            if (state.value.accounts.none { it.provider == provider && it.configured }) return@withLock
            update(provider) { it.copy(busy = true, hostsVerified = false, error = null) }
            try {
                if (validate) {
                    update(provider) { it.copy(validated = false) }
                    withAccount(provider) { key -> api.validate(provider, key) }
                    update(provider) { it.copy(validated = true) }
                }
                val catalogDomains = if (provider == DebridProvider.RealDebrid) {
                    val domains = JSONArray(get("https://api.real-debrid.com/rest/1.0/hosts/domains"))
                    (0 until domains.length()).map { domains.getString(it) }
                } else emptyList()
                val providerHosts = withAccount(provider) { key -> api.availableHosts(provider, key, catalogDomains) }
                requireEnabled()
                val hosts = Alphabetical.sorted(if (provider == DebridProvider.Mobilism) providerHosts
                    else MobilismHosts.shortlist(MobilismHosts.domains, providerHosts)) { it.name }
                cache.edit().putString(provider.name, JSONArray().apply {
                    hosts.forEach { put(JSONObject().put("domain", it.domain).put("name", it.name)) }
                }.toString()).putLong(provider.name + "-updated", System.currentTimeMillis())
                    .putInt(provider.name + "-format", CACHE_FORMAT).apply()
                update(provider) { it.copy(hosts = hosts, hostsVerified = true, busy = false, error = null) }
            } catch (cancelled: CancellationException) {
                update(provider) { it.copy(busy = false) }
                throw cancelled
            } catch (failure: DebridException) {
                update(provider) { it.copy(busy = false,
                    validated = it.validated && !failure.invalidatesAccount, error = failure.message) }
            } catch (failure: Exception) {
                diagnostics.record(DiagnosticEvent(
                    when (provider) {
                        DebridProvider.RealDebrid -> DiagnosticSource.RealDebrid
                        DebridProvider.MegaDebrid -> DiagnosticSource.MegaDebrid
                        DebridProvider.Mobilism -> DiagnosticSource.MobilismPremium
                    },
                    DiagnosticOperation.Hosts, DiagnosticPhase.Failed, failure = DiagnosticFailure.Unexpected))
                update(provider) { it.copy(busy = false, error =
                    if (it.hosts.isEmpty()) "Host support could not be loaded. Try again."
                    else "Host refresh failed. Saved hosts will stay inactive until a refresh succeeds."
                ) }
            }
        }
    }

    suspend fun resolve(url: String): String = withContext(Dispatchers.IO) {
        requireEnabled()
        val candidates = mutex.withLock {
            state.value.supporting(url).map { it.provider }
        }
        if (candidates.isEmpty()) throw DebridException("No verified account supports this host. Check Premium accounts in Settings.")
        val failures = mutableListOf<String>()
        for (provider in candidates) {
            requireEnabled()
            mutex.withLock {
                if (state.value.supporting(url).none { it.provider == provider }) return@withLock
                try {
                    val resolved = withAccount(provider) { key -> api.resolve(provider, key, url) }
                    requireEnabled()
                    return@withContext resolved
                } catch (failure: DebridException) {
                    requireEnabled()
                    if (failure.invalidatesAccount) update(provider) {
                        it.copy(validated = false, error = failure.message)
                    }
                    failures += "${provider.label}: ${failure.message}"
                }
            }
        }
        throw DebridException(failures.joinToString("\n\n").ifEmpty { "The saved account changed. Try again." })
    }

    private fun <T> withAccount(provider: DebridProvider, block: (String) -> T): T =
        if (provider == DebridProvider.MegaDebrid) megaSessions.use(block)
        else block(keys.read(provider).orEmpty())

    private fun get(url: String): String = client.newCall(Request.Builder().url(url)
        .header("User-Agent", "MobilismMaterial/0.1 (Android)")
        .build()).execute().use { response ->
        if (!response.isSuccessful) throw DebridException("Real-Debrid host list returned HTTP ${response.code}. Try again later.")
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
        if (!isEnabled()) {
            mutableState.value = DebridState(enabled = false)
            return
        }
        mutableState.update { it.copy(enabled = true, accounts = it.accounts.map { account ->
            if (account.provider == provider) transform(account) else account
        }) }
    }

    private companion object {
        const val MAX_CATALOG_BYTES = 2L * 1024 * 1024
        const val CACHE_FORMAT = 3
    }
}
