package org.mobilism.material.updates

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Process
import androidx.core.content.ContextCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class UpdateInstallerHandoffTest {
    @Test fun installerCanReadTheUpdateFromItsOwnProcess() = readFromInstaller(relay = false)

    @Test fun installerCanKeepStagingAfterItsEntryActivityFinishes() = readFromInstaller(relay = true)

    @Test fun installerCannotReadThePrivateApkWithoutAReadGrant() = readFromInstaller(relay = false, grant = false)

    private fun readFromInstaller(relay: Boolean, grant: Boolean = true) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val requestId = UUID.randomUUID().toString()
        val content = "Verified APK fixture $requestId".toByteArray()
        val file = File(context.cacheDir, "updates/$requestId.apk").apply { parentFile?.mkdirs(); writeBytes(content) }
        val results = LinkedBlockingQueue<Intent>()
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                if (intent.getStringExtra("request_id") == requestId) results.offer(intent)
            }
        }
        ContextCompat.registerReceiver(context, receiver, IntentFilter(InstallerProbeActivity.RESULT), ContextCompat.RECEIVER_EXPORTED)
        val intent = updateInstallIntent(context, file).apply {
            component = ComponentName(instrumentation.context.packageName, InstallerProbeActivity::class.java.name)
            putExtra("reply_package", context.packageName)
            putExtra("request_id", requestId)
            putExtra("relay", relay)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (!grant) {
                removeFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                clipData = null
            }
        }
        try {
            context.startActivity(intent)
            val result = results.poll(30, TimeUnit.SECONDS)
            assertNotNull("Installer did not report a result", result)
            assertNotEquals(Process.myUid(), result!!.getIntExtra("reader_uid", -1))
            if (grant) {
                assertNull(result.getStringExtra("error"), result.getStringExtra("error"))
                assertArrayEquals(MessageDigest.getInstance("SHA-256").digest(content), result.getByteArrayExtra("digest"))
            } else {
                assertTrue(result.getStringExtra("error"), result.getStringExtra("error").orEmpty().contains("SecurityException"))
                assertNull(result.getByteArrayExtra("digest"))
            }
        } finally {
            context.unregisterReceiver(receiver)
            context.revokeUriPermission(intent.data!!, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            file.delete()
        }
    }
}
