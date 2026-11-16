package org.mobilism.material.updates;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Process;
import java.io.InputStream;
import java.security.MessageDigest;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

// Runs as the test APK's separate UID, without the target app's provider access.
public class InstallerProbeActivity extends Activity {
    public static final String RESULT = "org.mobilism.material.INSTALLER_PROBE_RESULT";
    private static CountDownLatch relayDestroyed;
    private boolean relay;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        relay = getIntent().getBooleanExtra("relay", false);
        if (relay) {
            relayDestroyed = new CountDownLatch(1);
            // Some installers pass the URI internally after finishing their entry activity.
            Intent next = new Intent(this, StagingActivity.class);
            next.putExtras(getIntent());
            next.setDataAndType(getIntent().getData(), getIntent().getType());
            next.setClipData(getIntent().getClipData());
            next.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            next.putExtra("source_uri", getIntent().getDataString());
            startActivity(next);
            finish();
        } else {
            read(this, getIntent().getData(), null);
        }
    }

    @Override protected void onDestroy() {
        super.onDestroy();
        if (relay) relayDestroyed.countDown();
    }

    public static class StagingActivity extends Activity {
        @Override public void onCreate(Bundle state) {
            super.onCreate(state);
            read(this, Uri.parse(getIntent().getStringExtra("source_uri")), relayDestroyed);
        }
    }

    private static void read(Activity activity, Uri uri, CountDownLatch finished) {
        new Thread(() -> {
            Intent result = new Intent(RESULT).setPackage(activity.getIntent().getStringExtra("reply_package"));
            result.putExtra("request_id", activity.getIntent().getStringExtra("request_id"));
            result.putExtra("reader_uid", Process.myUid());
            try {
                if (finished != null) {
                    if (!finished.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("Entry activity stayed open");
                    Thread.sleep(500);
                }
                MessageDigest digest = MessageDigest.getInstance("SHA-256");
                try (InputStream input = activity.getContentResolver().openInputStream(uri)) {
                    if (input == null) throw new IllegalStateException("No APK stream");
                    byte[] buffer = new byte[8192];
                    int count;
                    while ((count = input.read(buffer)) != -1) digest.update(buffer, 0, count);
                }
                result.putExtra("digest", digest.digest());
            } catch (Exception failure) {
                result.putExtra("error", failure.toString());
            }
            activity.sendBroadcast(result);
            activity.runOnUiThread(activity::finish);
        }, "installer-probe").start();
    }
}
