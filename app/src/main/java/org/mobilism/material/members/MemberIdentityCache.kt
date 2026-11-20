package org.mobilism.material.members

import android.content.SharedPreferences
import org.json.JSONObject

class MemberIdentityCache(
    private val preferences: SharedPreferences,
    private val wallTime: () -> Long = System::currentTimeMillis,
) {
    internal data class Entry(val identity: MemberIdentity, val remainingMillis: Long)

    internal fun read(key: String, ttl: Long): Entry? {
        val raw = preferences.getString(key, null) ?: return null
        val entry = runCatching {
            val saved = JSONObject(raw)
            val age = wallTime() - saved.getLong("saved")
            val savedTtl = saved.getLong("ttl")
            if (age !in 0 until savedTtl) return@runCatching null
            if (age >= ttl) return null
            Entry(MemberIdentity(saved.optString("avatar").takeIf { it.isNotEmpty() },
                saved.getBoolean("admin"), saved.getBoolean("major")), minOf(ttl, savedTtl) - age)
        }.getOrNull()
        if (entry == null) preferences.edit().remove(key).apply()
        return entry
    }

    internal fun put(key: String, identity: MemberIdentity, ttl: Long) {
        val saved = JSONObject().put("saved", wallTime()).put("ttl", ttl)
            .put("avatar", identity.avatar.orEmpty()).put("admin", identity.administrator)
            .put("major", identity.majorAndroidReleaser)
        val edit = preferences.edit().putString(key, saved.toString())
        if (!preferences.contains(key)) {
            val entries = preferences.all
            if (entries.size >= 256) {
                entries.entries.sortedBy { runCatching { JSONObject(it.value as String).getLong("saved") }.getOrDefault(0L) }
                    .take(entries.size - 255).forEach { edit.remove(it.key) }
            }
        }
        edit.apply()
    }

}
