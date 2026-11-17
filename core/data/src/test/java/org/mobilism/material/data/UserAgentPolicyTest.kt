package org.mobilism.material.data

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class UserAgentPolicyTest {
    private fun rule(statuses: Set<Int> = emptySet()) = UserAgentRule("new-client", "New clients", true,
        false, setOf("0.1.22"), "MobilismClient", statuses)
    private fun json() = JSONObject("""{"id":"new-client","name":"New clients","enabled":true,"allVersions":false,
        "versions":["0.1.22"],"userAgent":"MobilismClient","statuses":[]}""")

    @Test fun immediateRulesTargetSelectedVersionsAndKeepTheActualVersionAndBuild() {
        val policy = UserAgentPolicy("0.1.22") { listOf(rule()) }
        assertEquals("MobilismClient", policy.prefix())
        assertNull(UserAgentPolicy("0.1.21") { listOf(rule()) }.prefix())
        assertEquals("MobilismClient MobilismMaterial/0.1.22 (Android; build 23)",
            AppUserAgent.format(policy.prefix(), "0.1.22", 23))
        assertEquals("MobilismClient MobilismMaterial/1.2.3 (Android; build 456)",
            AppUserAgent.format(policy.prefix(), "1.2.3", 456))
    }

    @Test fun responseConditionsOnlyActivateForSupportedMatchingStatusesAndDoNotStickAcrossRestarts() {
        val configured = listOf(rule(setOf(403, 426)))
        val policy = UserAgentPolicy("0.1.22") { configured }
        assertNull(policy.prefix())
        for (status in listOf(200, 401, 404, 406, 429, 500, 503)) {
            assertFalse(policy.response(status)); assertNull(policy.prefix())
        }
        assertTrue(policy.response(403)); assertEquals("MobilismClient", policy.prefix())
        assertFalse(policy.response(403))
        assertFalse(policy.response(200)); assertEquals("MobilismClient", policy.prefix())
        assertNull(UserAgentPolicy("0.1.22") { configured }.prefix())
        val oldApp = UserAgentPolicy("0.1.21") { configured }
        assertFalse(oldApp.response(403)); assertNull(oldApp.prefix())
    }

    @Test fun revocationExpiryAndEditsResetTriggersAndPriorityIsDeterministic() {
        val conditional = rule(setOf(403))
        val fallback = rule().copy(id = "fallback", allVersions = true, versions = emptySet(), userAgent = "FallbackClient")
        var rules = listOf(conditional, fallback)
        val policy = UserAgentPolicy("0.1.22") { rules }
        assertEquals("FallbackClient", policy.prefix())
        assertTrue(policy.response(403)); assertEquals("MobilismClient", policy.prefix())
        rules = rules.toList(); assertEquals("MobilismClient", policy.prefix())
        rules = listOf(conditional.copy(userAgent = "ChangedClient"), fallback)
        assertEquals("FallbackClient", policy.prefix())
        policy.response(403); assertEquals("ChangedClient", policy.prefix())
        rules = listOf(conditional.copy(enabled = false), fallback)
        assertEquals("FallbackClient", policy.prefix())
        rules = emptyList(); assertNull(policy.prefix())
        rules = listOf(conditional); assertNull(policy.prefix())
    }

    @Test fun malformedRemoteRulesCannotInjectHeadersOrReplaceShippedLimits() {
        assertNotNull(UserAgentRule.parse(json()))
        val invalid = listOf(json().put("userAgent", "value\r\nAuthorization: injected"),
            json().put("userAgent", "\tvalue"), json().put("userAgent", "a".repeat(257)),
            json().put("statuses", org.json.JSONArray(listOf(429))), json().put("statuses", org.json.JSONArray(listOf("403"))),
            json().put("enabled", "true"), json().put("versions", org.json.JSONArray()),
            json().put("allVersions", true))
        for (value in invalid) {
            assertNull(UserAgentRule.parse(value))
            val config = RemoteAppConfig.parse(JSONObject().put("schema", 2).put("limits", JSONObject().put("searchSpacingMs", 10000))
                .put("userAgentRules", org.json.JSONArray().put(value)).toString())
            assertTrue(config.userAgentRules.isEmpty()); assertEquals(10000L, config.limits.searchSpacingMs)
        }
        assertTrue(RemoteAppConfig.parse("""{"schema":2}""").userAgentRules.isEmpty())
    }
}
