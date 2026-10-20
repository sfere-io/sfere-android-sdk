# Changelog

All notable changes to the Sfere Android SDK (`io.sfere:analytics`). The format
follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/); versions follow
semantic versioning once 1.0.0 ships. The `release` workflow uses the section for
the tagged version as the GitHub release notes, so write entries for app developers.

## [Unreleased]

## [0.1.0-beta.1] - 2026-10-20

### Added

- First beta of the SDK on the base library (analytics-kotlin 1.26.0): `Sfere.setup`,
  `track`, `screen`, `identify`, `purchase`, `reset`, `setConsent`, `setEnabled`, `openUrl`.
- Lifecycle events `app_installed`, `app_updated`, `app_opened`, `app_backgrounded`,
  `session_started` and `deep_link_opened`, with GA4-style snake_case names.
- Click IDs and UTMs from deep links in `context.clientIds` and `context.campaign`.
- Consent (`context.consent`) and opt-out, both remembered across launches.
- Uploads to `POST {host}/v1/batch/b` only, with Basic auth; no `_metadata`,
  no empty `integrations`, no `context.instanceId`, no `context.device.id`.
- Publishing to GitHub Packages (`maven.pkg.github.com/sfere-io/sfere-android-sdk`, the organization
  renamed from fanfinity to sfere-io on 2026-10-20)
  with the sources jar; CI gates on every pull request (style, static analysis,
  public API dump, tests with coverage, build).
- Built with Kotlin 2.4.21, with kotlinx-serialization-json 1.11.0 (part of the public API)
  and kotlinx-coroutines 1.11.0. Apps that compile Kotlin need Kotlin 2.3 or newer; Java-only
  apps are not affected.
