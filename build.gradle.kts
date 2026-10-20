plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.ktlint) apply false
    alias(libs.plugins.detekt) apply false
    // The public API of :sfere-analytics is recorded in sfere-analytics/api/sfere-analytics.api.
    // `apiCheck` fails when the code and the dump disagree; `apiDump` updates the dump.
    alias(libs.plugins.binary.compatibility.validator)
    // SonarQube Cloud analysis: `./gradlew sonar` (CI's SonarQube job, with SONAR_TOKEN).
    // Keys from https://sonarcloud.io/project/configuration/GitHubActions?id=sfere-io_sfere-android-sdk
    alias(libs.plugins.sonarqube)
}

sonar {
    properties {
        property("sonar.projectKey", "sfere-io_sfere-android-sdk")
        property("sonar.organization", "sfere-io")
        property("sonar.host.url", "https://sonarcloud.io")
        property("sonar.sourceEncoding", "UTF-8")
        // Fail the job when the quality gate fails.
        property("sonar.qualitygate.wait", "true")
    }
}

apiValidation {
    // The sample app has no public API to protect.
    ignoredProjects += listOf("sample")
}

subprojects {
    apply(plugin = rootProject.libs.plugins.ktlint.get().pluginId)
    apply(plugin = rootProject.libs.plugins.detekt.get().pluginId)

    configure<org.jlleitschuh.gradle.ktlint.KtlintExtension> {
        android.set(true)
        // The checks only; the pre-commit hook runs ktlintFormat on the staged files.
        ignoreFailures.set(false)
        reporters {
            reporter(org.jlleitschuh.gradle.ktlint.reporter.ReporterType.PLAIN)
            reporter(org.jlleitschuh.gradle.ktlint.reporter.ReporterType.CHECKSTYLE)
        }
    }

    configure<io.gitlab.arturbosch.detekt.extensions.DetektExtension> {
        buildUponDefaultConfig = true
        allRules = false
        config.setFrom(rootProject.file("config/detekt/detekt.yml"))
        source.setFrom("src/main/kotlin", "src/test/kotlin")
    }

    tasks.withType<io.gitlab.arturbosch.detekt.Detekt>().configureEach {
        jvmTarget = "17"
        reports {
            html.required.set(true)
            xml.required.set(true)
            sarif.required.set(true)
            txt.required.set(false)
            md.required.set(false)
        }
    }
}
