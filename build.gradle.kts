buildscript {
    dependencies {
        // Raises the Kotlin compiler that AGP's built-in Kotlin support uses.
        classpath(libs.kotlin.gradle.plugin)
    }
}

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.spotless)
}

spotless {
    kotlin {
        target("app/src/**/*.kt")
        ktlint(libs.versions.ktlint.get())
    }
    kotlinGradle {
        target("*.gradle.kts", "app/*.gradle.kts")
        ktlint(libs.versions.ktlint.get())
    }
    format("extension") {
        target("app/src/main/assets/**/*.js", "app/src/main/assets/**/*.css", "app/src/main/assets/**/*.json")
        prettier()
    }
}

// The one command to run before a commit: formatter check, lint, unit tests and the build.
tasks.register("verify") {
    dependsOn("spotlessCheck", ":app:lintDebug", ":app:testDebugUnitTest", ":app:assembleDebug")
}
