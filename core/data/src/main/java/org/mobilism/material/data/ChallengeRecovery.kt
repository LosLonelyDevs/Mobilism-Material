package org.mobilism.material.data

fun interface ChallengeRecovery {
    suspend fun recover(site: VerificationSite): Boolean

    companion object {
        val None = ChallengeRecovery { false }
    }
}
