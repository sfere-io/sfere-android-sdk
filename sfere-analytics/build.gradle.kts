plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    `maven-publish`
}

group = providers.gradleProperty("GROUP").get()
version = providers.gradleProperty("VERSION_NAME").get()

android {
    namespace = "io.sfere.analytics"
    compileSdk = 35

    defaultConfig {
        minSdk = 21
        consumerProguardFiles("consumer-rules.pro")
        buildConfigField("String", "SDK_VERSION", "\"${project.version}\"")
        buildConfigField("String", "BASE_LIBRARY", "\"analytics-kotlin@${libs.versions.segment.analytics.get()}\"")
    }

    buildTypes {
        debug {
            // JaCoCo for the JVM tests: `createDebugUnitTestCoverageReport` writes
            // build/reports/coverage/test/debug/report.xml, which CI hands to SonarQube.
            enableUnitTestCoverage = true
        }
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

    lint {
        // Lint is a CI gate (`lintRelease`): every finding is an error.
        abortOnError = true
        warningsAsErrors = true
        checkReleaseBuilds = true
        // Dependabot owns dependency freshness; a new upstream release must not break CI.
        disable += setOf("GradleDependency", "AndroidGradlePluginVersion", "NewerVersionAvailable")
    }

    publishing {
        // One publication, the release AAR with its sources. Javadoc is not shipped yet.
        singleVariant("release") {
            withSourcesJar()
        }
    }
}

kotlin {
    explicitApi()
    jvmToolchain(17)
    compilerOptions {
        allWarningsAsErrors.set(true)
    }
}

dependencies {
    api(libs.segment.analytics.android)
    api(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.core)

    testImplementation(libs.junit)
    testImplementation(libs.okhttp.mockwebserver)
    testImplementation(libs.kotlinx.coroutines.test)
}

// SonarQube: the Gradle scanner reads the Android sources and class files itself; the test
// and coverage reports come from createDebugUnitTestCoverageReport (CI runs it first).
sonar {
    properties {
        property("sonar.junit.reportPaths", "${layout.buildDirectory.get()}/test-results/testDebugUnitTest")
        property("sonar.coverage.jacoco.xmlReportPaths", "${layout.buildDirectory.get()}/reports/coverage/test/debug/report.xml")
        property("sonar.androidLint.reportPaths", "${layout.buildDirectory.get()}/reports/lint-results-release.xml")
        property("sonar.androidVariant", "debug")
    }
}

// Publishing. Apps install from JitPack (jitpack.yml), which runs
// publishReleasePublicationToMavenLocal on a release tag and serves the result as
// com.github.sfere-io:sfere-android-sdk:<tag>, with no credentials. The release workflow also
// publishes io.sfere:analytics to GitHub Packages; Maven Central (same coordinates) comes later.
// GitHub Packages credentials come from Gradle properties (gpr.user / gpr.token, in
// ~/.gradle/gradle.properties, never in the repo) or, in GitHub Actions, from GITHUB_ACTOR /
// GITHUB_TOKEN. They are read only when publishing to that repository, so a build without
// them (JitPack, CI's local publish) is unaffected. Release flow: docs/ci-cd.md.
publishing {
    publications {
        register<MavenPublication>("release") {
            groupId = project.group.toString()
            artifactId = "analytics"
            version = project.version.toString()
            afterEvaluate {
                from(components["release"])
            }
            pom {
                name.set("Sfere Analytics for Android")
                description.set("Collects first-party fan events in Android apps and sends them to the Sfere ingestion endpoint.")
                url.set("https://github.com/sfere-io/sfere-android-sdk")
                organization {
                    name.set("Sfere")
                    url.set("https://sfere.io")
                }
                developers {
                    developer {
                        id.set("sfere")
                        name.set("Sfere")
                        email.set("engineering@sfere.io")
                    }
                }
                scm {
                    url.set("https://github.com/sfere-io/sfere-android-sdk")
                    connection.set("scm:git:https://github.com/sfere-io/sfere-android-sdk.git")
                    developerConnection.set("scm:git:ssh://git@github.com/sfere-io/sfere-android-sdk.git")
                }
            }
        }
    }
    repositories {
        maven {
            name = "GitHubPackages"
            url = uri("https://maven.pkg.github.com/sfere-io/sfere-android-sdk")
            credentials {
                username = providers.gradleProperty("gpr.user")
                    .orElse(providers.environmentVariable("GITHUB_ACTOR"))
                    .orNull
                password = providers.gradleProperty("gpr.token")
                    .orElse(providers.environmentVariable("GITHUB_TOKEN"))
                    .orNull
            }
        }
    }
}
