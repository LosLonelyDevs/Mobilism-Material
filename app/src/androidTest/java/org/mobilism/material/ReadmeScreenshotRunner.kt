package org.mobilism.material

import android.app.Application
import android.content.Context
import java.io.File
import androidx.test.runner.AndroidJUnitRunner

class ReadmeScreenshotRunner : AndroidJUnitRunner() {
    override fun callApplicationOnCreate(app: Application) {
        org.mobilism.material.data.AppHttp.initialize(app)
        super.callApplicationOnCreate(app)
    }

    override fun newApplication(cl: ClassLoader, className: String, context: Context): Application =
        // Only the explicit local live-test fixture starts the real application.
        super.newApplication(cl, if (File(context.filesDir, "debrid-live.json").isFile) className
            else Application::class.java.name, context)
}
