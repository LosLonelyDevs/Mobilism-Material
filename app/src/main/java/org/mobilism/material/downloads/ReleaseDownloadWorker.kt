package org.mobilism.material.downloads

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.net.Uri
import android.os.Build
import android.os.SystemClock
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.work.*
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.security.MessageDigest
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.mobilism.material.R
import org.mobilism.material.data.AppUserAgent
import org.mobilism.material.data.KeystoreText
import org.mobilism.material.data.Release
import org.mobilism.material.data.ReleaseTitle
import org.mobilism.material.data.ReleaseTitles
import org.mobilism.material.data.retryAfterMillis

internal fun ReleaseTitle.downloadNotificationTitle(savedName: String? = null): String {
    // Older queued downloads may already have a section-specific name.
    if (!savedName.isNullOrBlank() && savedName != name) return savedName
    if (name.isBlank()) return "Release download"
    val displayName = name.take(120)
    val displayVersion = version.orEmpty().take(80)
    return if (displayVersion.isBlank()) displayName else "$displayName v$displayVersion"
}

internal object ReleaseDownloads {
    const val TAG = "release-download"
    val links = KeystoreText("mobilism-release-downloads-v1")
    val transferSlot = Mutex()
    private val queueLock = Mutex()
    private val completed = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val completions = completed.asSharedFlow()

    fun announceCompletion(context: Context, title: String) {
        val message = "Download complete · $title"
        if (completed.subscriptionCount.value == 0 || !completed.tryEmit(message)) {
            showDownloadToast(context, message)
        }
    }

    suspend fun enqueue(context: Context, original: String, release: Release, folder: String, extract: Boolean,
        directLink: suspend () -> String): Boolean = withContext(Dispatchers.IO) {
        queueLock.withLock {
            val manager = WorkManager.getInstance(context)
            val digest = MessageDigest.getInstance("SHA-256").digest(original.toByteArray())
            val key = TAG + digest.joinToString("") { "%02x".format(it) }
            if (manager.getWorkInfosForUniqueWorkFlow(key).first().any { !it.state.isFinished }) return@withLock false
            ReleaseFolder(context, Uri.parse(folder)).requireWritable()
            val url = directLink()
            val parsed = url.toHttpUrlOrNull()
            if (parsed == null || !parsed.isHttps || parsed.username.isNotEmpty() || parsed.password.isNotEmpty())
                throw DownloadProblem("This link needs a browser.")
            val title = ReleaseTitles.parse(release.title, release.section)
            val request = OneTimeWorkRequestBuilder<ReleaseDownloadWorker>()
                .addTag(TAG)
                .setInputData(workDataOf("link" to links.encrypt(url), "folder" to folder, "extract" to extract,
                    "title" to release.title.take(120), "notificationTitle" to title.downloadNotificationTitle()))
                .build()
            manager.enqueueUniqueWork(key, ExistingWorkPolicy.KEEP, request).await()
            true
        }
    }
}

class ReleaseDownloadWorker internal constructor(context: Context, params: WorkerParameters, private val http: OkHttpClient) :
    CoroutineWorker(context, params) {
    constructor(context: Context, params: WorkerParameters) : this(context, params, releaseFileClient(context))
    private val releaseTitle get() = inputData.getString("title").orEmpty()
    private val notificationTitle by lazy {
        inputData.getString("notificationTitle")
            ?: ReleaseTitles.parse(releaseTitle).downloadNotificationTitle(inputData.getString("releaseName"))
    }
    private val notificationId = id.hashCode() or 1
    private val journal = context.getSharedPreferences("release-download-outputs", Context.MODE_PRIVATE)
    private val created = mutableListOf<Uri>()
    private var lastUpdate = 0L

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val stage = File(applicationContext.cacheDir, "release-$id.part")
        try {
            if (ReleaseDownloads.transferSlot.isLocked) {
                setForeground(notification("Queued", -1))
            }
            ReleaseDownloads.transferSlot.withLock {
                // A stopped worker can be redelivered. Never repeat the host request automatically.
                rollback()
                if (runAttemptCount > 0) throw DownloadProblem("Download interrupted. Tap the release link to try again.")
                val tree = Uri.parse(requireNotNull(inputData.getString("folder")))
                val folder = ReleaseFolder(applicationContext, tree)
                folder.requireWritable()
                setForeground(notification("Downloading", -1))
                val url = ReleaseDownloads.links.decrypt(requireNotNull(inputData.getString("link")))
                val name = fetch(url, stage)
                status("Saving files", -1, true)
                val job = currentCoroutineContext().job
                ReleaseFiles.publish(stage, name, inputData.getBoolean("extract", true), { job.ensureActive() }) { filename, input ->
                    job.ensureActive()
                    val uri = folder.create(filename)
                    created += uri
                    check(journal.edit().putStringSet(id.toString(), created.map { it.toString() }.toSet()).commit())
                    val output = applicationContext.contentResolver.openOutputStream(uri, "w")
                        ?: throw DownloadProblem("Could not write to the selected folder.")
                    output.use { copy(input, it, job) }
                }
                job.ensureActive()
                val target = if (created.size == 1) created.single()
                    else DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
                val message = if (created.size == 1) "Saved to your folder" else "Extracted " + created.size + " files"
                val displayName = if (created.size == 1) runCatching {
                    applicationContext.contentResolver.query(target, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                        ?.use { if (it.moveToFirst()) it.getString(0) else null }
                }.getOrNull()?.take(180) else null
                val result = workDataOf("status" to message, "file" to target.toString(),
                    "name" to displayName, "multiple" to (created.size > 1))
                check(journal.edit().remove(id.toString()).commit())
                created.clear()
                notifyResult(message, target)
                Result.success(result)
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            // Closing a cancelled HTTP stream can surface as an IOException.
            currentCoroutineContext().ensureActive()
            if (isStopped) throw CancellationException("Download stopped")
            val message = when (error) {
                is DownloadProblem -> error.message ?: "Download failed."
                is SecurityException -> "Folder access was lost. Choose the download folder again."
                is java.util.zip.ZipException -> "Invalid ZIP. Turn off extraction to save the original file."
                else -> "Download failed. Check your connection and folder space, then tap the link to retry."
            }
            notifyResult(message)
            Result.failure(workDataOf("status" to message))
        } finally {
            rollback()
            stage.delete()
        }
    }

    private fun rollback() {
        val pending = journal.getStringSet(id.toString(), emptySet()).orEmpty().map(Uri::parse) + created
        val remaining = pending.distinct().filterNot { uri ->
            try { DocumentsContract.deleteDocument(applicationContext.contentResolver, uri) }
            catch (_: java.io.FileNotFoundException) { true }
            catch (_: Exception) { false }
        }
        if (pending.isNotEmpty()) {
            val edit = journal.edit()
            if (remaining.isEmpty()) edit.remove(id.toString())
            else edit.putStringSet(id.toString(), remaining.map { it.toString() }.toSet())
            edit.apply()
        }
        created.clear()
    }

    private suspend fun fetch(url: String, stage: File): String = coroutineScope {
        val call = http.newCall(Request.Builder().url(url)
            .header("User-Agent", AppUserAgent.identity(applicationContext)).build())
        val transfer = async(Dispatchers.IO) {
            val job = currentCoroutineContext().job
            call.execute().use { response ->
                if (response.code != 200) throw DownloadProblem("File host returned HTTP " + response.code + ".")
                val body = response.body ?: throw DownloadProblem("The host returned an empty file.")
                val mime = body.contentType()?.let { it.type + "/" + it.subtype }
                val name = ReleaseFiles.name(response.request.url.toString(), response.header("Content-Disposition"), mime, releaseTitle)
                val source = body.source()
                val preview = source.peek().readByteArray(if (source.request(256)) 256L else source.buffer.size)
                    .toString(Charsets.UTF_8).trimStart('\uFEFF', ' ', '\t', '\n', '\r')
                if (mime == "text/html" || mime == "application/xhtml+xml" || preview.startsWith("<!doctype html", true)
                    || preview.startsWith("<html", true))
                    throw DownloadProblem("The host returned a webpage. Open the original link in your browser.")
                val expected = body.contentLength()
                val bytes = stage.outputStream().use { out -> copy(body.byteStream(), out, job) { n ->
                    status("Downloading", if (expected > 0) (n.toDouble() * 100 / expected).toInt().coerceIn(0, 100) else -1)
                } }
                if (bytes == 0L || (expected >= 0 && bytes != expected)) throw DownloadProblem("The file is incomplete.")
                name
            }
        }
        try { transfer.await() } finally { call.cancel() }
    }

    private fun copy(input: InputStream, output: OutputStream, job: Job, progress: (Long) -> Unit = {}): Long {
        val buffer = ByteArray(64 * 1024)
        var total = 0L
        while (true) {
            job.ensureActive()
            val size = input.read(buffer)
            if (size < 0) return total
            output.write(buffer, 0, size)
            total += size
            progress(total)
        }
    }

    private fun status(text: String, percent: Int, force: Boolean = false) {
        val now = SystemClock.elapsedRealtime()
        if (!force && now - lastUpdate < 1000) return
        lastUpdate = now
        setForegroundAsync(notification(text, percent))
    }

    private fun notificationBuilder(): NotificationCompat.Builder {
        val context = applicationContext
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(ReleaseDownloads.TAG, "Release downloads", NotificationManager.IMPORTANCE_LOW))
        return NotificationCompat.Builder(context, ReleaseDownloads.TAG).setSmallIcon(R.drawable.ic_app_update)
            .setContentTitle(notificationTitle)
    }

    private fun notification(text: String, percent: Int): ForegroundInfo {
        val notification = notificationBuilder()
            .setContentText(if (percent >= 0) "$text · $percent%" else text)
            .setProgress(100, percent.coerceAtLeast(0), percent < 0)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setOnlyAlertOnce(true).setOngoing(true)
            .addAction(0, "Cancel", WorkManager.getInstance(applicationContext).createCancelPendingIntent(id)).build()
        return if (Build.VERSION.SDK_INT >= 29) ForegroundInfo(notificationId, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        else ForegroundInfo(notificationId, notification)
    }

    private fun notifyResult(message: String, target: Uri? = null) {
        val context = applicationContext
        if (target == null) showDownloadToast(context, message, long = true)
        else ReleaseDownloads.announceCompletion(context, notificationTitle)
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED) return
        runCatching {
            val result = notificationBuilder().setContentText(message)
                .setStyle(NotificationCompat.BigTextStyle().bigText(message)).setAutoCancel(true)
                .setSubText(if (target == null) "Download failed" else "Download complete")
            if (target != null) {
                result.setContentIntent(PendingIntent.getActivity(context, notificationId, savedReleaseIntent(context, target),
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT))
            }
            context.getSystemService(NotificationManager::class.java).notify("release-result", notificationId, result.build())
        }
    }

}

internal fun showDownloadToast(context: Context, message: String, long: Boolean = false) {
    val app = context.applicationContext
    ContextCompat.getMainExecutor(app).execute {
        Toast.makeText(app, message, if (long) Toast.LENGTH_LONG else Toast.LENGTH_SHORT).show()
    }
}

internal fun savedReleaseIntent(context: Context, uri: Uri): Intent =
    Intent(Intent.ACTION_VIEW).setDataAndType(uri, context.contentResolver.getType(uri))
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)

internal fun releaseFileClient(context: Context): OkHttpClient {
    val cooldowns = context.getSharedPreferences("release-host-cooldowns", Context.MODE_PRIVATE)
    return OkHttpClient.Builder().retryOnConnectionFailure(false).followSslRedirects(false)
        .addNetworkInterceptor { chain ->
            val request = chain.request()
            val host = request.url.host
            val now = System.currentTimeMillis()
            if (cooldowns.getLong(host, 0L) > now) throw DownloadProblem("The file host asked you to wait before retrying.")
            val response = chain.proceed(request)
            if (response.code >= 400) {
                val delay = retryAfterMillis(response.header("Retry-After"), now)
                if (delay > 0) {
                    val edit = cooldowns.edit()
                    cooldowns.all.filterValues { it is Long && it <= now }.keys.forEach(edit::remove)
                    edit.putLong(host, now + delay.coerceAtMost(Long.MAX_VALUE - now)).apply()
                }
                response.close()
                throw DownloadProblem("File host returned HTTP " + response.code + ". Try again later.")
            }
            response
        }.build()
}
