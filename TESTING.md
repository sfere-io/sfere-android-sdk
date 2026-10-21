# Testing the Sfere Android SDK

For developers and testers. To add the SDK to an app, see [`README.md`](README.md). The iOS SDK lives in its own repository, [`sfere-io/sfere-ios-sdk`](https://github.com/sfere-io/sfere-ios-sdk), and sends the same payload.

## Status (2026-10-21)

Version `0.1.0` is ready to tag. Apps install it from JitPack with no credentials (`com.github.sfere-io:sfere-android-sdk:v0.1.0`, steps in the README); a build of the release commit on JitPack succeeded before tagging. JitPack builds the `v0.1.0` tag the first time it is requested after the tag is pushed.

| Area | State |
|---|---|
| Android SDK | 53 unit tests pass and the sample app builds. **Not yet run on an emulator or device**, so `Sfere.setup(context)`, the SharedPreferences store and the Android lifecycle events still need a real run. |
| Same payload as iOS | The shared parity scenario matches field by field (`tools/parity.sh`). |
| Staging | Not tested against the real ingestion endpoint yet. Everything so far runs against a local mock. |
| CI | Every pull request runs ktlint, detekt, Android Lint, the public API check, the unit tests with coverage, a SonarQube scan and the AAR/APK build with a local publish check ([`docs/ci-cd.md`](docs/ci-cd.md)). |
| Distribution | JitPack (`jitpack.yml`) is the public install path. The release workflow also publishes `io.sfere:analytics` to GitHub Packages, which needs a token. |

## What you need

- JDK 17, the Android SDK (API 35), and an emulator or device for the sample app.
- Node 18+ and Python 3 for the mock server and the scripts.
- `local.properties` with the SDK path (the file is git-ignored):

```sh
echo "sdk.dir=$HOME/Library/Android/sdk" > local.properties
```

All commands below run from the repository root. In VS Code, the same commands are tasks: `Cmd+Shift+B` runs the unit tests, and *Terminal → Run Task* lists the rest. Run `./gradlew --stop` when you are done, to free the memory the Gradle daemon holds.

## How to test

### 1. Unit tests

JVM tests, no emulator needed:

```sh
JAVA_HOME=$(/usr/libexec/java_home -v 17) ./gradlew :sfere-analytics:testDebugUnitTest
```

Expected: `BUILD SUCCESSFUL`, with 53 tests in `sfere-analytics/build/test-results/`.

The rest of what CI checks, in one command (VS Code task *Android: quality checks*):

```sh
JAVA_HOME=$(/usr/libexec/java_home -v 17) ./gradlew ktlintCheck detekt :sfere-analytics:apiCheck :sfere-analytics:lintRelease :sample:lintDebug
```

`ktlintFormat` fixes formatting. `:sfere-analytics:apiDump` updates `sfere-analytics/api/sfere-analytics.api` after an intentional public API change; review that diff like a contract. For coverage, run `:sfere-analytics:createDebugUnitTestCoverageReport` and open `sfere-analytics/build/reports/coverage/test/debug/index.html`.

### 2. Same payload as iOS

This needs both repositories checked out side by side, `sfere-ios-sdk` and `sfere-android-sdk` in the same folder. It also needs a Mac with Xcode for the iOS tests. Set `SFERE_IOS_SDK_DIR` and `SFERE_ANDROID_SDK_DIR` to use other locations.

```sh
tools/parity.sh
```

First it checks that the files both repositories share (`fixtures/`, the mock server, the parity scripts) are identical. Then it runs the same scenario on both SDKs and compares the upload bodies key by key. Expected: every shared file and every event reported as `same`, exit code 0.

### 3. Start the mock ingestion server

The sample app sends to a local mock of the ingestion endpoint, so you can see exactly what it sends.

```sh
tools/mock_ingest_ctl.sh start    # http://127.0.0.1:4320; running it twice reuses the server
tools/mock_ingest_ctl.sh stop
```

Useful routes while testing:

```sh
curl -s http://127.0.0.1:4320/__events | python3 -m json.tool    # everything received
curl -s -X POST http://127.0.0.1:4320/__reset                     # clear it
curl -s -X POST http://127.0.0.1:4320/__fail -d '{"status":503,"count":1}'   # next upload fails with 503
curl -s -X POST http://127.0.0.1:4320/__offline -d '{"offline":true}'        # drop connections like a dead network
curl -s -X POST http://127.0.0.1:4320/__offline -d '{"offline":false}'
```

### 4. Sample app

```sh
JAVA_HOME=$(/usr/libexec/java_home -v 17) ./gradlew :sample:assembleDebug
adb install -r sample/build/outputs/apk/debug/sample-debug.apk
```

- **Emulator:** the app sends to `http://10.0.2.2:4320`, which is the Mac's mock as the emulator sees it.
- **Real device over USB:** run `adb reverse tcp:4320 tcp:4320`, then in the app set the host to `http://127.0.0.1:4320`, save, and restart the app.
- Deep link: `adb shell am start -W -a android.intent.action.VIEW -d "sfereexample://match/42?utm_source=test&gclid=abc"`
- Debug logs: `adb logcat -s Sfere`

The screen has buttons for each event, switches for consent and opt-out, and a *Received by the mock* list. There is no automated flow script for Android yet. Repeat these checks by hand:

- Fresh install: `app_installed`, `app_opened` and `session_started`, with no `context.device.id`.
- Upgrade: `app_updated` with `previous_version` and `version`.
- Deep links on a cold start and while running: `deep_link_opened`, then `context.clientIds.gclid` and `context.campaign` on later events.
- Consent: the next event carries `context.consent.categoryPreferences`.
- Identify, then reset: a new anonymous id, and no user id or traits.
- Opt-out: nothing is sent, and the choice is remembered after a relaunch.
- Server errors: 503 and 429 are retried, and 400 is dropped.
- Offline queue: 200 events queued offline survive a forced quit, then each arrives exactly once.
- Session: a new session and `session_started` after the session timeout in the background.

### 5. What to check in the payloads

For any event in `/__events`:

- It went to `POST /v1/batch/b` with `Authorization: Basic …`, and the body has `writeKey` and `batch`.
- Event names are snake_case (`app_opened`, `add_to_cart`, `deep_link_opened`).
- `context.library` is `{name, version, base}`, and `context.session.id` is present.
- These are never present: `_metadata`, an empty `integrations`, `context.instanceId`, `context.device.id`, `metrics`.
- Email and phone in `identify` traits are sent as given. The SDK never hashes them.
- No request goes anywhere except the configured host. A proxy such as Proxyman shows this.

## Reporting problems

Open an issue in this repository with:

- the Android version, and whether it was an emulator or a device;
- the steps you took;
- the relevant part of `curl -s http://127.0.0.1:4320/__events`;
- the output of `adb logcat -s Sfere`.

## Repository layout

| Path | What |
|---|---|
| `sfere-analytics` | The SDK (`io.sfere:analytics`) and its unit tests |
| `sample` | Sample app |
| `fixtures/` | Test tables shared with the iOS SDK (deep links, event names). The iOS repository has an identical copy. |
| `tools/` | Mock ingestion server, parity check |
| `sfere-analytics/api/` | The public API dump that CI checks (`apiCheck`) |
| `config/detekt/` | detekt rules |
| `.github/`, `.githooks/` | CI, release and dependency workflows; the pre-commit hook |
