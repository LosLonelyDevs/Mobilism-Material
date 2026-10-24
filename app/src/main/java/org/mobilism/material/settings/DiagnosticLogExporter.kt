package org.mobilism.material.settings

import android.content.Context
import android.net.Uri
import android.os.Build
import androidx.core.content.FileProvider
import java.io.File
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.mobilism.material.BuildConfig

object DiagnosticLogExporter {
    suspend fun export(context: Context, logs: DiagnosticLogStore): Uri? = withContext(Dispatchers.IO) {
        val snapshot = logs.snapshot().takeIf { it.isNotBlank() } ?: return@withContext null
        val directory = File(context.cacheDir, "shared-logs").apply { mkdirs() }
        directory.listFiles().orEmpty().filter { it.isFile && it.name.startsWith("mobilism-") }
            .sortedByDescending { it.lastModified() }.drop(2).forEach { it.delete() }
        val file = File(directory, "mobilism-${BuildConfig.VERSION_NAME}-${UUID.randomUUID()}.txt")
        file.writeText("Mobilism Material ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})\n" +
            "Android API ${Build.VERSION.SDK_INT}\n\n" + snapshot, Charsets.UTF_8)
        FileProvider.getUriForFile(context, "${context.packageName}.diagnostics", file)
    }
}
