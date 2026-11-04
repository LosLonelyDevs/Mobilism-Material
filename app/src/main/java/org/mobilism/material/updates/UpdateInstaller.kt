package org.mobilism.material.updates

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import androidx.core.content.pm.PackageInfoCompat
import androidx.core.net.toUri
import java.io.File
import org.mobilism.material.data.AppRelease

internal fun canInstallUpdates(context: Context): Boolean = context.packageManager.canRequestPackageInstalls()

internal fun updatePermissionIntent(context: Context) =
    Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, "package:${context.packageName}".toUri())

internal fun updateInstallIntent(context: Context, file: File): Intent {
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.updates", file)
    return Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(uri, "application/vnd.android.package-archive")
        clipData = ClipData.newRawUri("App update", uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
}

// Android verifies again; reject the wrong app, version, or signer before opening its installer.
internal fun validateUpdatePackage(context: Context, file: File, release: AppRelease) {
    val manager = context.packageManager
    val flags = if (Build.VERSION.SDK_INT >= 28) PackageManager.GET_SIGNING_CERTIFICATES else PackageManager.GET_SIGNATURES
    val archive = manager.getPackageArchiveInfo(file.absolutePath, flags)
        ?: error("The downloaded file is not a valid APK.")
    val installed = manager.getPackageInfo(context.packageName, flags)
    require(archive.packageName == context.packageName) { "This APK is for a different app." }
    require(archive.versionName == release.version &&
        PackageInfoCompat.getLongVersionCode(archive) > PackageInfoCompat.getLongVersionCode(installed)) {
        "This APK is not the advertised newer version. Check for updates again."
    }
    require((archive.applicationInfo?.minSdkVersion ?: Int.MAX_VALUE) <= Build.VERSION.SDK_INT) {
        "This update needs a newer Android version."
    }
    val current = packageSigners(installed)
    require(current.isNotEmpty() && current == packageSigners(archive)) {
        "This update was signed with a different key and cannot replace this app."
    }
}

private fun packageSigners(info: PackageInfo): Set<String> =
    (if (Build.VERSION.SDK_INT >= 28) info.signingInfo?.apkContentsSigners else info.signatures)
        ?.map { it.toCharsString() }?.toSet().orEmpty()
