# P02 — First native Android vertical slice

**Status (2 November):** source implementation on [PR #5](https://github.com/cryogenized-spec/CiRYVA-01/pull/5). Real Android CI and final Codex review **passed** at `8b3373ad48`; branch-protection enforcement and device/emulator acceptance remain outstanding.

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

## Review improvements (2 November)

- Corrected `android.useAndroidX=true`, required by Compose dependencies on AGP 8.13.
- The input now has a 200dp minimum height in a scrollable, inset-aware layout, so editing and buttons remain reachable in short/landscape windows.
- A previously saved draft remains clearable when all editor text is deleted; Save-empty and Clear persist the deletion.
- Added two `androidTest` Compose UI tests covering save → delete all → clear → recreate and editing/saving in a short landscape viewport. CI **compiles** them but does **not run** them; real device/emulator execution is still pending before P02 is verified.

### Verified build evidence

On `8b3373ad48`, [GitHub Actions run 41381368037](https://github.com/cryogenized-spec/CiRYVA-01/actions/runs/41381368037) completed successfully, including authenticated Gradle 8.13 wrapper verification, `:app:assembleDebug`, `:app:lintDebug`, `:app:testDebugUnitTest`, `:app:compileDebugAndroidTestKotlin`, and `:core:test`. The job reported **66 executed tasks, BUILD SUCCESSFUL**, and published the `ciryva-p02-debug-apk` artifact. Compose instrumentation tests were **compiled, not executed**. The reviewed commit had no remaining Codex findings.

### Android emulator instrumentation — verified 2 November 2026

[GitHub Actions run 41387425469](https://github.com/cryogenized-spec/CiRYVA-01/actions/runs/41387425469) **passed** on an Android 15/API 35 `emulator-5554` with the P02 implementation source. It ran `./gradlew :app:connectedDebugAndroidTest --no-daemon --stacktrace`, reporting **Starting 2 tests**, **Finished 2 tests**, **BUILD SUCCESSFUL in 3m 11s** and 67 executed tasks. Scenarios cover persistence after Save → delete all → Clear → recreate, and reaching edit/save actions in landscape. A Compose-test API deprecation warning was observed; it is nonfatal and can be migrated separately.

**Limits:** this verifies two automated emulator scenarios, not a Xiaomi physical handset, wide font-scale testing, network transport, battery usage or release stability. The Android check still needs to become a **required** main-branch status before PR #5 merges.

## Emulator resource, licensing and cost assessment (verified 2 November)

A GitHub-hosted Android 15 emulator is **more expensive in resources than the compile-only workflow**. `.github/workflows/android-emulator.yml` is therefore **workflow_dispatch-only**: a human must request it in GitHub Actions. It does not re-download the emulator on every PR edit, push or normal merge.

- **Downloads — estimate, not measured:** expect roughly **0.8–2.5 GB** of additional SDK command-line/platform tools, emulator binaries, Android 15 x86_64 system image and dependencies on an uncached standard Ubuntu runner, **on top of** the compile-only Gradle/plugin dependency footprint. Logs do not record aggregate SDK download bytes because the emulator runner suppresses individual SDK download output.
- **Temporary unpacked storage — estimate, not measured:** allow approximately **3–8 GB extra** for installed SDK/emulator/system images and disposable Android virtual-device data; budget up to 16 GB of available runner disk as a conservative envelope. Exact footprint depends on runner preinstalls and Gradle caches. The Android emulator runner's virtual device reported **2560 MB RAM** in the first run.
- **Runtime / quota:** the [first emulator job](https://github.com/cryogenized-spec/CiRYVA-01/actions/runs/41387425469) ran from roughly 04:15:25 to 04:19:48 UTC; its Gradle command reported 3m 11s. Subsequent runs may vary significantly.
- **Billing:** as of the current [GitHub Actions billing rules](https://docs.github.com/en/actions/concepts/billing-and-usage), **standard GitHub-hosted runners on public repositories are free**, but the run still uses runner capacity and network bandwidth. Private repositories or larger runners have plan-dependent charges. This repo is public; do not treat other repo types as free.
- **Licences:** Android SDK/platform/emulator packages remain subject to their Android SDK and applicable system-image licences. The emulator-runner action runs SDK installers and accepts terms as part of setup; no Android package is installed on the user's laptop.
- **Less costly options:** retain ordinary compile/lint/JVM CI (no emulator) for every PR update, or manually test the debug APK on the user's physical Android handset (requires device involvement, but avoids downloading an emulator system image). Neither alone verifies the same automated Android UI scenarios.

**First-run disclosure:** the branch temporarily used a push-triggered emulator job to obtain the initial runtime evidence. After Codex review flagged the missing separate budget, the trigger was replaced with explicit manual dispatch to avoid recurring heavyweight downloads. No repeat emulator run was triggered by this documentation fix.
