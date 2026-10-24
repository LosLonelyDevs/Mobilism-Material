package org.mobilism.material

import android.app.Application
import android.content.Context
import java.io.File
import androidx.test.runner.AndroidJUnitRunner

class ReadmeScreenshotRunner : AndroidJUnitRunner() {
    override fun newApplication(cl: ClassLoader, className: String, context: Context): Application =
        // Only the explicit local live-test fixture starts the real application.
        super.newApplication(cl, if (File(context.filesDir, "debrid-live.json").isFile) className
            else Application::class.java.name, context)
}
