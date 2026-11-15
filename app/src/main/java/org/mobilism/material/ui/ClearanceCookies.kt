package org.mobilism.material.ui

import android.content.Context
import android.webkit.CookieManager
import androidx.core.content.edit
import androidx.webkit.CookieManagerCompat
import androidx.webkit.WebViewFeature
import okhttp3.Cookie
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.mobilism.material.data.VerificationSite

internal data class ClearanceExpiry(val expiresAt: Long?, val present: Boolean)

internal class ClearanceCookies(context: Context) {
    private val preferences = context.getSharedPreferences("connection-verification-expiry", Context.MODE_PRIVATE)

    fun read(site: VerificationSite): ClearanceExpiry {
        val manager = CookieManager.getInstance()
        val present = manager.getCookie(site.url).orEmpty().split(';').any { it.trim().startsWith("cf_clearance=") }
        val expiresAt = if (WebViewFeature.isFeatureSupported(WebViewFeature.GET_COOKIE_INFO)) {
            CookieManagerCompat.getCookieInfo(manager, site.url).mapNotNull { Cookie.parse(site.url.toHttpUrl(), it) }
                .filter { it.name == "cf_clearance" && it.persistent }.minOfOrNull { it.expiresAt }
        } else null
        if (expiresAt != null) preferences.edit { putLong(site.name, expiresAt) }
        // Keep only expiry metadata here. Cookie values stay in WebView's cookie store.
        return ClearanceExpiry(expiresAt ?: if (!present) preferences.getLong(site.name, 0).takeIf { it > 0 } else null, present)
    }
}
