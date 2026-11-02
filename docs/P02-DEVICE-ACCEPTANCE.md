# P02 — Android device acceptance protocol

**Prepared:** 2026-11-02 (SAST). This is a **procedure**, not a claim of completed testing. P02 remains incomplete until actual observations are recorded. See [implementation evidence](P02-IMPLEMENTATION.md).

## Evidence levels

- **GitHub build:** compile APK, lint and run Android/JVM unit tests.
- **GitHub Android emulator:** install app and run real instrumented UI scenarios using [emulator workflow](../.github/workflows/android-emulator.yml); this is Android runtime validation but **not a physical handset check**.
- **Physical device:** explicitly install and launch the exact APK from successful workflow, record Android version, model and any behavior differences. No user phone installation is possible through GitHub CI alone.

## Obtaining a genuine debug build

1. Open the successful PR #5 [Android CI run](https://github.com/cryogenized-spec/CiRYVA-01/actions/runs/41381368037), or the latest **passing** `Android native build` run on the final PR commit.
2. Download its `ciryva-p02-debug-apk` **artifact ZIP**, extract `app-debug.apk`, and check the run's commit SHA and successful job.
3. Debug APKs are development artifacts, not Play Store releases. Install only if they came from this repository's verified workflow, and only on a device you control. Android may require a temporary per-source permission for side-loading; revoke it afterwards if not needed.
4. No OpenAI API key is needed at this phase; this binary declares no network permission and is not a working AI chat client.

## Repeatable user-visible acceptance cases

| Case | Setup and action | Required outcome |
| --- | --- | --- |
| First launch | Install, open from launcher | Displays CiRYVA offline draft screen; no crash or deceptive online-control |
| Local save | Enter a distinctive note, tap **Save draft** | Status shows actual successful local write |
| Restart durability | Close/force-stop and reopen | Saved text reappears unchanged |
| Delete to empty | Save text, delete all editor characters | Save/Clear remain usable; empty state can be persisted |
| Clear | Clear stored draft; restart app | Draft stays empty; no stale text returns |
| Save failure | Controlled storage-failure simulation only (unit test) | No false “saved” success; no hazardous device manipulation |
| Portrait IME | Focus editor and type with keyboard open | Editor and Save/Clear remain reachable by scrolling |
| Landscape IME | Rotate, open keyboard and type | Editor remains accessible; content can be scrolled to actions |
| Accessibility | Raise system font scale (e.g. 1.3×–1.5×) | Controls remain readable and reachable; labels do not clip |
| Privacy | Inspect manifest / network permissions | No INTERNET permission or provider credential required |
| Rotation | Enter unsaved text and rotate | Unsaved text survives recreation when Android saved state is retained |

Report model/Android build/API, app version/build SHA, test date/time in SAST, exact outcomes, screenshots if useful, and failures. Do not paste user-private draft content or personal screenshots into the public GitHub repository.

## Deferred beyond P02

P03/P04 must introduce **real** OpenAI credentials, model selection and cancellable streamed responses. P05 will handle durable conversation history/Markdown. P06 extends persona, attachments and appearance. P02 must not expose inactive Send/agent/voice/camera/MCP controls as if implemented.

**CI limitation:** compiling `app/src/androidTest` is not running those tests. Only a completed successful `connectedDebugAndroidTest` on a real emulator or handset establishes runtime UI-test evidence. A successful emulator run still does not prove compatibility with the user's phone, which should be recorded separately.

## Recorded automated evidence — 2 November 2026

**Run:** [41387425469](https://github.com/cryogenized-spec/CiRYVA-01/actions/runs/41387425469); runner: GitHub-hosted Ubuntu 24.04, Android emulator x86_64, Android 15/API 35. **Executed:** two Compose instrumentation scenarios via `:app:connectedDebugAndroidTest`. **Outcome:** both finished successfully; 67 Gradle tasks; `BUILD SUCCESSFUL`.

This qualifies as emulator validation for those two test cases only. Physical-phone test cases, explicit large-font-scale measurements and manual insets observations remain **not performed**; the acceptance table above is still a procedure for those remaining cases.

## Optional interactive browser testing (no laptop APK/emulator download)

For an **interactive native Android test** in a laptop browser (not a replica/mockup), use the separately opt-in [Appetize browser preview workflow](../.github/workflows/browser-appetize.yml). This is a third-party emulator and requires the owner's own account; **it has not yet been run or verified for this app**. This option was prepared 2 November 2026. It is deliberately **manual-only** and never uploads APKs automatically.

**First time, after PR #5 is merged and the workflow is present on `main`:**

1. Create a free account at [Appetize](https://appetize.io/). Its published free plan is **30 streaming minutes per month** (as checked 2 November 2026). Review the plan before using it; there are paid tiers.
2. Obtain your **Appetize API token** from your Appetize account. In GitHub open **Settings → Secrets and variables → Actions → New repository secret**, name it `APPETIZE_API_TOKEN` and paste the token. Never paste this credential into a chat or source file.
3. In GitHub **Actions**, choose **Browser Android preview (Appetize opt-in)**, click **Run workflow** on `main`, and inspect the run. No upload happens until you deliberately run it. The runner rebuilds and tests the exact checked-out source, then submits `app-debug.apk` directly from GitHub to Appetize, with no local APK/SDK installation.
4. When the job passes, sign in to [Appetize Apps dashboard](https://appetize.io/apps), select CiRYVA and **Play**. Rotate the virtual phone, enter a note, Save, restart, Clear, and examine the layout at larger font scales. Record failures privately or report non-sensitive symptoms.

**Security and limitations:** the built development APK is transferred to **Appetize, a separate service**, and its per-build share link can be run by anyone who learns that link. Do not enter secrets or sensitive personal drafts into the preview. Repository workflow logs are public, so the workflow deliberately does not echo its Appetize build link; use your private Appetize dashboard. The app currently implements an **offline local draft editor**, **not** OpenAI chat, full persona/settings screens, or the approved four-screen design. Appetize requires active browser internet access and consumes streaming bandwidth. An Android virtual device is not identical to a physical Xiaomi handset. The GitHub-hosted automated emulator, which previously passed two tests on API 35, is a different, non-interactive CI environment.

**Resource/licence assessment:** the Appetize GitHub Action is MIT-licensed, pinned to upstream `v1.1.0` commit `2a2c772c74955925c1a7575fea8e4d58056f547d` and uses Node 24. The service is proprietary and must be opted into separately. The runner uses JDK 17, Gradle 8.13 (basic cache) and Android API 36. An uncached build may download **hundreds of MB** of SDK/plugin/dependencies and use roughly **1–3 GB** of temporary space on the public GitHub runner (planning estimates, not measured). A development APK of roughly 11 MB is uploaded to Appetize, followed by streamed display traffic on the browser connection. The free plan's 30-minute allowance is a **provider-side quota**, not an unlimited guarantee. No local Android Studio, emulator image or APK download is involved. If Appetize limits or credentials block this, the established automated GitHub emulator tests and later physical handset test remain alternatives.

**GitHub caveat:** [manual workflow dispatch](https://docs.github.com/en/actions/how-tos/manage-workflow-runs/manually-run-a-workflow) requires the workflow file on the default branch. If PR #5 has not merged, the Run workflow option will not yet be available for this newly added workflow. This is not a failed cloud emulator; it is a GitHub workflow visibility prerequisite.

## Verified 3-test narrow-screen Android runtime milestone (2 Nov 2026)

On P02 source commit `5edcf321905d5646fbcffae6aa31e2a8481992b8`, [Android 15 emulator run 41451304469](https://github.com/cryogenized-spec/CiRYVA-01/actions/runs/41451304469) passed **all 3 instrumented Compose UI tests** (0 skipped, 0 failed) at **320dp display width and system font scale 1.5x**. The run reported `BUILD SUCCESSFUL` with 67 actionable tasks. It also **reinstalled the built debug APK**, launched `MainActivity`, captured a nonempty screenshot and uploaded the ZIP artifact **`ciryva-p02-emulator-large-text`** (artifact ID `13144154214`, roughly 92 KB zipped, 14-day retention). The native assemble/lint/unit tests, foundation tests, integrity and static analysis were also all green on this exact source commit (eight checks total).

**The emulator workflow was then restored to `workflow_dispatch` only**, so ordinary PR updates do not repeatedly fetch an Android system image. This documentation/trigger-restoration commit needs its own CI verification. It should not be described as having rerun instrumentation tests; it retains evidence from the passing source commit.

**Limits:** This is real CI emulator execution and a static screenshot, **not a live browser-accessible Android device**. Interactive browser testing requires the separate manually triggered Appetize workflow, user-provided `APPETIZE_API_TOKEN` secret, and P02 code to be merged on the default branch first. Appetize has never been invoked for CiRYVA; there is no live share link. No SDK/emulator/APK has been installed on the user's HP OMEN, and physical-phone acceptance remains outstanding.
