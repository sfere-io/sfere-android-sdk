## What

<!-- What does this PR change? -->

## Why

<!-- Link the issue or context. Why is this the right change? -->

## How was this tested

All commands run from the repository root with JDK 17 (`TESTING.md`).

- [ ] `./gradlew :sfere-analytics:testDebugUnitTest` (or explain why not applicable)
- [ ] `./gradlew ktlintCheck detekt :sfere-analytics:lintRelease`
- [ ] `./gradlew :sfere-analytics:apiCheck` (run `apiDump` and commit the dump if the public API changed)
- [ ] `tools/parity.sh` against `sfere-ios-sdk` (if what the SDK sends changed)

## Checklist

- [ ] Follows `CLAUDE.md` (wire shape, event names, no hashing, no third-party hosts)
- [ ] A change to what the SDK sends has the matching change in `sfere-ios-sdk` (same round)
- [ ] `CHANGELOG.md` has an entry under `## [Unreleased]` (SDK changes; label `skip-changelog` otherwise)
- [ ] `VERSION_NAME` in `gradle.properties` is not an already-tagged version
- [ ] Public API change: `sfere-analytics/api/sfere-analytics.api` regenerated and reviewed
- [ ] No vendor name for the ingestion platform anywhere (say "ingestion endpoint")
- [ ] No secrets or real write keys in the diff (`.env` is git-ignored; tests use fakes)
- [ ] CI or release changes (`.github/**`, `.githooks/**`) come with a `docs/ci-cd.md` update
