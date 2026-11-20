package org.mobilism.material.data

import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext

/** Scheduled reads can use the existing session and leave verification to a user action. */
object BackgroundMobilismRead : AbstractCoroutineContextElement(Key) {
    object Key : CoroutineContext.Key<BackgroundMobilismRead>
}

fun interface ChallengeRecovery {
    suspend fun recover(site: VerificationSite): Boolean

    companion object {
        val None = ChallengeRecovery { false }
    }
}
