package org.mobilism.material.data

import android.content.Context
import androidx.core.content.pm.PackageInfoCompat

object AppUserAgent {
    fun format(browser: String?, version: String, build: Long): String {
        val safeVersion = version.takeIf { it.matches(Regex("[A-Za-z0-9._+-]{1,50}")) } ?: "unknown"
        val identity = "MobilismMaterial/$safeVersion (Android; build $build)"
        return browser?.takeIf { it.isNotBlank() && '\r' !in it && '\n' !in it }?.let { "$it $identity" } ?: identity
    }

    fun value(context: Context): String {
        val configured = (context.applicationContext as? UserAgentPolicyProvider)?.userAgentPolicy?.prefix()
        return configured?.let { identity(context, it) } ?: defaultValue(context)
    }

    fun defaultValue(context: Context): String = identity(context,
        runCatching { android.webkit.WebSettings.getDefaultUserAgent(context) }.getOrNull())

    fun response(context: Context, status: Int): Boolean =
        (context.applicationContext as? UserAgentPolicyProvider)?.userAgentPolicy?.response(status) == true

    fun identity(context: Context, browser: String? = null): String {
        val info = context.packageManager.getPackageInfo(context.packageName, 0)
        return format(browser, info.versionName.orEmpty(), PackageInfoCompat.getLongVersionCode(info))
    }
}
