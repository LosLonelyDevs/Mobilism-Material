package org.mobilism.material.data

import java.io.IOException
import java.util.concurrent.atomic.AtomicLong
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response

enum class DiagnosticSource { App, Forum, RealDebrid, MegaDebrid, MobilismPremium, Vercel }
enum class DiagnosticOperation { Startup, Settings, Export, Login, Categories, Releases, Search, Comments, Messages, Reaction, Account, Hosts, Resolve, SessionRenewal, NoticeDisplay, DeviceNotification, FollowingCheck, CacheLookup, CacheContribution, Other }
enum class DiagnosticPhase { Started, Completed, Failed, Retrying, Rendered, Posted, Dismissed }
enum class DiagnosticFailure { Timeout, Network, Cancelled, ProviderRejected, Unrestricting, Unexpected,
    Dns, Tls, Permission, Challenge, Server, InvalidResponse, SessionExpired, Credentials }

data class DiagnosticEvent(
    val source: DiagnosticSource,
    val operation: DiagnosticOperation,
    val phase: DiagnosticPhase,
    val requestId: Long? = null,
    val httpStatus: Int? = null,
    val elapsedMillis: Long? = null,
    val failure: DiagnosticFailure? = null,
    val noticeKey: String? = null,
    val cache: CacheDiagnostic? = null,
)

enum class AppCache { Following, MemberIdentity, Search, Read, ReleaseFeed, Inbox, Images, ReleaseComments }
enum class CacheOutcome { Available, MemoryHit, DiskHit, Miss, Stale, Unconfigured, Disabled, Failed, Published, Empty, Cooldown }

data class CacheDiagnostic(val name: AppCache, val outcome: CacheOutcome, val hits: Int = 0, val misses: Int = 0,
    val items: Int? = null, val background: Boolean? = null, val fallbackSearches: Int? = null)

suspend fun DiagnosticLogger.cacheLookup(cache: AppCache, outcome: CacheOutcome) {
    val miss = outcome == CacheOutcome.Miss
    recordCache(CacheDiagnostic(cache, outcome, hits = if (miss) 0 else 1, misses = if (miss) 1 else 0))
}

suspend fun DiagnosticLogger.recordCache(detail: CacheDiagnostic, operation: DiagnosticOperation = DiagnosticOperation.CacheLookup,
    httpStatus: Int? = null) {
    if (this === DiagnosticLogger.None) return
    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        val source = if (detail.name == AppCache.Following) DiagnosticSource.Vercel else DiagnosticSource.App
        val phase = if (detail.outcome == CacheOutcome.Failed) DiagnosticPhase.Failed else DiagnosticPhase.Completed
        // Diagnostics are best effort, including when local storage is full.
        runCatching { record(DiagnosticEvent(source, operation, phase, httpStatus = httpStatus, cache = detail)) }
    }
}

fun interface DiagnosticLogger {
    fun record(event: DiagnosticEvent)

    companion object { val None = DiagnosticLogger {} }
}

internal class DiagnosticInterceptor(private val logger: DiagnosticLogger) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val source = when (request.url.host) {
            "api.real-debrid.com" -> DiagnosticSource.RealDebrid
            "www.mega-debrid.eu" -> DiagnosticSource.MegaDebrid
            "mblservices.org" -> DiagnosticSource.MobilismPremium
            else -> DiagnosticSource.Forum
        }
        val event = DiagnosticEvent(source, operation(request, source), DiagnosticPhase.Started,
            requestId = sequence.incrementAndGet())
        logger.record(event)
        val start = System.nanoTime()
        try {
            return chain.proceed(request).also { response ->
                logger.record(event.copy(phase = DiagnosticPhase.Completed, httpStatus = response.code,
                    elapsedMillis = (System.nanoTime() - start) / 1_000_000))
            }
        } catch (failure: IOException) {
            logger.record(event.copy(phase = DiagnosticPhase.Failed,
                elapsedMillis = (System.nanoTime() - start) / 1_000_000,
                failure = when {
                    chain.call().isCanceled() -> DiagnosticFailure.Cancelled
                    else -> NetworkFailures.category(failure)
                }))
            throw failure
        }
    }

    private fun operation(request: Request, source: DiagnosticSource): DiagnosticOperation {
        val path = request.url.encodedPath
        return when (source) {
            DiagnosticSource.MobilismPremium -> DiagnosticOperation.Resolve
            DiagnosticSource.RealDebrid -> when {
                path.endsWith("/user") -> DiagnosticOperation.Account
                "/hosts" in path -> DiagnosticOperation.Hosts
                path.endsWith("/unrestrict/link") -> DiagnosticOperation.Resolve
                else -> DiagnosticOperation.Other
            }
            DiagnosticSource.MegaDebrid -> when (request.url.queryParameter("action")) {
                "connectUser" -> DiagnosticOperation.Login
                "getUserHistory" -> DiagnosticOperation.Account
                "getHostersList" -> DiagnosticOperation.Hosts
                "getLink" -> DiagnosticOperation.Resolve
                else -> DiagnosticOperation.Other
            }
            else -> when {
                "/auth/" in path -> DiagnosticOperation.Login
                path.endsWith("/search") -> DiagnosticOperation.Search
                "/comments/" in path || path.endsWith("/comment") -> DiagnosticOperation.Comments
                "/message/" in path -> DiagnosticOperation.Messages
                path.endsWith("/category") -> DiagnosticOperation.Categories
                "/release" in path -> DiagnosticOperation.Releases
                "/post/" in path -> DiagnosticOperation.Reaction
                else -> DiagnosticOperation.Other
            }
        }
    }

    private companion object { val sequence = AtomicLong() }
}
