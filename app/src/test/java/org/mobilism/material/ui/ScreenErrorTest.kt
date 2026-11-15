package org.mobilism.material.ui

import org.junit.Assert.*
import org.junit.Test
import org.mobilism.material.data.ApiFailure
import org.mobilism.material.data.FailureKind
import org.mobilism.material.data.VerificationSite

class ScreenErrorTest {
    @Test fun challengeOffersVerificationForTheFailedSite() {
        VerificationSite.entries.forEach { site ->
            val error = ApiFailure(FailureKind.Challenge, "Browser check", 403, site).forScreen()
            assertEquals(site, error.verificationSite)
            assertFalse(error.showNetworkSettings)
        }
    }

    @Test fun otherFailuresKeepTheirOwnRecovery() {
        FailureKind.entries.filter { it != FailureKind.Challenge }.forEach { kind ->
            val error = ApiFailure(kind, "Failure").forScreen()
            assertNull(error.verificationSite)
            assertEquals(kind == FailureKind.Permission || kind == FailureKind.Dns, error.showNetworkSettings)
        }
    }
}
