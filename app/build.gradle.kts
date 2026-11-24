import java.net.URI
import java.util.Base64

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

val verifyAppHttp by tasks.registering {
    val sources = rootProject.subprojects.map { module ->
        module.fileTree("src") {
            include("**/*.kt", "**/*.java")
            exclude("test*/**", "androidTest*/**", "testFixtures/**")
        }
    }
    val factory = rootProject.file("core/data/src/main/java/org/mobilism/material/data/AppHttp.kt")
    inputs.files(sources)
    doLast {
        val bypass = Regex("""\bOkHttpClient\s*(?:\.\s*Builder\s*)?\(|\bWebView\s*\(|::WebView\b|\.\s*(?:openConnection|openStream)\s*\(|\bJsoup\.connect\s*\(|\buserAgentString\s*=|["']User-Agent["']""")
        val sharedConnection = Regex("""\bAppHttp\s*\.\s*openConnection\s*\(""")
        val offenders = inputs.files.filter { it != factory }.filter { file ->
            bypass.containsMatchIn(file.readText().replace(sharedConnection, ""))
        }
        check(offenders.isEmpty()) {
            "Use AppHttp for network clients, URL connections, WebViews and User-Agent headers: " +
                offenders.joinToString { it.relativeTo(rootProject.projectDir).path }
        }
    }
}
tasks.named("preBuild") { dependsOn(verifyAppHttp) }

val signingPath = providers.environmentVariable("KEYSTORE_FILE").orNull
val dailyUsageEndpoint = providers.environmentVariable("DAILY_USAGE_ENDPOINT").orElse(providers.provider {
    val localConfig = rootProject.file(".env")
    if (localConfig.isFile) localConfig.useLines { lines ->
        lines.firstOrNull { it.substringBefore('=').trim() == "DAILY_USAGE_ENDPOINT" }
            ?.substringAfter('=')?.trim()?.removeSurrounding("\"")?.removeSurrounding("'").orEmpty()
    } else ""
}).get().trim()
if (dailyUsageEndpoint.isNotEmpty()) {
    val endpoint = URI(dailyUsageEndpoint)
    require(endpoint.scheme == "https" && !endpoint.host.isNullOrBlank() && endpoint.userInfo == null &&
        endpoint.query == null && endpoint.fragment == null) { "DAILY_USAGE_ENDPOINT must be an HTTPS URL without credentials or query parameters" }
}
val readme = providers.fileContents(rootProject.layout.projectDirectory.file("README.md")).asText.get()
val changelog = providers.fileContents(rootProject.layout.projectDirectory.file("CHANGELOG.md")).asText.get().replace("\r\n", "\n")
val offlineFormattingPreview = providers.gradleProperty("offlineFormattingPreview").orNull == "true"

fun String.buildConfigString() = "\"" + replace("\\", "\\\\").replace("\"", "\\\"")
    .replace("\r", "").replace("\n", "\\n") + "\""

android {
    namespace = "org.mobilism.material"
    compileSdk {
        version = release(37) { minorApiLevel = 2 }
    }

    defaultConfig {
        // A separate identity during migration protects the legacy app's data.
        applicationId = "cum.ahfu.mobi.material"
        minSdk = 26
        targetSdk = 36
        versionCode = 29
        versionName = "0.1.28"
        val encodedEndpoint = Base64.getEncoder().encodeToString(dailyUsageEndpoint.toByteArray(Charsets.UTF_8))
        buildConfigField("String", "DAILY_USAGE_ENDPOINT",
            "new String(java.util.Base64.getDecoder().decode(${encodedEndpoint.buildConfigString()}), java.nio.charset.StandardCharsets.UTF_8)")
        val releaseNotes = changelog.substringAfter("## $versionName\n", "")
            .substringBefore("\n## ").trim()
        check(releaseNotes.isNotBlank()) { "Add CHANGELOG.md notes for $versionName" }
        buildConfigField("String", "ABOUT_DESCRIPTION", readme.trim().split(Regex("\\r?\\n\\s*\\r?\\n"))[1].buildConfigString())
        testInstrumentationRunner = if (providers.gradleProperty("liveVerificationProbe").orNull == "true")
            "org.mobilism.material.LiveVerificationProbeRunner" else "org.mobilism.material.ReadmeScreenshotRunner"
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
        debug {
            if (offlineFormattingPreview) {
                applicationIdSuffix = ".formattingpreview"
            }
        }
        release {
            signingConfig = signingConfigs.findByName("release")
        }
    }

    if (offlineFormattingPreview && providers.gradleProperty("offlineFixtureRunner").orNull == "true") {
        sourceSets.getByName("debug").manifest.srcFile("src/formattingPreview/AndroidManifest.xml")
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
    implementation(libs.androidx.webkit)
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
    implementation(libs.coil.gif)
    implementation(libs.jsoup)
    implementation(libs.commonmark)
    implementation(libs.libarchive)
    implementation(libs.okhttp)
    debugImplementation(libs.compose.tooling)
    debugImplementation("androidx.compose.ui:ui-test-manifest")
    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    androidTestImplementation("androidx.test:runner:1.7.0")
    androidTestImplementation("androidx.test.ext:junit:1.3.0")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.7.0")
    androidTestImplementation(libs.okhttp)
    androidTestImplementation("androidx.work:work-testing:${libs.versions.work.get()}")
    androidTestImplementation("com.squareup.okhttp3:mockwebserver:${libs.versions.okhttp.get()}")
    androidTestImplementation("com.squareup.okhttp3:okhttp-tls:${libs.versions.okhttp.get()}")
    androidTestImplementation("androidx.test.uiautomator:uiautomator:2.2.0")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20250517")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2")
}
