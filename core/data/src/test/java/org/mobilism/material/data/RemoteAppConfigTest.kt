package org.mobilism.material.data

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class RemoteAppConfigTest {
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
