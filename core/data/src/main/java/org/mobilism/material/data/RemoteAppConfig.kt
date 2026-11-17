package org.mobilism.material.data

import java.time.Instant
import org.json.JSONObject

data class ApiRequestLimits(
    val searchSpacingMs: Long = 3_000,
    val searchCacheTtlMs: Long = 5 * 60_000,
    val searchFailureCooldownMs: Long = 60_000,
    val categorySearchRequestLimit: Int = 6,
    val categorySearchConcurrency: Int = 4,
    val readRetryDelayMs: Long = 500,
    val readSpacingMs: Long = 250,
    val readConcurrency: Int = 3,
    val readFailureCooldownMs: Long = 60_000,
    val readFailureMaxCooldownMs: Long = 15 * 60_000,
    val discussionCategoriesCacheTtlMs: Long = 5 * 60_000,
    val discussionFirstPageCacheTtlMs: Long = 60_000,
    val messagePollIntervalMs: Long = 60_000,
    val messageFailureMaxCooldownMs: Long = 15 * 60_000,
    val memberLookupSpacingMs: Long = 1_000,
    val memberCacheTtlMs: Long = 60 * 60_000,
    val memberMissingCacheTtlMs: Long = 60 * 60_000,
    val memberFailureCooldownMs: Long = 60_000,
    val memberFailureMaxCooldownMs: Long = 15 * 60_000,
    val debridRefreshIntervalMs: Long = 15 * 60_000,
) {
    companion object {
        val Defaults = ApiRequestLimits()

        fun parse(json: JSONObject?): ApiRequestLimits {
            fun value(key: String, fallback: Long, range: LongRange): Long {
                val raw = json?.opt(key)
                return (raw as? Number)?.toLong()?.takeIf { raw.toDouble() == it.toDouble() && it in range } ?: fallback
            }
            return ApiRequestLimits(
                value("searchSpacingMs", Defaults.searchSpacingMs, 1_000L..60_000L),
                value("searchCacheTtlMs", Defaults.searchCacheTtlMs, 0L..3_600_000L),
                value("searchFailureCooldownMs", Defaults.searchFailureCooldownMs, 60_000L..3_600_000L),
                value("categorySearchRequestLimit", Defaults.categorySearchRequestLimit.toLong(), 1L..30L).toInt(),
                value("categorySearchConcurrency", Defaults.categorySearchConcurrency.toLong(), 1L..4L).toInt(),
                value("readRetryDelayMs", Defaults.readRetryDelayMs, 0L..5_000L),
                value("readSpacingMs", Defaults.readSpacingMs, 100L..60_000L),
                value("readConcurrency", Defaults.readConcurrency.toLong(), 1L..3L).toInt(),
                value("readFailureCooldownMs", Defaults.readFailureCooldownMs, 60_000L..3_600_000L),
                value("readFailureMaxCooldownMs", Defaults.readFailureMaxCooldownMs, 60_000L..3_600_000L),
                value("discussionCategoriesCacheTtlMs", Defaults.discussionCategoriesCacheTtlMs, 0L..3_600_000L),
                value("discussionFirstPageCacheTtlMs", Defaults.discussionFirstPageCacheTtlMs, 0L..3_600_000L),
                value("messagePollIntervalMs", Defaults.messagePollIntervalMs, 60_000L..3_600_000L),
                value("messageFailureMaxCooldownMs", Defaults.messageFailureMaxCooldownMs, 60_000L..3_600_000L),
                value("memberLookupSpacingMs", Defaults.memberLookupSpacingMs, 1_000L..60_000L),
                value("memberCacheTtlMs", Defaults.memberCacheTtlMs, 60_000L..86_400_000L),
                value("memberMissingCacheTtlMs", Defaults.memberMissingCacheTtlMs, 60_000L..86_400_000L),
                value("memberFailureCooldownMs", Defaults.memberFailureCooldownMs, 60_000L..3_600_000L),
                value("memberFailureMaxCooldownMs", Defaults.memberFailureMaxCooldownMs, 60_000L..3_600_000L),
                value("debridRefreshIntervalMs", Defaults.debridRefreshIntervalMs, 60_000L..3_600_000L),
            )
        }
    }
}

data class AppNotice(
    val id: String,
    val title: String,
    val message: String,
    val mode: String,
    val frequency: String,
    val enabled: Boolean,
    val startsAt: Long?,
    val endsAt: Long?,
    val minVersionCode: Int,
    val maxVersionCode: Int?,
    val requiredVersionCode: Int?,
    val linkText: String?,
    val linkUrl: String?,
    val audience: NoticeAudience = NoticeAudience.Everyone,
    val deviceNotification: Boolean = false,
) {
    val required: Boolean get() = mode == "required"
    fun applies(versionCode: Int, now: Long): Boolean = enabled &&
        (startsAt == null || now >= startsAt) && (endsAt == null || now < endsAt) &&
        versionCode >= minVersionCode && (maxVersionCode == null || versionCode <= maxVersionCode) &&
        (!required || versionCode < (requiredVersionCode ?: 0))

    // Copy changes should appear again without unrelated configuration edits resurfacing a notice.
    val dismissalKey: String get() = java.security.MessageDigest.getInstance("SHA-256")
        .digest(toString().toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }

    companion object {
        internal fun parse(json: JSONObject): AppNotice? = runCatching {
            fun text(key: String, max: Int): String = json.getString(key).also {
                require(it.isNotBlank() && it.length <= max && it.none { c -> c == '\u0000' })
            }
            fun integer(key: String, optional: Boolean = false): Int? {
                if (optional && json.isNull(key)) return null
                val raw = json.get(key) as Number
                return raw.toInt().also { require(raw.toDouble() == it.toDouble() && it > 0) }
            }
            fun date(key: String): Long? = if (json.isNull(key)) null else Instant.parse(json.getString(key)).toEpochMilli()
            val start = date("startsAt")
            val end = date("endsAt")
            require(start == null || end == null || start < end)
            val mode = text("mode", 10).also { require(it in setOf("notice", "update", "required")) }
            val frequency = text("frequency", 10).also { require(it in setOf("once", "launch", "always")) }
            val min = integer("minVersionCode")!!
            val max = integer("maxVersionCode", true)
            require(max == null || max >= min)
            val required = integer("requiredVersionCode", true)
            require(mode != "required" || required != null)
            val url = if (json.isNull("linkUrl")) null else text("linkUrl", 2048).also {
                val uri = java.net.URI(it)
                require(uri.scheme == "https" && !uri.host.isNullOrBlank() && uri.userInfo == null)
            }
            val label = if (url == null) null else text("linkText", 80)
            AppNotice(text("id", 80), text("title", 120), text("message", 2000), mode, frequency,
                json.get("enabled") as Boolean, start, end, min, max, required, label, url,
                NoticeAudience.parse(json.getJSONObject("audience")),
                if (json.has("deviceNotification")) json.get("deviceNotification") as Boolean else false)
        }.getOrNull()
    }
}

data class RemoteAppConfig(val limits: ApiRequestLimits = ApiRequestLimits.Defaults, val notices: List<AppNotice> = emptyList(),
    val userAgentRules: List<UserAgentRule> = emptyList()) {
    companion object {
        fun parse(text: String): RemoteAppConfig {
            require(text.toByteArray(Charsets.UTF_8).size <= 65_536)
            val json = JSONObject(text)
            require(json.opt("schema") == 2)
            val notices = json.optJSONArray("notices")
            val rules = json.optJSONArray("userAgentRules")?.takeIf { it.length() <= 10 }
            require(notices == null || notices.length() <= 10)
            return RemoteAppConfig(ApiRequestLimits.parse(json.optJSONObject("limits")),
                (0 until (notices?.length() ?: 0)).mapNotNull { notices?.optJSONObject(it)?.let(AppNotice::parse) }
                    .distinctBy { it.id },
                (0 until (rules?.length() ?: 0)).mapNotNull { rules?.optJSONObject(it)?.let(UserAgentRule::parse) }
                    .distinctBy { it.id })
        }
    }
}
