# CI/CD for the Sfere Android SDK

How the code is protected on the way to `main`, and how a version reaches app
developers. The model is the one `sfere-io/apis` uses, translated from PHP to
Android: every pull request passes the same named checks, `main` is protected by
rulesets with no bypass, Dependabot keeps dependencies and actions current, and a
release is a tag that only an admin can create.

## Workflows

All actions are **pinned by commit SHA** (the `# vX.Y.Z` comment says which
release). Keep it that way when editing a workflow; Dependabot bumps the pins.
Every workflow declares `permissions: contents: read` at the top and widens it only
on the job that needs more.

### `ci.yml` — the quality gate

| Item | Value |
|---|---|
| Triggers | `pull_request` (opened, synchronize, reopened); `push` to `main`; `workflow_call` (from `release.yml`) |
| Runner | `ubuntu-latest`, Temurin JDK 17, Gradle from the wrapper (`gradle/actions/setup-gradle`, wrapper checksum validated, cache written on `main` and read elsewhere) |
| Concurrency | one run per ref; a newer push to a PR cancels the older run |

| Job (check name) | What it runs | Fails when |
|---|---|---|
| **Kotlin style** | `./gradlew ktlintCheck` | any file is not formatted the way `.editorconfig` says (`ktlint_code_style = android_studio`). Fix: `./gradlew ktlintFormat`, or let the pre-commit hook do it |
| **Static analysis** | `./gradlew detekt`, then `:sfere-analytics:lintRelease :sample:lintDebug` | any detekt finding (`config/detekt/detekt.yml`, zero issues allowed, no baseline) or any Android Lint warning (`warningsAsErrors`). Reports are uploaded as the `analysis-reports` artifact |
| **API contract** | vendor-name grep over every tracked file; `:sfere-analytics:apiCheck`; on PRs the public API diff against the base goes into the job summary; the version and CHANGELOG rules | the ingestion vendor is named anywhere; the committed `sfere-analytics/api/sfere-analytics.api` is stale (fix: `./gradlew :sfere-analytics:apiDump`, the diff is in the summary); the SDK changed while `VERSION_NAME` is already tagged; the SDK changed without a `CHANGELOG.md` change (label `skip-changelog` to waive) |
| **Tests & coverage** | `:sfere-analytics:createDebugUnitTestCoverageReport` (runs `testDebugUnitTest`, then JaCoCo) | a test fails. The summary shows the counts and the line/branch coverage; results and the JaCoCo XML are the `test-results` artifact |
| **SonarQube** | restores `test-results`, runs `./gradlew sonar` (the SonarQube Gradle scanner; keys and properties in `build.gradle.kts` and `sfere-analytics/build.gradle.kts`, the sample skipped) | the quality gate fails (`sonar.qualitygate.wait=true`). **Skipped with a note when `SONAR_TOKEN` is not set** (Dependabot runs), so the required check still passes |
| **Build** | `:sfere-analytics:assembleRelease :sample:assembleDebug`, then `publishReleasePublicationToMavenLocal` and an inspection of the published files | the AAR, POM, sources jar or Gradle module file is missing from `~/.m2`, the AAR has no `classes.jar` / `io.sfere.analytics.Sfere` / consumer ProGuard rules, or the POM does not carry the base library as a dependency. The AAR and the sample APK are the `sfere-analytics` artifact |

### `dependency-graph.yml`

On every push to `main`, `gradle/actions/dependency-submission` resolves the
dependencies of `:sfere-analytics` and `:sample` and submits them to GitHub. This is
what gives Dependabot alerts the real resolved graph rather than only the names
in `gradle/libs.versions.toml`. Needs `contents: write` on that one job.
GitHub's pull-request dependency-review action is not used: on a private
repository it needs GitHub Advanced Security, which this plan does not include.

### `release.yml` — publish and release

| Item | Value |
|---|---|
| Trigger | `push` of a tag matching `v*` |
| Gate | the whole `ci.yml` first (`workflow_call`), so a release never ships bytes that did not pass every PR check |
| Environment | `release` (deployment record; accepts only `v*` refs) |
| Permissions | `contents: write` (the GitHub release), `packages: write` (GitHub Packages) on the publish job only |

Steps, in order:

1. The tag is `vX.Y.Z` or `vX.Y.Z-pre.N`, equals `VERSION_NAME` in `gradle.properties`,
   points at a commit on `main`, and `CHANGELOG.md` has a `## [X.Y.Z]` section.
2. `./gradlew :sfere-analytics:publishReleasePublicationToGitHubPackagesRepository`
   with the Actions token. GitHub Packages refuses a version that already exists,
   so a version is published once.
3. The AAR, POM, sources jar and module file are downloaded back from
   `maven.pkg.github.com` and the AAR's sha256 must equal the one just built; the
   classes and the base-library dependency are checked again on the registry copy.
4. `gh release create vX.Y.Z` with the CHANGELOG section as notes (plus the install
   snippet and the AAR's sha256), the registry's AAR, sources jar and POM attached,
   and `--prerelease` when the version has a `-` suffix.

## Releasing

1. On a branch: make sure `VERSION_NAME` in `gradle.properties` is the version to
   ship and is not tagged yet (CI enforces this on every SDK change). Rename
   `## [Unreleased]` in `CHANGELOG.md` to `## [X.Y.Z] - YYYY-MM-DD`, and add a new
   empty `## [Unreleased]` above it. Open the PR, get it merged.
2. An admin tags the merge commit on `main` and pushes the tag:

   ```sh
   git switch main && git pull
   git tag vX.Y.Z
   git -c credential.helper='!gh auth git-credential' push origin vX.Y.Z
   ```

   Only admins can create `v*` tags (the `release-tags` ruleset), and no one can
   move or delete a tag (`protect-tags`).
3. Watch the `release` run. When it is green, the package is at
   https://github.com/sfere-io/sfere-android-sdk/packages and the release at
   https://github.com/sfere-io/sfere-android-sdk/releases.
4. If anything after the publish step failed, the version is on the registry
   already: fix forward with a new `VERSION_NAME`, never by re-tagging.

Beta versions are `X.Y.Z-beta.N` (`0.1.0-beta.1` first). At 1.0.0 the same
publication moves to Maven Central under the same coordinates; the POM already
carries the name, description, URL, organization, developers and SCM blocks Central
requires, and will need a licence block then.

## Consuming the package

During the beta the artifact is private on GitHub Packages. An app needs a GitHub
account with read access to this repository and a classic personal access token
with the `read:packages` scope. The token goes in the developer's
`~/.gradle/gradle.properties`, never in the app repository:

```properties
gpr.user=<github login>
gpr.token=<token with read:packages>
```

Then, in the app's `settings.gradle.kts` and module `build.gradle.kts`, the
snippet in the README's **Install** section. CI systems building the app use a
service account's token the same way (as environment variables mapped to those
Gradle properties).

## Branch and tag protection

Repository rulesets, all with **no bypass actors** (admins included):

| Ruleset | Target | Rules |
|---|---|---|
| `protect-main` | the default branch | no deletion, no force-push, linear history; pull request required with 1 approval, code-owner review, stale approvals dismissed on push, last push approved by someone else, all threads resolved, squash merge only; required checks `Kotlin style`, `Static analysis`, `API contract`, `Tests & coverage`, `SonarQube`, `Build`, strict (branch up to date) |
| `protect-tags` | all tags | no deletion, no force-push (a tag never moves) |
| `release-tags` | `v*` tags | creation restricted; bypass: repository admins (creating a release tag is the privileged act) |

Repository settings that go with them: squash merge only, branches deleted on
merge, Dependabot alerts and security updates on, Actions limited to GitHub-owned
and verified creators plus `gradle/*` and `actions/*`, default `GITHUB_TOKEN`
read-only, Actions may not create or approve pull requests.

`.github/CODEOWNERS` lists the sensitive paths (workflows, hooks, Gradle files, the
API dump, the shared fixtures, the Sonar and detekt configs, the policy docs); a
maintainer must approve changes to them.

## Dependency verification

`gradle/verification-metadata.xml` holds a sha256 checksum for every dependency,
plugin and build tool Gradle downloads. Gradle refuses an artifact whose checksum
is missing or different, on every machine and in CI, so a tampered or substituted
download cannot enter a build (this is also what SonarQube's `kotlin:S6474` and
`text:S8569` rules ask for). Consequences:

- A dependency bump, Dependabot's included, fails every Gradle job until the file
  is regenerated. On the PR branch run the command below and commit the file; the
  diff shows exactly which artifacts changed.
- Regenerate with the full task set CI runs, so no configuration is left out:

  ```sh
  ./gradlew --write-verification-metadata sha256 ktlintCheck detekt :sfere-analytics:apiCheck \
    :sfere-analytics:lintRelease :sample:lintDebug :sfere-analytics:createDebugUnitTestCoverageReport \
    :sfere-analytics:assembleRelease :sample:assembleDebug \
    :sfere-analytics:publishReleasePublicationToMavenLocal :sfere-analytics:sonarResolver
  ```

  Review the diff before committing: a new entry must be a dependency you meant to
  add or upgrade.
- Platform-specific build tools (the AAPT2 binaries) are trusted by pattern in the
  `<trusted-artifacts>` block, because a Mac resolves a different file from the
  Linux runner. Everything else is pinned.

## Local counterparts

| Gate | Locally |
|---|---|
| Everything CI runs | `./gradlew ktlintCheck detekt :sfere-analytics:apiCheck :sfere-analytics:lintRelease :sample:lintDebug :sfere-analytics:testDebugUnitTest :sfere-analytics:assembleRelease :sample:assembleDebug` (VS Code task **Android: quality checks**) |
| Formatting | `./gradlew ktlintFormat` |
| Public API dump | `./gradlew :sfere-analytics:apiDump` after an intentional API change; review the diff of `sfere-analytics/api/sfere-analytics.api` |
| Coverage report | `./gradlew :sfere-analytics:createDebugUnitTestCoverageReport`, then `sfere-analytics/build/reports/coverage/test/debug/index.html` |
| Publish dry run | `./gradlew :sfere-analytics:publishReleasePublicationToMavenLocal`, then look in `~/.m2/repository/io/sfere/analytics/` |
| Pre-commit hook | `git config core.hooksPath .githooks` once per clone: ktlint on the staged Kotlin, `apiDump` when the SDK sources are staged, and the vendor-name check |

JDK 17 is required (`jvmToolchain(17)`). On this project's Macs:
`JAVA_HOME=$(/usr/libexec/java_home -v 17)`; elsewhere point `JAVA_HOME` at any JDK 17.
`local.properties` or `ANDROID_HOME` must point at an Android SDK with API 35.

## Secrets and variables

| Name | Where | Used by |
|---|---|---|
| `SONAR_TOKEN` | repository secret (set 2026-10-20) | the `SonarQube` job. SonarQube Cloud project `sfere-io_sfere-android-sdk` in organization `sfere-io`: https://sonarcloud.io/project/overview?id=sfere-io_sfere-android-sdk |
| `GITHUB_TOKEN` | automatic | publishing to GitHub Packages, the GitHub release, the dependency submission |

No deploy keys, no personal tokens, no cloud credentials: the SDK ships to GitHub
Packages, not to a server.

## Not yet in CI

- **Cross-repo parity with the iOS SDK.** `tools/parity.sh` needs both repositories
  side by side and a Mac with Xcode (plan step F1). Until a macOS job with access to
  `sfere-io/sfere-ios-sdk` exists, run it locally before merging a change to what
  the SDK sends.
- **Emulator flows (Espresso).** There are no `androidTest` sources yet (plan step
  D3). When they exist, add an emulator job on `main` or nightly, as decision Q7 says.
