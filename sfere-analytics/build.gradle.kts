plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
}

group = "io.sfere"
version = "0.1.0"

android {
    namespace = "io.sfere.analytics"
    compileSdk = 35

    defaultConfig {
        minSdk = 21
        consumerProguardFiles("consumer-rules.pro")
        buildConfigField("String", "SDK_VERSION", "\"${project.version}\"")
        buildConfigField("String", "BASE_LIBRARY", "\"analytics-kotlin@${libs.versions.segment.analytics.get()}\"")
    }

    buildFeatures {
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    testOptions {
        unitTests {
            isReturnDefaultValues = true
            all {
                // The fixtures are shared with the iOS SDK: sfere-ios-sdk has an identical copy, and tools/parity.sh checks they match.
                it.systemProperty("sfere.fixtures.dir", rootProject.projectDir.resolve("fixtures").canonicalPath)
            }
        }
    }
}

kotlin {
    explicitApi()
    jvmToolchain(17)
}

dependencies {
    api(libs.segment.analytics.android)
    api(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.core)

    testImplementation(libs.junit)
    testImplementation(libs.okhttp.mockwebserver)
    testImplementation(libs.kotlinx.coroutines.test)
}
