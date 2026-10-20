# Sfere Android SDK

The Sfere Android SDK. It collects first-party fan events in Android apps and sends them to the Sfere ingestion endpoint. It wraps Segment's open-source `analytics-kotlin` unmodified, the same library the ingestion platform uses. Sfere's behaviour is added on top as plugins.

- **Sibling repo:** the iOS SDK is `fanfinity/sfere-ios-sdk`, checked out at `~/Desktop/Projects/sfere-ios-sdk`. Until 2026-10-18 both lived in one repo, `fanfinity/sfere-mobile-sdks`, which is now deleted from GitHub. The two were split because apps integrate them differently. The old local folder `~/Desktop/Projects/sfere-mobile-sdks` was deleted too.
- **Payload contract:** `docs/WIRE-CONTRACT.md` and `docs/EVENTS.md` in the private repo `fanfinity/sfere-sdk`. Read them with `gh api 'repos/fanfinity/sfere-sdk/contents/<path>' -H 'Accept: application/vnd.github.raw'`; `gh repo clone` fails on this machine.
- **Plan and next steps:** `docs/mobile/sfere-mobile-sdks-plan.html` and `docs/mobile/sfere-mobile-sdks-next-steps.html` in `fanfinity/sfere-sdk`. They cover both platforms. Read them with `gh api 'repos/fanfinity/sfere-sdk/contents/docs/mobile/<file>' -H 'Accept: application/vnd.github.raw'`. Change them through a docs PR in that repo. The vendor name of the ingestion platform never appears there. Read the next-steps page at the start of every session. It has the ordered steps with their status, the user's decisions Q1–Q9, and the `#decision-record` JSON block. Copy the user's answers into that block and mark steps done there.
- **GitHub:** private repo `fanfinity/sfere-android-sdk`. `origin` uses HTTPS because no SSH key is loaded. Push with `git -c credential.helper='!gh auth git-credential' push`, or run `gh auth setup-git` once.

## Layout

| Path | What |
|---|---|
| `README.md` | Integration guide for app developers. |
| `TESTING.md` | For developers and testers: status, prerequisites, and how to test (unit tests, parity, mock, sample app). Keep its status table and test counts current. |
| `CONTRIBUTING.md`, `SECURITY.md`, `CHANGELOG.md` | Pull-request policy (mirrors `fanfinity/apis`), vulnerability reporting, and the changelog whose tagged section becomes the release notes. |
| `docs/ci-cd.md` | The workflows, the required checks, the rulesets and the release steps. Update it with every change under `.github/` or `.githooks/`. |
| `.github/workflows/` | `ci.yml` (the gate: Kotlin style, Static analysis, API contract, Tests & coverage, SonarQube, Build), `dependency-graph.yml` (Gradle graph submission from `main`), `release.yml` (`v*` tag → GitHub Packages → verify the registry copy → GitHub release). Actions pinned by commit SHA. |
| `.githooks/pre-commit` | ktlint on the staged Kotlin, `apiDump` when SDK sources are staged, vendor-name check. Installed with `git config core.hooksPath .githooks`. |
| `sfere-analytics/api/sfere-analytics.api` | The public API dump (binary-compatibility-validator). `apiCheck` is the gate, `apiDump` the only writer. |
| `config/detekt/detekt.yml` | detekt rules on top of the defaults; zero findings allowed, no baseline. |
| `sonar-project.properties` | SonarQube Cloud project `fanfinity_sfere-android-sdk`; the scan runs only when `SONAR_TOKEN` exists. |
| `sfere-analytics/` | Module `:sfere-analytics` (`io.sfere:analytics`), sources in `src/main/kotlin/io/sfere/analytics/` (public API at the top level, everything else under `internal/`) |
| `sfere-analytics/src/test/` | JVM tests (JUnit 4 + MockWebServer, no Robolectric). `TransportTest.kt` runs the real analytics-kotlin core |
| `sample/` | Sample app (`:sample`, plain Android views, no UI libraries). Debug builds allow cleartext to `10.0.2.2` only. Default host `http://10.0.2.2:4320`, deep-link scheme `sfereexample://` |
| `tools/mock_ingest.mjs` | Mock ingestion endpoint for the sample app (Node, no dependencies): `/v1/batch/b`, `GET /__events`, `POST /__reset`, `POST /__fail {status, count, retryAfter}`, gzip bodies. Port 4320 or `SFERE_MOCK_PORT`. Start and stop it with `tools/mock_ingest_ctl.sh start|stop|status`. |
| `tools/parity.sh`, `tools/parity_diff.py` | Cross-repo parity check: verifies the shared files are identical in both repos, runs both parity tests, and compares the upload bodies key by key. Exits non-zero on any difference. |
| `fixtures/` | `deeplinks.json` (URL → `clientIds` / `campaign`) and `event-names.json` (base names → Sfere names). Gradle passes the path to the tests as `sfere.fixtures.dir`. The iOS repo has an identical copy. |

## Run commands

All Gradle commands need `JAVA_HOME=$(/usr/libexec/java_home -v 17)`.

| Task | Command | VS Code |
|---|---|---|
| Unit tests | `./gradlew :sfere-analytics:testDebugUnitTest` | `Android: test` (default build task, `Cmd+Shift+B`) |
| AAR | `./gradlew :sfere-analytics:assembleRelease` | `Android: build AAR` |
| Sample app | `./gradlew :sample:assembleDebug` | `Android: build sample app` |
| What CI checks | `./gradlew ktlintCheck detekt :sfere-analytics:apiCheck :sfere-analytics:lintRelease :sample:lintDebug` | `Android: quality checks` |
| Fix formatting / update the API dump | `./gradlew ktlintFormat` / `./gradlew :sfere-analytics:apiDump` | — |
| Coverage report | `./gradlew :sfere-analytics:createDebugUnitTestCoverageReport` → `sfere-analytics/build/reports/coverage/test/debug/` | — |
| Publish dry run | `./gradlew :sfere-analytics:publishReleasePublicationToMavenLocal` → `~/.m2/repository/io/sfere/analytics/` | — |
| Free Gradle memory | `./gradlew --stop` | `Android: stop Gradle daemons` |
| Mock ingestion server | `tools/mock_ingest_ctl.sh start` / `stop` (safe to run twice; stop kills only the PID on the port after checking it is the mock) | `Mock ingest: start` / `Mock ingest: stop` |
| iOS vs Android payloads | `tools/parity.sh` (needs `../sfere-ios-sdk`, or `SFERE_IOS_SDK_DIR`) | `Parity: compare iOS and Android payloads` |

- `local.properties` (git-ignored) holds `sdk.dir=~/Library/Android/sdk`. On another machine `ANDROID_HOME` works too; CI uses the runner's SDK.
- **Version:** `VERSION_NAME` in `gradle.properties` is the only place the SDK version lives (`0.1.0-beta.1`). It becomes `context.library.version`, the Maven version and the tag `vVERSION_NAME`. A tagged version is immutable: bump it before changing the SDK again (CI's "API contract" job fails otherwise), and add the `CHANGELOG.md` entry under `## [Unreleased]`.
- **Warnings are errors:** Kotlin `allWarningsAsErrors`, Android Lint `warningsAsErrors`, detekt `maxIssues: 0`, ktlint `android_studio` style (`.editorconfig`). Fix the cause; suppress only with a reason in a comment.
- `gradle.properties` caps Gradle memory: 2 GB heap, 2 workers. Kotlin compiles inside the Gradle daemon (`kotlin.compiler.execution.strategy=in-process`), so there is no Kotlin daemon to clean up; never kill one by name. This Mac has crashed from memory pressure before, so keep the caps. Run builds one after another, and stop the daemons when done.

## Rules the code follows

- **Wire shape:**
  - Uploads go to `POST {host}/v1/batch/b` with `Authorization: Basic base64("writeKey:")`, and the batch body also carries `writeKey`.
  - No `_metadata`, no empty `integrations` and no `context.instanceId` on the wire. Each extra key becomes a warehouse column (full list below).
  - Only the allowed `clientIds` keys are sent (the list is in `fixtures/deeplinks.json`).
- **No third-party hosts:**
  - Settings are inline, and a `RequestFactory` subclass answers the settings request locally.
  - Segment telemetry is turned off.
  - Tests assert that the only request goes to `{host}`.
- **Pipeline:** the SDK does not use `SegmentDestination`, because a plugin can't remove `_metadata` or `integrations` there. Instead, `SfereDestination` runs an `EventPipeline` subclass that overrides `stringifyBaseEvent`.
- **Event names:**
  - GA4-style snake_case, decided 2026-10-13. Base lifecycle names are renamed: `Application Opened` → `app_opened`, and so on.
  - The SDK adds `session_started`.
  - E-commerce uses the GA4 keys from EVENTS.md.
- **No hashing:** the SDK never hashes or reformats emails or phone numbers.
- **Vendor names:** never name the ingestion vendor in code or docs; say "ingestion endpoint". Segment library names appear only in `context.library.base` and in notices.
- **Startup timing:** events queued while the base library starts up go out on the flush interval (30 s by default), not on `flushAt`.
- **Uploads are not gzipped.** Whether the ingress accepts gzip is still unverified.
- **Public API is a contract:** any change to what `io.sfere.analytics` exposes shows up as a diff of `sfere-analytics/api/sfere-analytics.api` (run `apiDump`, commit the dump, review the diff). Keep the iOS SDK's API in step.

## Same behaviour as iOS

Tests check each behaviour below on both platforms. The two parity tests run the same calls:
- Android: `TransportTest.parityScenarioAndDeepLinkProperties` (this repo)
- iOS: `WireTests.testParityScenarioAndDeepLinkProperties` (`sfere-ios-sdk`)

`tools/parity.sh` must report every shared file and every event as "same". When you change what the SDK sends, make the matching change in `sfere-ios-sdk` too (matching PRs in both repos), and run `tools/parity.sh` with both repos checked out side by side.

These files are shared and must stay byte-identical in both repos: `fixtures/deeplinks.json`, `fixtures/event-names.json`, `tools/parity.sh`, `tools/parity_diff.py`, `tools/mock_ingest.mjs` and `tools/mock_ingest_ctl.sh`. Change them in both repos in the same round; `tools/parity.sh` fails while they differ.

- **Consent:** `consent` in the config is the initial value only. Consent already stored by `setConsent` wins.
- **Reset:** `reset()` clears the user id, traits, session and click IDs. Consent and opt-out stay.
- **Opt-out:** `setEnabled(false)` deletes events not yet uploaded and is remembered across launches.
- **`deep_link_opened`:** properties are only `url`, `referring_application` and `sfere_dedup_id`.
- **Device id:** no `context.device.id`.
- **Dropped keys:** `instanceId`, `referrer`, `__eventOrigin` and `metrics` are removed. So are an empty `category` and empty `properties`.

## Decisions (2026-10-13 to 18)

- **Separate repos (2026-10-18):** iOS and Android are separate repos because apps integrate them differently. The combined repo was deleted.
- **Distribution:** private during beta (GitHub Packages Maven), then public (Maven Central `io.sfere`) at 1.0.0. **CI/CD added 2026-10-20** on the `fanfinity/apis` model: named required checks, rulesets without bypass, Dependabot, SHA-pinned actions, a tag-triggered release that publishes `io.sfere:analytics` to GitHub Packages and verifies the registry copy before cutting the GitHub release (`docs/ci-cd.md`). Plan steps F1 (Android part) and G1 (Android part) are done by this; the cross-repo parity job and the emulator job are still open.
- **Push tracking:** not in v1; it comes in v1.1.
- **Platform context (Q2, 2026-10-15):** keep everything the base library adds: `app`, `device` (without `id`), `os`, `locale`, `timezone`, `userAgent`, `screen`, `network`.
- **2026-10-15 answers (Q3–Q9 in the next-steps page):** native UI tests (Espresso); advertising IDs in v1 but off by default (`collectAdvertisingId`); Play Install Referrer in v1; GitHub Actions on every PR; Claude opens a PR for the contract docs in `fanfinity/sfere-sdk`; beta pilot app `sfere-sdk-demo`. (Q3 said "one private repo"; the 2026-10-18 split replaces it.)
- **React Native:** the React Native SDK will wrap this SDK and the iOS one.
- **Minimum OS:** Android API 21.

## Still open

- **Spike B (Q1, open):** send real events to staging `/v1/batch/b` with a browser key, and check gzip support. This needs a staging host and key.
- **Never run yet:** `Sfere.setup(context, …)`, the SharedPreferences store and the real Android lifecycle and context plugins. The JVM tests use the core library with in-memory storage.
- **Sample app:** has not run on an emulator yet. The emulator needs cmdline-tools and a system image (an AVD exists, but `system-images/` is empty).
- **`debug` option:** the README and `SfereConfiguration` say debug mode logs every payload; check that it does, and keep it the same as iOS.
- **Startup race (found 2026-10-20 on a slow CI runner):** the base library provides its user state on its own dispatcher after construction; an event processed before that is dropped inside the library (`lateinit property anonymousId has not been initialized`). The JVM tests now wait for that state in the harness (`TransportTest.awaitStartup`). An app's first `track` right after `Sfere.setup` on a slow device may hit the same window: check it in phase D3 and, if confirmed, hold events in `Sfere` until the library has its state.
- **Next round:** emulator flows (phase D, Espresso), Play Install Referrer, advertising IDs, the first beta tag (`v0.1.0-beta.1`, an admin creates it on `main`), the SonarQube Cloud project and its `SONAR_TOKEN`, a macOS parity job that checks out `sfere-ios-sdk`.
