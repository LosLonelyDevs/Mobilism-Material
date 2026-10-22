package org.mobilism.material.ui

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.graphics.drawable.Drawable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class InstalledApp(val packageName: String, val label: String, val version: String, val icon: Drawable, val system: Boolean)
data class InstalledState(val apps: List<InstalledApp> = emptyList(), val busy: Boolean = false, val error: ScreenError? = null)

class LibraryViewModel(context: Context) : ViewModel() {
    private val manager = context.applicationContext.packageManager
    private val ownPackage = context.packageName
    private val mutableInstalled = MutableStateFlow(InstalledState())
    val installed = mutableInstalled.asStateFlow()
    private var installedJob: Job? = null

    fun refreshInstalled() {
        if (installedJob?.isActive == true) return
        installedJob = viewModelScope.launch {
            mutableInstalled.update { it.copy(busy = true, error = null) }
            try {
                val apps = withContext(Dispatchers.IO) {
                    @Suppress("DEPRECATION")
                    manager.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0)
                        .distinctBy { it.activityInfo.packageName }.mapNotNull { activity ->
                            val info = activity.activityInfo.applicationInfo
                            if (info.packageName == ownPackage) return@mapNotNull null
                            runCatching {
                                @Suppress("DEPRECATION")
                                val version = manager.getPackageInfo(info.packageName, 0).versionName.orEmpty()
                                InstalledApp(info.packageName, info.loadLabel(manager).toString(), version, info.loadIcon(manager),
                                    info.flags and ApplicationInfo.FLAG_SYSTEM != 0)
                            }.getOrNull()
                        }.sortedBy { it.label.lowercase() }
                }
                mutableInstalled.value = InstalledState(apps)
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) { mutableInstalled.update { it.copy(busy = false, error = error.forScreen()) } }
        }
    }
}
