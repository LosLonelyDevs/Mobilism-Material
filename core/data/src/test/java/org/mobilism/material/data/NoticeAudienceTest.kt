package org.mobilism.material.data

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class NoticeAudienceTest {
    private fun context(username: String? = "alice", version: String = "0.1.20", build: Int = 21, api: Int = 36) =
        NoticeContext(username, version, build, api)
    private fun users(vararg names: String) = NoticeAudience.Users(names.map(NoticeAudience::usernameHash).toSet())
    private fun versions(vararg names: String) = NoticeAudience.Versions(names.toSet())
    private fun all(vararg conditions: NoticeAudience) = NoticeAudience.Group(true, conditions.toList())
    private fun any(vararg conditions: NoticeAudience) = NoticeAudience.Group(false, conditions.toList())

    @Test fun allowlistAndVersionsExcludeEveryNonmatchingAccountAndVersion() {
        val audience = all(users("alice", "bob"), versions("0.1.20", "0.1.19"))
        assertTrue(audience.matches(context(" ALICE ")))
        assertTrue(audience.matches(context("bob", "0.1.19")))
        assertFalse(audience.matches(context("charlie")))
        assertFalse(audience.matches(context(null)))
        assertFalse(audience.matches(context("alice", "0.1.21")))
        assertFalse(audience.matches(context("alice").copy(identityKnown = false)))
    }

    @Test fun nestedAnyGroupsKeepUserSpecificVersionRequirementsSeparate() {
        val audience = all(any(all(users("alice"), versions("0.1.20")), all(users("bob"), versions("0.1.19"))),
            NoticeAudience.NumberRule("androidApi", "gte", 33))
        assertTrue(audience.matches(context()))
        assertTrue(audience.matches(context("bob", "0.1.19")))
        assertFalse(audience.matches(context("bob")))
        assertFalse(audience.matches(context("alice", "0.1.19")))
        assertFalse(audience.matches(context(api = 32)))
    }

    @Test fun exclusionsNeverMatchUnknownIdentityAndEveryoneIsExplicit() {
        assertTrue(NoticeAudience.Everyone.matches(context(null)))
        assertTrue(NoticeAudience.SignedIn(false).matches(context(null)))
        assertFalse(NoticeAudience.SignedIn(false).matches(context(null).copy(identityKnown = false)))
        val exclusion = users("alice").copy(include = false)
        assertFalse(exclusion.matches(context(null)))
        assertTrue(exclusion.matches(context("bob")))
        assertFalse(NoticeAudience.Group(true, emptyList()).matches(context()))
    }

    @Test fun clientAndServiceShareTheSameUnicodeNormalizedUsernameHash() {
        for (name in listOf("alice", " Alice ", "ＡLICE")) assertEquals(
            "148224b0bb7b29e47327e246bdcb1cd727fbac22780e4cdac6775731bc06543c", NoticeAudience.usernameHash(name))
    }

    @Test fun allNumericOperatorsRespectTheirBoundary() {
        for ((operator, values) in mapOf("eq" to setOf(21), "lt" to setOf(20), "lte" to setOf(20, 21), "gt" to setOf(22), "gte" to setOf(21, 22))) {
            val rule = NoticeAudience.NumberRule("versionCode", operator, 21)
            for (value in 20..22) assertEquals(value in values, rule.matches(context(build = value)))
        }
    }

    @Test fun publicRulesParseButAnyUnknownOrEmptyRuleInvalidatesTheEntireAudience() {
        val rule = """{"type":"rule","field":"usernameHash","operator":"in","values":["${NoticeAudience.usernameHash("alice")}"]}"""
        val valid = NoticeAudience.parse(JSONObject("""{"type":"all","conditions":[$rule]}"""))
        assertTrue(valid.matches(context()))
        assertFalse(valid.matches(context("bob")))
        for (json in listOf("{}", """{"type":"all","conditions":[]}""",
            """{"type":"rule","field":"username","operator":"in","values":["alice"]}""",
            """{"type":"any","conditions":[$rule,{"type":"everyone"}]}""",
            """{"type":"any","conditions":[$rule,{"type":"unknown"}]}""",
            """{"type":"rule","field":"versionCode","operator":"gte","value":"21"}""")) {
            assertTrue(runCatching { NoticeAudience.parse(JSONObject(json)) }.isFailure)
        }
    }
}
