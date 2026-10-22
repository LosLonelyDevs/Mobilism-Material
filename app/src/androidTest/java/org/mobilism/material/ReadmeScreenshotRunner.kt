package org.mobilism.material

import android.app.Application
import android.content.Context
import androidx.test.runner.AndroidJUnitRunner

class ReadmeScreenshotRunner : AndroidJUnitRunner() {
    override fun newApplication(cl: ClassLoader, className: String, context: Context): Application =
        // Screenshot tests never start account repositories or background update checks.
        super.newApplication(cl, Application::class.java.name, context)
}
