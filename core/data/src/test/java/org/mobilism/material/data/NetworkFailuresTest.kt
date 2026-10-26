package org.mobilism.material.data

import java.io.IOException
import java.io.InterruptedIOException
import java.net.UnknownHostException
import javax.net.ssl.SSLHandshakeException
import org.junit.Assert.*
import org.junit.Test

class NetworkFailuresTest {
    @Test fun distinguishesChallengeServerOutageAndMalformedHtml() {
        assertEquals(FailureKind.Challenge, NetworkFailures.response(403, true, "private-body")!!.kind)
        assertEquals(FailureKind.Challenge, NetworkFailures.response(403, false, "<html>challenge-platform</html>")!!.kind)
        assertEquals(FailureKind.Server, NetworkFailures.response(522, false, "<html>Origin down</html>")!!.kind)
        assertEquals(FailureKind.Server, NetworkFailures.response(503, false, "")!!.kind)
        assertEquals(FailureKind.Server, NetworkFailures.response(429, false, "")!!.kind)
        assertEquals(FailureKind.InvalidResponse, NetworkFailures.response(200, false, "<html>Other page</html>")!!.kind)
        assertNull(NetworkFailures.response(400, false, "{\"error\":{}}"))
        assertNull(NetworkFailures.response(429, false, "{\"error\":{\"text\":\"Please wait before posting again.\"}}"))
        assertFalse(NetworkFailures.response(403, true, "private-body")!!.message.contains("private"))
    }

    @Test fun classifiesDnsPermissionTlsAndCallTimeoutWithoutExceptionText() {
        for ((error, expected) in listOf(
            UnknownHostException("private-host") to DiagnosticFailure.Dns,
            UnknownHostException("android_getaddrinfo failed: EPERM private-host") to DiagnosticFailure.Permission,
            SSLHandshakeException("private-certificate") to DiagnosticFailure.Tls,
            InterruptedIOException("private-call timeout") to DiagnosticFailure.Timeout,
            IOException("private-address") to DiagnosticFailure.Network,
        )) {
            assertEquals(expected, NetworkFailures.category(error))
            assertFalse(NetworkFailures.forScreen(error).message.contains("private-"))
        }
    }
}
