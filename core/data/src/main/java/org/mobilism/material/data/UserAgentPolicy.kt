package org.mobilism.material.data

import org.json.JSONObject

data class UserAgentRule(val id: String, val name: String, val enabled: Boolean, val allVersions: Boolean,
    val versions: Set<String>, val userAgent: String, val statuses: Set<Int>) {
    fun matches(version: String) = enabled && (allVersions || version in versions)

    companion object {
        private val supportedStatuses = setOf(403, 406, 426)
        internal fun parse(json: JSONObject): UserAgentRule? = runCatching {
            val id = json.get("id") as String
            val name = json.get("name") as String
            val enabled = json.get("enabled") as Boolean
            val allVersions = json.get("allVersions") as Boolean
            val versionJson = json.getJSONArray("versions")
            require(versionJson.length() <= 100)
            val versions = (0 until versionJson.length()).map { versionJson.get(it) as String }.toSet()
            val agent = json.get("userAgent") as String
            val statusJson = json.getJSONArray("statuses")
            require(statusJson.length() <= supportedStatuses.size)
            val statuses = (0 until statusJson.length()).map { statusJson.get(it) as Int }.toSet()
            require(id.matches(Regex("[A-Za-z0-9_-]{1,80}")))
            require(name.isNotBlank() && name.length <= 80 && name.none { it.code < 32 || it.code == 127 })
            require(versions.all { it.length <= 50 && it.matches(Regex("\\d+\\.\\d+\\.\\d+(?:[-+][A-Za-z0-9.-]+)?")) })
            require(if (allVersions) versions.isEmpty() else versions.isNotEmpty())
            require(agent.length in 1..256 && agent.isNotBlank() && agent.all { it.code in 32..126 })
            require(statuses.all { it in supportedStatuses })
            UserAgentRule(id, name.trim(), enabled, allVersions, versions, agent.trim(), statuses)
        }.getOrNull()
    }
}

class UserAgentPolicy(private val version: String, private val rules: () -> List<UserAgentRule>) {
    private var snapshot = emptyList<UserAgentRule>()
    private val activated = mutableSetOf<String>()

    private fun eligible(): List<UserAgentRule> {
        val current = rules()
        if (snapshot != current) { snapshot = current; activated.clear() }
        return current.filter { it.matches(version) }
    }

    @Synchronized fun prefix(): String? = eligible()
        .firstOrNull { it.statuses.isEmpty() || it.id in activated }?.userAgent

    // Observing a response never sends a request. The caller must not replay it for this change.
    @Synchronized fun response(status: Int): Boolean {
        val previous = prefix()
        eligible().filter { status in it.statuses }.forEach { activated.add(it.id) }
        return prefix() != previous
    }
}

interface UserAgentPolicyProvider { val userAgentPolicy: UserAgentPolicy }
