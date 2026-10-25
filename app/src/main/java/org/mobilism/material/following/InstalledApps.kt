package org.mobilism.material.following

import android.content.Context
import android.content.Intent
import android.os.Build
import org.mobilism.material.data.InstalledVersion

class InstalledApps(context: Context) {
    private val manager = context.applicationContext.packageManager

    @Suppress("DEPRECATION")
    fun read(packageName: String): InstalledVersion? = runCatching {
        val info = manager.getPackageInfo(packageName, 0)
        InstalledVersion(packageName, info.applicationInfo?.loadLabel(manager)?.toString() ?: packageName,
            info.versionName.orEmpty(), if (Build.VERSION.SDK_INT >= 28) info.longVersionCode else info.versionCode.toLong())
    }.getOrNull()

    @Suppress("DEPRECATION")
    fun list(): List<InstalledVersion> = manager.queryIntentActivities(
        Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0,
    ).map { it.activityInfo.packageName }.distinct().mapNotNull(::read)
        .let { org.mobilism.material.data.Alphabetical.sorted(it) { app -> app.name } }
}
