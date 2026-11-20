package org.mobilism.material.ui

import android.webkit.CookieManager
import androidx.webkit.CookieManagerCompat
import androidx.webkit.WebViewFeature
import okhttp3.Cookie
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.mobilism.material.data.VerificationSite

internal data class ClearanceExpiry(val expiresAt: Long?, val present: Boolean)

internal object ClearanceCookies {
    fun read(site: VerificationSite): ClearanceExpiry {
        val manager = CookieManager.getInstance()
        val present = manager.getCookie(site.url).orEmpty().split(';').any { it.trim().startsWith("cf_clearance=") }
        val expiresAt = if (present && WebViewFeature.isFeatureSupported(WebViewFeature.GET_COOKIE_INFO)) {
            CookieManagerCompat.getCookieInfo(manager, site.url).mapNotNull { Cookie.parse(site.url.toHttpUrl(), it) }
                .filter { it.name == "cf_clearance" && it.persistent }.minOfOrNull { it.expiresAt }
        } else null
        return ClearanceExpiry(expiresAt, present)
    }
}
