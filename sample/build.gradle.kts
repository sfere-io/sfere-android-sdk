// The Android sample app: calls every v1 event so the SDK can be checked by hand and by UI tests.
// It depends on the SDK module directly, never on a published artifact.
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "io.sfere.sample"
    compileSdk = 35

    defaultConfig {
        applicationId = "io.sfere.sample"
        minSdk = 21
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    lint {
        abortOnError = true
        warningsAsErrors = true
        disable += setOf(
            "GradleDependency", "AndroidGradlePluginVersion", "NewerVersionAvailable",
            // A test tool, not a shipped app: no translations, no icon, no backup rules.
            "SetTextI18n", "MissingApplicationIcon", "DataExtractionRules",
        )
    }
}

kotlin {
    jvmToolchain(17)
    compilerOptions {
        allWarningsAsErrors.set(true)
    }
}

dependencies {
    implementation(project(":sfere-analytics"))
}
