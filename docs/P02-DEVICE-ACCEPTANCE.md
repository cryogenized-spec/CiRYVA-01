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
