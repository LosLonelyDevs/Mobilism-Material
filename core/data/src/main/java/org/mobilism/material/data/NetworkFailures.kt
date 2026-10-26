package org.mobilism.material.data

import java.io.IOException
import java.io.InterruptedIOException
import java.net.UnknownHostException
import javax.net.ssl.SSLException

internal object NetworkFailures {
    fun category(failure: IOException): DiagnosticFailure {
        val causes = generateSequence<Throwable>(failure) { it.cause }.take(8).toList()
        return when {
            causes.any { it is SecurityException || it.message.orEmpty().let { text ->
                "EPERM" in text || "EACCES" in text || "Permission denied" in text
            } } -> DiagnosticFailure.Permission
            causes.any { it is UnknownHostException } -> DiagnosticFailure.Dns
            causes.any { it is SSLException } -> DiagnosticFailure.Tls
            causes.any { it is InterruptedIOException } -> DiagnosticFailure.Timeout
            else -> DiagnosticFailure.Network
        }
    }

    fun forScreen(failure: IOException): ApiFailure = when (category(failure)) {
        DiagnosticFailure.Permission -> ApiFailure(FailureKind.Permission, "Network access is blocked. Check Mobilism's app permissions.")
        DiagnosticFailure.Dns -> ApiFailure(FailureKind.Dns, "Mobilism's server address could not be resolved. Check Private DNS, VPN, and your connection.")
        DiagnosticFailure.Tls -> ApiFailure(FailureKind.Connection, "A secure connection to Mobilism could not be verified. Check your device date, VPN, or network filtering.")
        DiagnosticFailure.Timeout -> ApiFailure(FailureKind.Connection, "Mobilism took too long to respond. Try again or switch networks.")
        else -> ApiFailure(FailureKind.Connection, "Could not reach Mobilism. Check your connection or switch networks, then try again.")
    }

    fun response(status: Int, challenged: Boolean, body: String): ApiFailure? {
        val html = body.trimStart().startsWith('<')
        return when {
            challenged || html && (body.contains("cf-chl-", true) || body.contains("challenge-platform", true)) ->
                ApiFailure(FailureKind.Challenge, "Cloudflare is asking this connection for browser verification. Try another network or use the forum in your browser.", status)
            status in 500..599 -> ApiFailure(FailureKind.Server, "Mobilism is temporarily unavailable (HTTP $status). Try again later.", status)
            status == 429 && !body.trimStart().startsWith('{') ->
                ApiFailure(FailureKind.Server, "Mobilism is limiting requests. Wait a moment and try again.", status)
            status in 300..399 -> ApiFailure(FailureKind.Server, "Mobilism redirected the app request. Try again later.", status)
            html -> ApiFailure(FailureKind.InvalidResponse, "Mobilism returned a web page instead of app data (HTTP $status). Try again later.", status)
            else -> null
        }
    }
}
