# P02 — Android toolchain and first native-build preflight

**Date:** 2026-11-02 (SAST). **Status:** engineering preflight; no Android code, CI build or installable APK yet.
**Starting base:** P01 PR #3 merged as `2f773c96c6f15a2f3f2c70957471a7b79f595d51`.

## Objective and boundaries

Create the **first genuinely buildable native Android host**, not a fake chatbot or a screenshot wrapped in an app. The first implementation PR must introduce Android sources *together with* the verified Gradle wrapper, real Android CI, and meaningful unit tests. The P02 acceptance goal remains an installed and launched APK on a supported handset or emulator, plus passing `assembleDebug`, `lintDebug`, and `testDebugUnitTest`. Do not report JVM-only checks as Android validation.

**P01 design is accepted as a visual direction**, following the user's 2 November approval. Real-device typography, high font-scale, contrast over wallpaper, IME/window insets and final font inclusion remain unverified Android work.

## Conservative candidate stack (not yet build-tested)

| Component | Candidate | Reason / caveat |
| --- | --- | --- |
| Java for build | JDK 17 | Consistent with existing foundation CI and AGP 8.13 minimum |
| Gradle | **8.13** | Already used by the passing Kotlin foundation workflow |
| Kotlin / Compose compiler plugin | **2.2.21** | Matches the existing JVM core; use `org.jetbrains.kotlin.plugin.compose` |
| Android Gradle Plugin | **8.13.2** | Official 8.13 family supports Gradle 8.13, JDK 17, and Android API 36.1 |
| Android `compileSdk` | **36** | Compatible with this AGP family and the proposed 2026.04 stable Compose wave |
| Android `targetSdk` | **36** | Meets normal mobile Google Play submission baseline effective 31 August 2026; recheck before distribution |
| Android `minSdk` | **26, provisional** | Broad personal-device support, but cannot confirm without a test handset |
| Jetpack Compose BOM | **2026.04.01**, proposed | Avoid latest 1.12+ Compose libraries that require API 37 / AGP 9+ |
| Activity Compose | **1.11.0**, proposed | Android 36-era integration; no version added without a real Android build |
| Kotlin module strategy | Keep `:core` plain JVM; add `:app` | Reuse tested transport-agnostic primitives, avoid SDK dependency in `:core` |

**Important:** These are source-checked *compatibility candidates*, **not a proven working dependency matrix**. Resolve final exact artifacts against Maven metadata and Android CI before labelling the stack validated. If the required dependency closure forces AGP 9, revise the plan in a separate, testable change, not by silently upgrading the entire build.

References:

- [Official AGP 8.13 compatibility](https://developer.android.com/build/releases/agp-8-13-0-release-notes)
- [Compose compiler setup](https://developer.android.com/develop/ui/compose/setup-compose-dependencies-and-compiler)
- [Compose/AGP newer requirements](https://developer.android.com/develop/ui/compose/setup-compose-dependencies-and-compiler)
- [Google Play target API rules](https://developer.android.com/google/play/requirements/target-sdk)
- [Android SDK command-line tools](https://developer.android.com/tools/sdkmanager)

## Download and storage controls

**Nothing is installed on the user's laptop by this preflight.** Android SDK/Build Tools, Gradle distribution, Android Gradle Plugin and transitive AndroidX artifacts will be obtained by a GitHub-hosted CI runner in the first implementation PR. Budget for **hundreds of MB of fresh network downloads** and **roughly 1–3 GB of transient SDK/dependency/cache footprint** for an uncached, emulator-free Linux CI build; these are *planning estimates*, not measured usage or a guarantee. An emulator/system image would add substantially more, so defer it until selected. Record actual sizes and GitHub Actions consumption from the first run. Never install Android Studio/SDK, emulators, fonts or heavyweight dependencies on the user's HP OMEN without separate permission.

The **Gradle Wrapper JAR must be genuine** and checked against a published Gradle wrapper checksum. GitHub connectors that accept only UTF-8 cannot substitute a fake wrapper or an unverified shell script. Do not open a code PR with missing wrapper bits and claim Android readiness.

## Implementation sequence for P02

1. Obtain the genuine Gradle 8.13 wrapper scripts/JAR/properties, verify signature/checksum, add app module/plugin/manifest, and pin AndroidX/Compose dependencies.
2. Add a single **honest, functioning native host**: window/insets/theme and a minimal working local interaction with corresponding unit tests. Do **not** show interactive Send, camera, voice, tools, model selection or history controls before their implementation; the real chat comes at P03–P05.
3. Add `.github/workflows/android.yml` using JDK 17 and Android SDK API 36 with read-only token, pinned Actions, genuine `:app:assembleDebug :app:lintDebug :app:testDebugUnitTest`, and Gradle wrapper validation.
4. Check **actual GitHub Actions job logs** and fix failures. Compare bytes downloaded, cache sizes, APK dimensions and build timings. Preserve passing `:core:test` and repository integrity.
5. Once a true Android status job exists and passes, make **Android build, lint and unit tests** required on protected `main` before merging native app code.
6. Install APK on the agreed Android handset or emulator and record OS/API, launch, rotation, keyboard/IME and accessibility observations. Without this, P02 remains *in progress*, even with green CI.

## Open architecture questions

- Which Android handset / minimum OS must the first APK support?
- Whether the app remains single-user personal BYOK or will be distributed; the latter requires a backend-key custody decision before P03.
- Exact packaging and licensing of Orbitron and selected body font; no unapproved font binary.
- First meaningful native interaction and test beyond opening the app; do not build dummy screens solely to satisfy a visual gate.

This preflight is documentary and intentionally does not pass the P02 acceptance gate.
