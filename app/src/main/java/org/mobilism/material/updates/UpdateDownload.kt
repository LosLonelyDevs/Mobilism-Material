package org.mobilism.material.updates

import java.io.File
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.mobilism.material.data.AppReleaseUpdates
import org.mobilism.material.data.MAX_UPDATE_APK_BYTES
import org.mobilism.material.data.ReleaseApk

internal fun isUpdateDownloadUrl(url: URL, initial: URL): Boolean =
    url.protocol == "https" && url.userInfo == null && url.port in listOf(-1, 443) &&
        ((url.host == initial.host && url.path == initial.path) || url.host == "release-assets.githubusercontent.com")

// Ported from GAYERFyTV: bound redirects, file size, and memory before exposing an APK.
internal suspend fun downloadUpdateApk(apk: ReleaseApk, destination: File, onProgress: (Int) -> Unit) =
    withContext(Dispatchers.IO) {
        require(apk.url.startsWith(AppReleaseUpdates.REPOSITORY_URL + "/releases/download/")) {
            "The update download is not from this app's repository."
        }
        val initial = URL(apk.url)
        var url = initial
        repeat(6) {
            coroutineContext.ensureActive()
            require(isUpdateDownloadUrl(url, initial)) { "The update server returned an unsafe download address." }
            val connection = (url.openConnection() as HttpURLConnection).apply {
                instanceFollowRedirects = false
                connectTimeout = 15_000
                readTimeout = 15_000
                setRequestProperty("Accept", "application/vnd.android.package-archive, application/octet-stream")
                setRequestProperty("Accept-Encoding", "identity")
            }
            try {
                val status = connection.responseCode
                if (status in listOf(301, 302, 303, 307, 308)) {
                    url = URL(url, connection.getHeaderField("Location") ?: throw IOException("No update download was returned."))
                } else {
                    if (status != 200) throw IOException("Download failed (HTTP $status). Try again.")
                    val length = connection.getHeaderField("Content-Length")?.toLongOrNull() ?: -1L
                    if (length != -1L && length != apk.size) throw IOException("The download size does not match the release.")
                    connection.inputStream.use { input ->
                        writeUpdateApk(input, destination, apk, onProgress) { coroutineContext.ensureActive() }
                    }
                    return@withContext
                }
            } finally {
                connection.disconnect()
            }
        }
        throw IOException("The download redirected too many times. Try again.")
    }

internal fun writeUpdateApk(input: InputStream, destination: File, apk: ReleaseApk,
    onProgress: (Int) -> Unit = {}, checkCancelled: () -> Unit = {}) {
    require(apk.size in 1..MAX_UPDATE_APK_BYTES) { "Invalid update size." }
    val partial = File(destination.parentFile, "${destination.name}.part")
    destination.parentFile?.mkdirs()
    val digest = MessageDigest.getInstance("SHA-256")
    var total = 0L
    var percent = -1
    try {
        partial.outputStream().use { output ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                checkCancelled()
                val count = input.read(buffer)
                if (count == -1) break
                total += count
                if (total > apk.size) throw IOException("The download is larger than the published APK.")
                output.write(buffer, 0, count)
                digest.update(buffer, 0, count)
                val next = (total * 100 / apk.size).toInt()
                if (next != percent) { percent = next; onProgress(percent) }
            }
        }
        checkCancelled()
        if (total != apk.size) throw IOException("The download was interrupted. Try again.")
        val actual = digest.digest().joinToString("") { "%02x".format(it) }
        if (apk.sha256 != null && !actual.equals(apk.sha256, ignoreCase = true))
            throw IOException("The downloaded APK failed its integrity check. Try again.")
        if (destination.exists() && !destination.delete()) throw IOException("Could not replace the previous update.")
        if (!partial.renameTo(destination)) throw IOException("Could not save the downloaded update.")
    } finally {
        partial.delete()
    }
}
