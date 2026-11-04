package org.mobilism.material.data

import okhttp3.HttpUrl.Companion.toHttpUrl

internal fun forumAvatarUrl(value: String): String? {
    val path = value.trim()
    if (path.isEmpty() || path.equals("null", true)) return null
    webUrlOrNull(path)?.let { return it }
    if (path.startsWith('/') || path.startsWith("./") || path.startsWith("download/") || path.startsWith("images/")) {
        return "https://forum.mobilism.org/".toHttpUrl().resolve(path)?.toString()
    }
    return null
}
