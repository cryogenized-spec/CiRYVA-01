# CiRYVA-01 — Current Session Checkpoint

**Updated:** 2026-11-02 (SAST, UTC+02:00).
**Current stage:** P00/P01 and P02 preflight merged. **P02 native implementation is still open [PR #5](https://github.com/cryogenized-spec/CiRYVA-01/pull/5), not merged.** The source commit `5edcf3219` passed Android compile/lint/unit tests and **three real Android 15 emulator UI tests** at 320dp/1.5x text; screenshot artifact captured. No interactive cloud-browser session or physical-handset test yet.

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

- **Verified Android runtime test:** [GitHub-hosted Android 15 emulator run 41387425469](https://github.com/cryogenized-spec/CiRYVA-01/actions/runs/41387425469) **passed** on the P02 implementation source, running `:app:connectedDebugAndroidTest` against emulator-5554 (API 35). Logs confirm **Starting 2 tests**, **Finished 2 tests**, `BUILD SUCCESSFUL` and **67 Gradle tasks executed**. This validates only the included draft-clear/recreate and landscape-editor UI scenarios. It is not physical-device, broad accessibility or full UI design acceptance.
- Added [P02 device acceptance protocol](P02-DEVICE-ACCEPTANCE.md), covering signed-by-source APK provenance, saved-draft restart, empty-state deletion, IME, landscape, accessibility and privacy.
- Updated the [README](../README.md) to reflect merged P00/P01, verified Android CI in PR #5 and pending P02/P03 boundaries.
- The connected local browser rejected access to GitHub ruleset settings; the GitHub repo connector can only read settings. **No ruleset change or PR merge was performed.**

## Next action

Verify the final PR #5 HEAD's Android/Kotlin checks and final Codex review, have a repository administrator add **Android build, lint and unit tests** as a required status in ruleset `26312198`, then merge only after enforcement. The two Android 15 emulator tests already passed. Record separate physical-phone and broader font-scale/IME testing; do not claim production-ready status. P03 OpenAI transport starts after P02 foundation is accepted; never include credentials in public GitHub.

## Resource-policy amendment for emulator workflow

Codex raised a P1 review issue because the initial emulator workflow auto-downloaded a large Android system image without a separate cost/storage/licensing assessment. The job was changed to **workflow_dispatch-only** after the first successful 2-test run. See [P02 implementation cost assessment](P02-IMPLEMENTATION.md#emulator-resource-licensing-and-cost-assessment-verified-2-november) for separate estimated download/storage, public-runner billing, licensing and alternatives. No local SDK/emulator was installed; actual SDK transfer byte totals were not measured. Final PR Codex review and required Android branch status remain pending.

## Newest P02 checkpoint — 2 November 2026 SAST

- [Verified Android emulator run 41451304469](https://github.com/cryogenized-spec/CiRYVA-01/actions/runs/41451304469) completed with **3/3 Android 15 UI tests passing**, 320dp/1.5x accessibility layout, successful rebuild/reinstall/relaunch and artifact `ciryva-p02-emulator-large-text`. The immediately preceding screenshot launcher failure was fixed by reinstalling the debug APK after instrumentation completed.
- Restored the emulator workflow to **manual dispatch only** after successful one-time runtime evidence, preventing repeat high-bandwidth test runs on every push.
- Manual-only [browser preview using Appetize](../.github/workflows/browser-appetize.yml) is committed but **not run**; it needs an account token secret and a successful protected merge to `main`. Browser streaming uses third-party hosting with separate terms/quota; no automatic upload is enabled.
- Active ruleset `26312198` still requires only `Repository integrity` and `Foundation JVM tests`. The repo admin must add **`Android build, lint and unit tests`** before PR #5 merges. No available connected tool has write access to that settings page. No merge or local SDK installation was performed.
- Keep Codex review aligned with the latest head and close resolved review threads only after checking their actual fixes. New documentation/trigger-only commit CI must be green before protected merge.
- P03 draft [PR #6](https://github.com/cryogenized-spec/CiRYVA-01/pull/6) remains separate, with successfully tested stateful UTF-8 and Responses event parser. Do not advance to paid inference without explicit credential custody and consent.
