plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

val signingPath = providers.environmentVariable("KEYSTORE_FILE").orNull
val readme = providers.fileContents(rootProject.layout.projectDirectory.file("README.md")).asText.get()
val changelog = providers.fileContents(rootProject.layout.projectDirectory.file("CHANGELOG.md")).asText.get().replace("\r\n", "\n")

fun String.buildConfigString() = "\"" + replace("\\", "\\\\").replace("\"", "\\\"")
    .replace("\r", "").replace("\n", "\\n") + "\""

android {
    namespace = "org.mobilism.material"
    compileSdk = 37

    defaultConfig {
        // A separate identity during migration protects the legacy app's data.
        applicationId = "cum.ahfu.mobi.material"
        minSdk = 26
        targetSdk = 36
        versionCode = 17
        versionName = "0.1.16"
        val releaseNotes = changelog.substringAfter("## $versionName\n", "")
            .substringBefore("\n## ").trim()
        check(releaseNotes.isNotBlank()) { "Add CHANGELOG.md notes for $versionName" }
        buildConfigField("String", "ABOUT_DESCRIPTION", readme.trim().split(Regex("\\r?\\n\\s*\\r?\\n"))[1].buildConfigString())
        buildConfigField("String", "RELEASE_NOTES", releaseNotes.buildConfigString())
        testInstrumentationRunner = "org.mobilism.material.ReadmeScreenshotRunner"
    }

    signingConfigs {
        if (!signingPath.isNullOrBlank()) create("release") {
            storeFile = rootProject.file(signingPath)
            storePassword = providers.environmentVariable("KEYSTORE_PASSWORD").get()
            keyAlias = providers.environmentVariable("KEY_ALIAS").get()
            keyPassword = providers.environmentVariable("KEY_PASSWORD").get()
        }
    }
    buildTypes {
        release {
            signingConfig = signingConfigs.findByName("release")
        }
    }

    buildFeatures { compose = true; buildConfig = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    packaging { resources.excludes += "/META-INF/{AL2.0,LGPL2.1}" }
}

base { archivesName.set("MobilismMaterial_v${android.defaultConfig.versionName}") }

androidComponents {
    onVariants(selector().all()) { variant ->
        val variantName = variant.name.replaceFirstChar { it.uppercase() }
        val copyTask = tasks.register<Copy>("copy${variantName}Apk") {
            group = "distribution"
            from(variant.artifacts.get(com.android.build.api.artifact.SingleArtifact.APK)) {
                include("*.apk")
            }
            into(rootProject.layout.buildDirectory.dir("apk"))
        }
        tasks.matching { it.name == "assemble$variantName" }.configureEach {
            finalizedBy(copyTask)
        }
    }
}

kotlin {
    jvmToolchain(21)
    compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) }
}

tasks.withType<org.gradle.api.tasks.testing.Test>().configureEach {
    val changelogFile = rootProject.layout.projectDirectory.file("CHANGELOG.md")
    inputs.file(changelogFile).withPathSensitivity(org.gradle.api.tasks.PathSensitivity.RELATIVE)
    systemProperty("mobilism.changelog.path", changelogFile.asFile.absolutePath)
}

dependencies {
    implementation(project(":core:data"))
    implementation(project(":core:designsystem"))
    implementation(platform(libs.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.work)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.compose.ui)
    implementation(libs.compose.foundation)
    implementation(libs.compose.animation)
    implementation(libs.compose.material3)
    implementation(libs.compose.icons)
    implementation(libs.coil.compose)
    implementation(libs.coil.network)
    implementation(libs.jsoup)
    debugImplementation(libs.compose.tooling)
    debugImplementation("androidx.compose.ui:ui-test-manifest")
    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    androidTestImplementation("androidx.test:runner:1.7.0")
    androidTestImplementation("androidx.test.ext:junit:1.3.0")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.7.0")
    androidTestImplementation(libs.okhttp)
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2")
}
