package org.mobilism.material.ui

import org.junit.Assert.*
import org.junit.Test
import org.mobilism.material.data.VerificationSite

class ClearanceRenewalPolicyTest {
    @Test fun renewsTwoMinutesBeforeKnownExpiryOnceAndChecksAgainAfterExpiry() {
        val policy = ClearanceRenewalPolicy()
        val site = VerificationSite.App
        assertFalse(policy.shouldRenew(site, 600_000, true, 479_999))
        assertTrue(policy.shouldRenew(site, 600_000, true, 480_000))
        assertFalse(policy.shouldRenew(site, 600_000, true, 590_000))
        assertTrue(policy.shouldRenew(site, 600_000, false, 600_001))
        assertFalse(policy.shouldRenew(site, 600_000, false, 900_000))
    }

    @Test fun renewedExpiryAndOriginsHaveIndependentSchedules() {
        val policy = ClearanceRenewalPolicy()
        assertTrue(policy.shouldRenew(VerificationSite.App, 100_000, true, 1))
        assertTrue(policy.shouldRenew(VerificationSite.Forum, 100_000, true, 1))
        assertFalse(policy.shouldRenew(VerificationSite.App, 700_000, true, 200_000))
        assertTrue(policy.shouldRenew(VerificationSite.App, 700_000, true, 580_000))
    }

    @Test fun unknownExpiryDoesNotInventARenewalDeadlineAndMissingCookiesGetOneCheck() {
        val policy = ClearanceRenewalPolicy()
        assertFalse(policy.shouldRenew(VerificationSite.App, null, true, 100))
        assertFalse(policy.shouldRenew(VerificationSite.App, null, false, 100))
        assertTrue(policy.shouldRenew(VerificationSite.App, 900_000, false, 100))
        assertFalse(policy.shouldRenew(VerificationSite.App, 900_000, false, 200))
    }
}
