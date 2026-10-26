package org.mobilism.material.data

import java.io.IOException
import java.util.concurrent.atomic.AtomicLong
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response

enum class DiagnosticSource { App, Forum, RealDebrid, MegaDebrid, MobilismPremium }
enum class DiagnosticOperation { Startup, Settings, Export, Login, Categories, Releases, Search, Comments, Reaction, Account, Hosts, Resolve, SessionRenewal, Other }
enum class DiagnosticPhase { Started, Completed, Failed, Retrying }
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
)

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
                path.endsWith("/category") -> DiagnosticOperation.Categories
                "/release" in path -> DiagnosticOperation.Releases
                "/post/" in path -> DiagnosticOperation.Reaction
                else -> DiagnosticOperation.Other
            }
        }
    }

    private companion object { val sequence = AtomicLong() }
}
