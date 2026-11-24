package org.mobilism.material.data

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class RemoteAppConfigTest {
    @Test fun cacheLifetimesAndResetsAcceptOnlySupportedValues() {
        val limits = RemoteAppConfig.parse("""{"schema":2,"limits":{"releaseTopCacheTtlMs":0,
            "releaseLatestCacheTtlMs":12345,"premiumHostCacheTtlMs":604800001},
            "cacheResets":{"Latest":2,"Top":0,"RealDebrid":3,"MegaDebrid":"4","Unknown":5}}""").limits
        assertEquals(0L, limits.releaseTopCacheTtlMs)
        assertEquals(12345L, limits.releaseLatestCacheTtlMs)
        assertTrue(limits.customTopLifetime && limits.customLatestLifetime)
        assertEquals(0L, limits.premiumHostCacheTtlMs)
        assertEquals(mapOf("Latest" to 2, "RealDebrid" to 3), limits.cacheResets)
        assertFalse(ApiRequestLimits.parse(JSONObject("""{"releaseTopCacheTtlMs":86400001}""")).customTopLifetime)
    }

    @Test fun imageControlsUseSafeDefaultsAndAcceptIndependentOverrides() {
        fun parse(fields: String) = RemoteAppConfig.parse("""{"schema":2,$fields}""").images
        assertEquals(ImageCacheControls(), parse(""""imageCacheEnabled":"false","imageRetryGeneration":-1"""))
        assertEquals(ImageCacheControls(cacheEnabled = false, retryGeneration = 7),
            parse(""""imageCacheEnabled":false,"imageRetryGeneration":7"""))
        assertEquals(ImageCacheControls(retriesEnabled = false), parse(""""imageRetriesEnabled":false"""))
    }
    @Test fun followingGestureFailsClosedAndCacheOverridesDoNotChangeDirectLimits() {
        val defaults = ApiRequestLimits.Defaults
        for (invalid in listOf(JSONObject.NULL, "true", 1, false))
            assertFalse(ApiRequestLimits.parse(JSONObject().put("followPullToRefreshEnabled", invalid)).followPullToRefreshEnabled)
        assertEquals(defaults, ApiRequestLimits.parse(null))
        val json = JSONObject("""{"followPullToRefreshEnabled":true,"followCacheIntervalMinutes":30,
            "followCacheManualCooldownMs":60000,"followCacheBatchSize":512}""")
        assertEquals(defaults.copy(followPullToRefreshEnabled = true, followCacheIntervalMinutes = 30,
            followCacheManualCooldownMs = 60000, followCacheBatchSize = 512), ApiRequestLimits.parse(json))
    }

    @Test fun followingLimitsAcceptBoundsAndFallBackIndividuallyWithoutAProtocolBump() {
        val bounds = listOf("followBatchSize" to (1L..20L), "followAppIntervalMs" to (3_600_000L..604_800_000L),
            "followAttemptIntervalMs" to (900_000L..86_400_000L))
        fun actual(limits: ApiRequestLimits, key: String) = when (key) {
            "followBatchSize" -> limits.followBatchSize.toLong()
            "followAppIntervalMs" -> limits.followAppIntervalMs
            else -> limits.followAttemptIntervalMs
        }
        for ((key, range) in bounds) {
            for (value in listOf(range.first, range.last)) {
                assertEquals(value, actual(ApiRequestLimits.parse(JSONObject().put(key, value)), key))
            }
            for (value in listOf(range.first - 1, range.last + 1, 1.5, "5", JSONObject.NULL)) {
                assertEquals(actual(ApiRequestLimits.Defaults, key), actual(ApiRequestLimits.parse(JSONObject().put(key, value)), key))
            }
        }
        val parsed = RemoteAppConfig.parse("""{"schema":2,"limits":{"followBatchSize":8,"followAppIntervalMs":3600000,
            "followAttemptIntervalMs":1800000},"notices":[]}""")
        assertEquals(ApiRequestLimits.Defaults.copy(followBatchSize = 8, followAppIntervalMs = 3_600_000,
            followAttemptIntervalMs = 1_800_000), parsed.limits)
    }

    @Test fun upstreamPolicyOverridesAreParsedAndInvalidValuesKeepShippedDefaults() {
        val valid = JSONObject("""{"readSpacingMs":2000,"readConcurrency":1,"readFailureCooldownMs":120000,
            "discussionCategoriesCacheTtlMs":0,"messagePollIntervalMs":180000,"memberLookupSpacingMs":5000,
            "memberCacheTtlMs":60000,"debridRefreshIntervalMs":60000}""")
        assertEquals(ApiRequestLimits.Defaults.copy(readSpacingMs = 2_000, readConcurrency = 1,
            readFailureCooldownMs = 120_000, discussionCategoriesCacheTtlMs = 0,
            messagePollIntervalMs = 180_000, memberLookupSpacingMs = 5_000,
            memberCacheTtlMs = 60_000, debridRefreshIntervalMs = 60_000), ApiRequestLimits.parse(valid))
        val invalid = JSONObject()
        for (key in listOf("readSpacingMs", "readConcurrency", "readFailureCooldownMs", "readFailureMaxCooldownMs",
            "discussionCategoriesCacheTtlMs", "discussionFirstPageCacheTtlMs", "messagePollIntervalMs",
            "messageFailureMaxCooldownMs", "memberLookupSpacingMs", "memberCacheTtlMs", "memberMissingCacheTtlMs",
            "memberFailureCooldownMs", "memberFailureMaxCooldownMs", "debridRefreshIntervalMs")) invalid.put(key, -1)
        assertEquals(ApiRequestLimits.Defaults, ApiRequestLimits.parse(invalid))
    }

    private fun notice() = JSONObject("""{"id":"test","title":"Title","message":"Message","mode":"notice",
        "frequency":"once","enabled":true,"startsAt":null,"endsAt":null,"minVersionCode":1,
        "maxVersionCode":null,"requiredVersionCode":null,"linkText":null,"linkUrl":null,"audience":{"type":"everyone"}}""")

    @Test fun deviceNotificationsAreOptInAndRejectMalformedFlags() {
        assertFalse(AppNotice.parse(notice())!!.deviceNotification)
        assertFalse(AppNotice.parse(notice().put("deviceNotification", false))!!.deviceNotification)
        assertTrue(AppNotice.parse(notice().put("deviceNotification", true))!!.deviceNotification)
        assertNull(AppNotice.parse(notice().put("deviceNotification", "true")))
        assertNull(AppNotice.parse(notice().put("deviceNotification", JSONObject.NULL)))
    }

    @Test fun missingInvalidAndOutOfRangeLimitsUseHardcodedValuesPerField() {
        assertEquals(ApiRequestLimits.Defaults, ApiRequestLimits.parse(null))
        val parsed = ApiRequestLimits.parse(JSONObject("""{"searchSpacingMs":10000,"searchCacheTtlMs":-1,
            "searchFailureCooldownMs":"1","categorySearchRequestLimit":999,"categorySearchConcurrency":1.5,"readRetryDelayMs":null}"""))
        assertEquals(ApiRequestLimits.Defaults.copy(searchSpacingMs = 10_000), parsed)
        assertEquals(0L, ApiRequestLimits.parse(JSONObject("""{"searchCacheTtlMs":0}""")).searchCacheTtlMs)
    }

    @Test fun requiredNoticeTargetsOnlyOlderBuildsWithinScheduleAndAudience() {
        val parsed = AppNotice.parse(notice().put("mode", "required").put("requiredVersionCode", 30)
            .put("minVersionCode", 10).put("startsAt", "2026-11-16T10:00:00.000Z")
            .put("endsAt", "2026-11-16T12:00:00.000Z"))!!
        val start = java.time.Instant.parse("2026-11-16T10:00:00Z").toEpochMilli()
        assertFalse(parsed.applies(9, start))
        assertFalse(parsed.applies(20, start - 1))
        assertTrue(parsed.applies(20, start))
        assertFalse(parsed.applies(30, start))
        assertFalse(parsed.applies(20, start + 2 * 60 * 60_000))
        assertFalse(parsed.copy(enabled = false).applies(20, start))
    }

    @Test fun invalidNoticesAreIgnoredWithoutDroppingValidLimits() {
        for (json in listOf(notice().put("mode", "required"), notice().put("linkUrl", "javascript:alert(1)").put("linkText", "Read"),
            notice().put("startsAt", "broken"), notice().put("minVersionCode", "1"))) {
            val config = RemoteAppConfig.parse(JSONObject().put("schema", 2).put("limits", JSONObject().put("searchSpacingMs", 10000))
                .put("notices", org.json.JSONArray().put(json)).toString())
            assertTrue(config.notices.isEmpty())
            assertEquals(10_000L, config.limits.searchSpacingMs)
        }
        assertTrue(runCatching { RemoteAppConfig.parse("{\"schema\":1}") }.isFailure)
        assertTrue(runCatching { RemoteAppConfig.parse("not JSON") }.isFailure)
    }

    @Test fun remoteEndpointStaysOnConfiguredHttpsOrigin() {
        assertEquals("https://example.org/api/app-config", RemoteConfigClient.endpoint("https://example.org/api/ping"))
        for (url in listOf("", "http://example.org/api/ping", "https://user:secret@example.org/api/ping", "https://example.org/api/ping?token=secret"))
            assertNull(RemoteConfigClient.endpoint(url))
    }

    @Test fun userAgentIncludesRealVersionAndBuildWithAndWithoutBrowser() {
        assertEquals("MobilismMaterial/0.1.20 (Android; build 21)", AppUserAgent.format(null, "0.1.20", 21))
        assertEquals("Mozilla/5.0 MobilismMaterial/0.1.21 (Android; build 22)", AppUserAgent.format("Mozilla/5.0", "0.1.21", 22))
        assertFalse(AppUserAgent.format("bad\r\nheader", "bad\r\nversion", 21).contains('\n'))
    }
}
