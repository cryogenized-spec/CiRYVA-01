# P02 — First native Android vertical slice

**Status:** proposed implementation PR, pending real Android CI and device acceptance. Branch: `feat/p02-native-compose-host`.

## Actual scope

The Kotlin/JVM core remains independent at `:core`. New `:app` is a real native Android module with Kotlin/Jetpack Compose, custom dark CiRYVA colours and one **functional, honest local feature**: an editable draft that users explicitly save to app-private `SharedPreferences`, reopen after a restart and clear. Save and Clear report storage success/failure, and draft length is bounded at 20,000 characters. An unsaved draft survives activity recreation via `rememberSaveable` where Android retains saved state. No network or provider permission is declared.

There is **no pretend chat**: no Send button, no invented model output, no conversation archive, camera, voice or tool controls yet. These come with genuine P03–P06 implementations. The initial design uses system sans fonts; Orbitron bundling and final typography remain later validation work.

## Build supply chain

- Gradle 8.13 wrapper scripts and JAR copied directly from Gradle's published `v8.13.0` GitHub tag; JAR blob SHA `9bbc975c742b298b441bfb90dbc124400a3751b9`.
- Official wrapper JAR SHA-256: `81a82aaea5abcc8ff68b3dfcb58b3c3c429378efd98e7433460610fecd7ae45f`.
- Official Gradle 8.13 binary ZIP SHA-256 pinned in `gradle-wrapper.properties`: `20f1b1176237254a6fc204d8434196fa11a4cfb387567519c61556e8710aed78`.
- Official reference: https://gradle.org/release-checksums/ . Original `v8.13.0` repository default points to a release candidate distribution and has therefore **deliberately been replaced** with stable 8.13 plus its verified ZIP digest.
- Android Gradle Plugin 8.13.2, Kotlin 2.2.21, JDK 17, `compileSdk` 36, `targetSdk` 36, provisional `minSdk` 26, Compose BOM 2026.04.01 and Activity Compose 1.11.0.
- Android GitHub Actions must execute `assembleDebug`, `lintDebug` and `testDebugUnitTest` with a real SDK and fail on errors. `:core:test` also remains in CI.

## Verification and unfinished gates

Until the **new Android CI job completes successfully**, do not claim a working build. Unit tests for `DraftEditor` cover persistence through a shared store, clearing, overlength rejection, failure propagation and bounded reading. They cannot substitute for instrumentation of actual `SharedPreferences`, IME, window insets or hardware launch.

Before merging this PR, require the exact Android CI status check in GitHub ruleset `26312198` alongside existing foundation and repository-integrity checks. If no available tool can edit rulesets, leave PR open and ask the user to add it in the GitHub UI. P02 completion additionally requires installing and launching on a real handset/emulator and recording API version, accessibility and restart behaviour.

**Costs:** GitHub runner makes downloads, not the user's laptop. First uncached Android build can consume hundreds of MB of bandwidth and 1–3 GB of temporary CI storage; no emulator or paid AI calls. Do not install anything locally without consent.
