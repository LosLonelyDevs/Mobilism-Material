package org.mobilism.material.data

fun interface ChallengeRecovery {
    suspend fun recover(site: VerificationSite): Boolean

    suspend fun retryProfile(read: suspend () -> String): String? =
        if (recover(VerificationSite.Forum)) read() else null

    companion object {
        val None = ChallengeRecovery { false }
    }
}
