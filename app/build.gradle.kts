plugins {
    alias(libs.plugins.android.application)
}

val appVersion: String = providers.gradleProperty("appVersion").get()
val (major, minor, patch) = appVersion.split(".").map(String::toInt)

// The devices this app is for are arm64. An emulator on a PC takes -Pabi=x86_64.
val abi: String = providers.gradleProperty("abi").getOrElse("arm64-v8a")

android {
    namespace = "dev.miyabisun.subfeedbrowser"
    compileSdk {
        version = release(37) { minorApiLevel = 2 }
    }
    defaultConfig {
        applicationId = "dev.miyabisun.subfeedbrowser"
        minSdk = 36
        targetSdk = 37
        versionCode = major * 1_000_000 + minor * 1_000 + patch
        versionName = appVersion
        ndk { abiFilters += abi }
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    buildTypes {
        release {
            // Releases are built by hand and signed with the builder's debug key: no key lives in
            // this repository or in CI. Unlike a debug build they cannot be inspected over adb.
            signingConfig = signingConfigs.getByName("debug")
        }
    }
    lint {
        warningsAsErrors = true
        // Newer versions of dependencies are taken up on purpose, not on every lint run.
        disable += setOf("GradleDependency", "NewerVersionAvailable", "AndroidGradlePluginVersion")
        // The app is built for arm64 phones and tablets only; the engine is 90 MB per ABI.
        disable += "ChromeOsAbiSupport"
    }
    buildFeatures { buildConfig = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation(libs.geckoview.map { "${it.module.group}:geckoview-$abi:${it.version}" })
    implementation(libs.androidx.swiperefreshlayout)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.androidx.test.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.mockwebserver)
}
