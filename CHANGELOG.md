# Changelog

All notable changes to the Sfere Android SDK (`io.sfere:analytics`). The format
follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/); versions follow
semantic versioning once 1.0.0 ships. The `release` workflow uses the section for
the tagged version as the GitHub release notes, so write entries for app developers.

## [Unreleased]

## [0.1.1] - 2026-10-22

### Changed

- Releases are published to **Maven Central** as `io.sfere:analytics`. Apps add nothing
  to their repositories (`mavenCentral()` is there by default) and depend on
  `implementation("io.sfere:analytics:0.1.1")`. No token is needed.
- JitPack and GitHub Packages are no longer release channels. Apps on
  `com.github.sfere-io:sfere-android-sdk:v0.1.0` should move to `io.sfere:analytics`;
  the SDK itself is unchanged.
- The SDK is licensed under the MIT License (`LICENSE`).

## [0.1.0] - 2026-10-21

The first public release. `0.1.0-beta.1` was never tagged or published; its changes ship here.

### Added

- The SDK on the base library (analytics-kotlin 1.26.0): `Sfere.setup`,
  `track`, `screen`, `identify`, `purchase`, `reset`, `setConsent`, `setEnabled`, `openUrl`.
- Lifecycle events `app_installed`, `app_updated`, `app_opened`, `app_backgrounded`,
  `session_started` and `deep_link_opened`, with GA4-style snake_case names.
- Click IDs and UTMs from deep links in `context.clientIds` and `context.campaign`.
- Consent (`context.consent`) and opt-out, both remembered across launches.
- Uploads to `POST {host}/v1/batch/b` only, with Basic auth; no `_metadata`,
  no empty `integrations`, no `context.instanceId`, no `context.device.id`.
- Install from JitPack with no credentials: add `maven("https://jitpack.io")` and
  `implementation("com.github.sfere-io:sfere-android-sdk:v0.1.0")` (see the README).
  Each release is also published to GitHub Packages as `io.sfere:analytics`, which
  needs a GitHub token to download.
- Built with Kotlin 2.4.21, with kotlinx-serialization-json 1.11.0 (part of the public API)
  and kotlinx-coroutines 1.11.0. Apps that compile Kotlin need Kotlin 2.3 or newer; Java-only
  apps are not affected.
