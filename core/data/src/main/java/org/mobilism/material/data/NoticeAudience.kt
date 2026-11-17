package org.mobilism.material.data

import java.security.MessageDigest
import java.text.Normalizer
import java.util.Locale
import org.json.JSONObject

data class NoticeContext(val username: String?, val versionName: String, val versionCode: Int,
    val androidApi: Int, val identityKnown: Boolean = true) {
    val signedIn: Boolean? get() = if (identityKnown) !username.isNullOrBlank() else null
}

sealed interface NoticeAudience {
    fun matches(context: NoticeContext): Boolean

    data object Everyone : NoticeAudience {
        override fun matches(context: NoticeContext) = true
    }

    data class Group(val all: Boolean, val conditions: List<NoticeAudience>) : NoticeAudience {
        override fun matches(context: NoticeContext): Boolean = conditions.isNotEmpty() &&
            if (all) conditions.all { it.matches(context) } else conditions.any { it.matches(context) }
    }

    data class Users(val hashes: Set<String>, val include: Boolean = true) : NoticeAudience {
        override fun matches(context: NoticeContext): Boolean {
            if (context.signedIn != true) return false
            return (usernameHash(checkNotNull(context.username)) in hashes) == include
        }
    }

    data class Versions(val versions: Set<String>, val include: Boolean = true) : NoticeAudience {
        override fun matches(context: NoticeContext): Boolean {
            val version = AppVersions.parts(context.versionName)?.joinToString(".") ?: return false
            return (version in versions) == include
        }
    }

    data class NumberRule(val field: String, val operator: String, val value: Int) : NoticeAudience {
        override fun matches(context: NoticeContext): Boolean {
            val actual = when (field) { "versionCode" -> context.versionCode; "androidApi" -> context.androidApi; else -> return false }
            if (actual < 1) return false
            return when (operator) {
                "eq" -> actual == value
                "lt" -> actual < value
                "lte" -> actual <= value
                "gt" -> actual > value
                "gte" -> actual >= value
                else -> false
            }
        }
    }

    data class SignedIn(val value: Boolean) : NoticeAudience {
        override fun matches(context: NoticeContext) = context.signedIn == value
    }

    companion object {
        fun usernameHash(username: String): String {
            val normalized = Normalizer.normalize(username, Normalizer.Form.NFKC).trim().lowercase(Locale.ROOT)
            return MessageDigest.getInstance("SHA-256").digest("mobilism-notice-user-v1\u0000$normalized".toByteArray(Charsets.UTF_8))
                .joinToString("") { "%02x".format(it) }
        }

        fun parse(json: JSONObject): NoticeAudience {
            var count = 0
            fun visit(item: JSONObject, depth: Int): NoticeAudience {
                require(++count <= 24 && depth <= 3)
                fun keys(vararg allowed: String) { require(item.keys().asSequence().toSet() == allowed.toSet()) }
                when (item.getString("type")) {
                    "everyone" -> { keys("type"); require(depth == 0); return Everyone }
                    "all", "any" -> {
                        keys("type", "conditions")
                        val children = item.getJSONArray("conditions")
                        require(children.length() in 1..12)
                        return Group(item.getString("type") == "all", (0 until children.length()).map { visit(children.getJSONObject(it), depth + 1) })
                    }
                    "rule" -> {
                        val field = item.getString("field")
                        val operator = item.getString("operator")
                        if (field == "usernameHash" || field == "versionName") {
                            keys("type", "field", "operator", "values")
                            require(operator in setOf("in", "notIn"))
                            val values = item.getJSONArray("values")
                            require(values.length() in 1..100)
                            val strings = (0 until values.length()).map { values.get(it) as String }.toSet()
                            if (field == "usernameHash") {
                                require(strings.all { it.matches(Regex("[a-f0-9]{64}")) })
                                return Users(strings, operator == "in")
                            }
                            require(strings.all { AppVersions.parts(it)?.joinToString(".") == it })
                            return Versions(strings, operator == "in")
                        }
                        keys("type", "field", "operator", "value")
                        if (field == "signedIn") {
                            require(operator == "eq")
                            return SignedIn(item.get("value") as Boolean)
                        }
                        require(field in setOf("versionCode", "androidApi") && operator in setOf("eq", "lt", "lte", "gt", "gte"))
                        val raw = item.get("value") as Number
                        val value = raw.toInt()
                        require(raw.toDouble() == value.toDouble() && value in 1..(if (field == "androidApi") 100 else Int.MAX_VALUE))
                        return NumberRule(field, operator, value)
                    }
                    else -> error("Unsupported notice audience")
                }
            }
            return visit(json, 0)
        }
    }
}
