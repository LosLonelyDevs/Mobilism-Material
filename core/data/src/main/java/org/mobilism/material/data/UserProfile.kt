package org.mobilism.material.data

import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.jsoup.Jsoup

data class ProfileReference(val id: Long? = null, val username: String? = null) {
    init { require(id == null || id > 0); require(id != null || !username.isNullOrBlank()) }
    val url: String get() = "https://forum.mobilism.org/memberlist.php".toHttpUrl().newBuilder()
        .addQueryParameter("mode", "viewprofile").apply {
            if (id != null) addQueryParameter("u", id.toString()) else addQueryParameter("un", username!!.trim())
        }.build().toString()

    companion object {
        fun fromUrl(value: String): ProfileReference? {
            val url = value.toHttpUrlOrNull() ?: return null
            if (url.host !in setOf("forum.mobilism.org", "mobilism.org", "www.mobilism.org") ||
                url.encodedPath != "/memberlist.php" || url.queryParameter("mode") != "viewprofile" ||
                url.username.isNotEmpty() || url.password.isNotEmpty() || url.port !in setOf(80, 443)) return null
            val ids = url.queryParameterValues("u")
            val names = url.queryParameterValues("un")
            if (ids.isNotEmpty()) return ids.singleOrNull()?.toLongOrNull()?.takeIf { it > 0 }?.let { ProfileReference(id = it) }
            return names.singleOrNull()?.trim()?.takeIf { it.isNotBlank() }?.let { ProfileReference(username = it) }
        }
    }
}

enum class AccountRole(val label: String) { Administrator("Administrator"), Moderator("Moderator"), Developer("Developer"), Member("Member") }
enum class ProfileContactKind { Telegram, WhatsApp, Website }
data class ProfileContact(val label: String, val url: String, val kind: ProfileContactKind)

data class UserProfile(
    val reference: ProfileReference, val username: String, val avatar: String?, val roles: Set<AccountRole>,
    val rank: String, val groups: List<String>, val about: String, val details: Map<String, String>,
    val joined: String?, val lastVisited: String?, val posts: String?, val balance: String?,
    val contacts: List<ProfileContact> = emptyList(),
)

object ForumProfiles {
    fun parse(html: String, reference: ProfileReference): UserProfile {
        val document = Jsoup.parse(html, reference.url)
        val profile = document.selectFirst("#viewprofile") ?: invalid()
        val fields = linkedMapOf<String, String>()
        profile.select("ul.reset-list > li, ul.recent-activity > li").forEach { row ->
            val text = row.text()
            if (':' !in text) return@forEach
            val label = text.substringBefore(':').trim()
            val value = text.substringAfter(':').trim()
            if (label.isNotBlank() && value.isNotBlank() && value !in setOf("Not Specified", "-")) fields[label] = value
        }
        val name = fields.remove("Username")?.trim()?.takeIf { it.isNotBlank() } ?: invalid()
        if (reference.username != null && !name.equals(reference.username.trim(), ignoreCase = true)) invalid()
        val groups = profile.select("select[name=g] option").map { it.text().trim() }.filter { it.isNotBlank() }.distinct()
        val rank = profile.select(".span2 > ul.unstyled > li").firstOrNull { it.select("img").isEmpty() && it.text().isNotBlank() }?.text().orEmpty()
        // Role badges only use the forum's rank and group controls, never free-form biography fields.
        val roles = buildSet {
            if (groups.any { it.equals("Administrators", true) } || rank.equals("Administrator", true)) add(AccountRole.Administrator)
            else if (groups.any { it.equals("Moderators", true) || it.endsWith(" Moderators", true) } ||
                rank.equals("Moderator", true) || rank.endsWith(" Moderator", true)) add(AccountRole.Moderator)
            if (groups.any { it.equals("Developers", true) } || rank.equals("Developer", true)) add(AccountRole.Developer)
            if (isEmpty()) add(AccountRole.Member)
        }
        val avatar = profile.selectFirst(".avatar-frame img[src], .span2 img[src]")?.absUrl("src")?.let(::webUrlOrNull)
        val about = fields.remove("About me").orEmpty()
        val joined = fields.remove("Joined")
        val lastVisited = fields.remove("Last visited")
        val posts = fields.remove("Total posts")?.substringBefore('(')?.trim()
        val balance = fields.remove("WRZ$")
        fields.keys.filter { it.equals("Website", ignoreCase = true) }.forEach(fields::remove)
        val contacts = profile.select("a[href]").mapNotNull { anchor ->
            val link = webUrlOrNull(anchor.absUrl("href"))?.toHttpUrlOrNull() ?: return@mapNotNull null
            if (link.username.isNotEmpty() || link.password.isNotEmpty() || link.host == "forum.mobilism.org") return@mapNotNull null
            val kind = when {
                link.host in setOf("t.me", "telegram.me", "www.telegram.me") -> ProfileContactKind.Telegram
                link.host == "wa.me" || link.host == "whatsapp.com" || link.host.endsWith(".whatsapp.com") -> ProfileContactKind.WhatsApp
                else -> ProfileContactKind.Website
            }
            val label = when (kind) {
                ProfileContactKind.Telegram -> "Telegram"
                ProfileContactKind.WhatsApp -> "WhatsApp"
                ProfileContactKind.Website -> anchor.text().ifBlank { link.host }.take(80)
            }
            ProfileContact(label, link.toString(), kind)
        }.distinctBy { it.url }
        return UserProfile(reference, name, avatar, roles, rank, groups, about, fields, joined, lastVisited, posts, balance, contacts)
    }

    private fun invalid(): Nothing = throw ApiFailure(FailureKind.InvalidResponse, "The forum did not return this member's profile.")
}
