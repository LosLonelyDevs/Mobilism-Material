package org.mobilism.material

import android.app.Application
import org.mobilism.material.data.DefaultMobilismRepository
import org.mobilism.material.settings.AppearanceStore

class MobilismApplication : Application() {
    val repository by lazy { DefaultMobilismRepository(this) }
    val appearance by lazy { AppearanceStore(this) }
}
