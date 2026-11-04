package org.mobilism.material.updates

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import java.io.File
import java.security.MessageDigest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.mobilism.material.data.AppRelease
import org.mobilism.material.data.ReleaseApk

internal enum class UpdatePhase { Idle, Downloading, Verifying, Ready, Cancelling }

internal data class UpdateInstallState(
    val phase: UpdatePhase = UpdatePhase.Idle,
    val release: AppRelease? = null,
    val progress: Int = 0,
    val message: String? = null,
    val installRequested: Boolean = false,
) {
    val busy: Boolean get() = phase in listOf(UpdatePhase.Downloading, UpdatePhase.Verifying, UpdatePhase.Cancelling)
}

// Owned by the activity so navigation and rotation keep the same download.
internal class AppUpdateViewModel(
    private val apkFile: File,
    private val validate: (File, AppRelease) -> Unit,
    private val download: suspend (ReleaseApk, File, (Int) -> Unit) -> Unit = ::downloadUpdateApk,
) : ViewModel() {
    constructor(context: Context) : this(File(context.cacheDir, "updates/update.apk"),
        { file, release -> validateUpdatePackage(context.applicationContext, file, release) })

    private val mutable = MutableStateFlow(UpdateInstallState())
    val state = mutable.asStateFlow()
    private var work: Job? = null

    fun downloadAndInstall(release: AppRelease) {
        if (work?.isActive == true || state.value.busy) return
        val apk = release.apk ?: run { notice("This release's download is not ready. Check again later."); return }
        if (state.value.phase == UpdatePhase.Ready && state.value.release == release) {
            requestInstall()
            return
        }
        mutable.value = UpdateInstallState(UpdatePhase.Downloading, release, message = "Downloading version ${release.version}...")
        work = viewModelScope.launch {
            try {
                val cached = withContext(Dispatchers.IO) {
                    try { verifyFile(release); true }
                    catch (cancelled: CancellationException) { throw cancelled }
                    catch (_: Exception) { false }
                }
                if (!cached) download(apk, apkFile) { progress -> mutable.update { it.copy(progress = progress) } }
                mutable.update { it.copy(phase = UpdatePhase.Verifying, message = "Verifying the update...") }
                withContext(Dispatchers.IO) { verifyFile(release) }
                mutable.update { it.copy(phase = UpdatePhase.Ready, progress = 100,
                    message = "Download verified. Ready to install.", installRequested = true) }
            } catch (cancelled: CancellationException) {
                apkFile.delete()
                mutable.update { it.copy(phase = UpdatePhase.Idle, installRequested = false, message = "Update canceled. You can try again.") }
                throw cancelled
            } catch (failure: Exception) {
                apkFile.delete()
                mutable.update { it.copy(phase = UpdatePhase.Idle, installRequested = false,
                    message = failure.message ?: "The download failed. Try again.") }
            }
        }
    }

    fun cancel() {
        if (work?.isActive != true || state.value.phase != UpdatePhase.Downloading) return
        mutable.update { it.copy(phase = UpdatePhase.Cancelling, installRequested = false, message = "Canceling download...") }
        // Wait for partial-file cleanup before allowing another writer.
        work?.cancel()
    }

    fun notice(message: String) { mutable.update { it.copy(message = message) } }

    fun requestInstall() {
        if (state.value.phase == UpdatePhase.Ready) mutable.update { it.copy(installRequested = true) }
    }

    fun consumeInstallRequest(): Boolean {
        if (state.value.phase != UpdatePhase.Ready || !state.value.installRequested) return false
        mutable.update { it.copy(installRequested = false) }
        return true
    }

    suspend fun fileForInstall(): File? {
        val release = state.value.release ?: return null
        if (state.value.phase != UpdatePhase.Ready) return null
        mutable.update { it.copy(phase = UpdatePhase.Verifying, message = "Verifying the saved update...") }
        return try {
            withContext(Dispatchers.IO) { verifyFile(release) }
            apkFile
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            apkFile.delete()
            mutable.update { it.copy(phase = UpdatePhase.Idle,
                message = failure.message ?: "Download the update again before installing.") }
            null
        } finally {
            mutable.update { if (it.phase == UpdatePhase.Verifying) it.copy(phase = UpdatePhase.Ready) else it }
        }
    }

    private suspend fun verifyFile(release: AppRelease) {
        val apk = requireNotNull(release.apk)
        require(apkFile.isFile && apkFile.length() == apk.size) { "Download the update again before installing." }
        apk.sha256?.let { expected ->
            val digest = MessageDigest.getInstance("SHA-256")
            apkFile.inputStream().use { input ->
                val buffer = ByteArray(64 * 1024)
                while (true) {
                    kotlin.coroutines.coroutineContext.ensureActive()
                    val count = input.read(buffer)
                    if (count == -1) break
                    digest.update(buffer, 0, count)
                }
            }
            require(digest.digest().joinToString("") { "%02x".format(it) }.equals(expected, ignoreCase = true)) {
                "The saved update failed its integrity check. Download it again."
            }
        }
        validate(apkFile, release)
    }
}
