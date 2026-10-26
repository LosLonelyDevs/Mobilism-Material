package org.mobilism.material.data

import org.json.JSONObject

/** Accessed under the repository mutex; credentials only enter the encrypted key store. */
internal class MegaDebridSessions(
    private val api: DebridApi,
    private val read: () -> String?,
    private val save: (String) -> Unit,
    private val legacyPremiumUntil: () -> Long,
    private val diagnostics: DiagnosticLogger = DiagnosticLogger.None,
) {
    fun connect(username: String, password: String) {
        val session = api.connectMegaDebrid(username, password)
        save(account(username.trim(), password, session).toString())
    }

    fun <T> use(block: (String) -> T): T {
        val saved = read().orEmpty()
        val account = if (saved.startsWith('{')) JSONObject(saved) else null
        if (account?.optBoolean("renewalBlocked") == true) throw DebridException(
            "Sign in to MegaDebrid again to check your credentials and Premium access.", true,
        )
        var token = account?.getString("token") ?: saved
        val until = account?.getLong("premiumUntil") ?: legacyPremiumUntil()
        var renewed = false
        if (until <= System.currentTimeMillis() / 1000) {
            record(DiagnosticPhase.Retrying, DiagnosticFailure.SessionExpired)
            token = renew(account)
            renewed = true
        }
        return try {
            block(token)
        } catch (failure: DebridException) {
            if (!failure.sessionExpired || renewed) throw failure
            record(DiagnosticPhase.Retrying, DiagnosticFailure.SessionExpired)
            block(renew(account))
        }
    }

    private fun renew(saved: JSONObject?): String {
        if (saved == null) {
            record(DiagnosticPhase.Failed, DiagnosticFailure.Credentials)
            throw DebridException(
                "Sign in to MegaDebrid once to enable automatic session renewal.", true,
            )
        }
        val username = saved.getString("username")
        val password = saved.getString("password")
        record(DiagnosticPhase.Started)
        return try {
            val session = api.connectMegaDebrid(username, password)
            save(account(username, password, session).toString())
            record(DiagnosticPhase.Completed)
            session.token
        } catch (failure: DebridException) {
            // Repeated bad logins can temporarily block the user's IP at MegaDebrid.
            if (failure.invalidatesAccount && !failure.sessionExpired) {
                save(saved.put("renewalBlocked", true).toString())
            }
            record(DiagnosticPhase.Failed, if (failure.invalidatesAccount) DiagnosticFailure.ProviderRejected else DiagnosticFailure.Network)
            throw failure
        }
    }

    private fun record(phase: DiagnosticPhase, failure: DiagnosticFailure? = null) = diagnostics.record(
        DiagnosticEvent(DiagnosticSource.MegaDebrid, DiagnosticOperation.SessionRenewal, phase, failure = failure))

    private fun account(username: String, password: String, session: MegaDebridSession) = JSONObject()
        .put("username", username).put("password", password)
        .put("token", session.token).put("premiumUntil", session.premiumUntil)
}
