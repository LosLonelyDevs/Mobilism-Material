plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "org.mobilism.material.data"
    compileSdk = 37
    defaultConfig { minSdk = 26 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    jvmToolchain(21)
    compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) }
}

dependencies {
    implementation(libs.androidx.core)
    api(libs.coroutines.android)
    implementation(libs.okhttp)
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20250517")
}
