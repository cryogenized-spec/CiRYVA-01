# CiRYVA-01 — Current Session Checkpoint

**Updated:** 2026-11-02 (SAST, UTC+02:00).
**Current stage:** P00/P01 and P02 preflight merged; P02 native implementation remains on open [PR #5](https://github.com/cryogenized-spec/CiRYVA-01/pull/5). Its implementation commit `8b3373ad48` passed real GitHub Android CI and fresh Codex review. **Not merged; no handset/emulator launch yet.**

## Verified baseline

- PR #1 foundation merge: `92949d9781fa06fe0f8bb72d0d77d6e87a590eea`; true Kotlin/JVM SSE framing, turn arbitration and token budget tests.
- PR #2 master roadmap merge: `34a552243cedf3f326f1bf1a179939a34239b8e3`.
- PR #3 visual direction merge: `2f773c96c6f15a2f3f2c70957471a7b79f595d51`; approved atmosphere, with native rendering still pending.
- PR #4 P02 preflight merge: `3d95160f46288313d505f4e6399054866ecd9eca`.
- Active ruleset `Mandatory CI test pass` ID `26312198` currently requires `Repository integrity` and `Foundation JVM tests`; Android status check must be added before accepting new native code.

## Current P02 work

- A **native Android Compose module** now exists on a feature branch with a real offline Save/Clear draft interaction and real source tests, a genuine Gradle 8.13 wrapper and candidate pinned Android stack. See [P02-IMPLEMENTATION.md](P02-IMPLEMENTATION.md).
- `.github/workflows/android.yml` requests real `:app:assembleDebug`, `:app:lintDebug`, `:app:testDebugUnitTest` and `:core:test`, plus APK artifact upload.
- **Verified:** on `8b3373ad48`, [Android run 41381368037](https://github.com/cryogenized-spec/CiRYVA-01/actions/runs/41381368037) passed wrapper SHA verification, `:app:assembleDebug`, `:app:lintDebug`, `:app:testDebugUnitTest`, `:app:compileDebugAndroidTestKotlin`, and `:core:test` (66 actionable Gradle tasks, successful). Uploaded `ciryva-p02-debug-apk` artifact, approximately 10.9 MB as GitHub archive. **Instrumentation tests were compiled only, not run.**
- Do **not** install the SDK or any font files on user's Linux laptop. GitHub-hosted runner only. No OpenAI API key or cost.
- Codex reviewed `8b3373ad48` without findings after AndroidX, saved-draft deletion, scrollable IME layout and correct Compose imports were fixed. Earlier review threads were resolved.
- **Still required before merging:** add `Android build, lint and unit tests` as a required status in active GitHub ruleset `26312198`. Available repository connector does not expose ruleset updates, so repository admin must configure it separately. Main remains on merged planning state.
- **Still required for P02 Verified:** install/launch APK and test restart, keyboard/rotation/font-scale/accessibility on actual Android device/emulator; no such test has happened.

## New P02 device-validation work (2 November)

- Added [GitHub-hosted Android 15 emulator workflow](../.github/workflows/android-emulator.yml) on the P02 implementation branch to **execute** the previously compiled Compose UI instrumentation tests; it is not part of the default lightweight PR pipeline. The first run is [41387425469](https://github.com/cryogenized-spec/CiRYVA-01/actions/runs/41387425469); inspect its final conclusion and logs before claiming runtime test success.
- Added [P02 device acceptance protocol](P02-DEVICE-ACCEPTANCE.md), covering signed-by-source APK provenance, saved-draft restart, empty-state deletion, IME, landscape, accessibility and privacy.
- Updated the [README](../README.md) to reflect merged P00/P01, verified Android CI in PR #5 and pending P02/P03 boundaries.
- The connected local browser rejected access to GitHub ruleset settings; the GitHub repo connector can only read settings. **No ruleset change or PR merge was performed.**

## Next action

Read the final emulator run result; fix any failed UI tests in the P02 implementation branch and request fresh Codex review if source changes. Verify the final PR #5 HEAD's Android and Kotlin checks, have a repository administrator add **Android build, lint and unit tests** as a required status in ruleset `26312198`, and merge only after enforcement. Even with emulator tests passing, record a separate physical-device test before assuming compatibility with the user's phone. P03 OpenAI transport starts after P02's foundation gate; never include real credentials in public GitHub.
