import com.vanniktech.maven.publish.AndroidSingleVariantLibrary
import com.vanniktech.maven.publish.DeploymentValidation
import com.vanniktech.maven.publish.JavadocJar
import com.vanniktech.maven.publish.SourcesJar

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.maven.publish)
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

// Publishing. Releases go to Maven Central as io.sfere:analytics through the Central Portal
// (`./gradlew publishToMavenCentral`, run by release.yml on a v* tag). The upload is signed
// with GPG; the Portal credentials and the signing key come from Gradle properties
// (mavenCentralUsername / mavenCentralPassword / signingInMemoryKey /
// signingInMemoryKeyPassword), set in CI as ORG_GRADLE_PROJECT_* environment variables and
// never in the repo. `./gradlew publishToMavenLocal` needs none of them: CI's Build job uses
// it to check the publication without touching a registry. Release flow: docs/ci-cd.md.
mavenPublishing {
    // Wait only for the Portal's validation (seconds), the step that can reject a release. A validated
    // deployment is published automatically; that takes Central 5-30 minutes or more, and release.yml
    // does not wait for it.
    publishToMavenCentral(automaticRelease = true, validateDeployment = DeploymentValidation.VALIDATED)
    // Signed whenever the key is supplied, which release.yml checks before publishing (Central
    // rejects unsigned files). Without it, publishToMavenLocal still works for dry runs.
    if (providers.gradleProperty("signingInMemoryKey").isPresent) {
        signAllPublications()
    }
    // One publication: the release AAR with its sources. Central requires a javadoc jar; it is
    // empty until the SDK ships generated API docs.
    configure(
        AndroidSingleVariantLibrary(
            javadocJar = JavadocJar.Empty(),
            sourcesJar = SourcesJar.Sources(),
            variant = "release",
        ),
    )
    coordinates(project.group.toString(), "analytics", project.version.toString())
    pom {
        name.set("Sfere Analytics for Android")
        description.set("Collects first-party fan events in Android apps and sends them to the Sfere ingestion endpoint.")
        url.set("https://github.com/sfere-io/sfere-android-sdk")
        inceptionYear.set("2026")
        licenses {
            license {
                name.set("MIT License")
                url.set("https://opensource.org/license/mit")
                distribution.set("repo")
            }
        }
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
