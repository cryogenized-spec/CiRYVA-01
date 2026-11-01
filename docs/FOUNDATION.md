# CiRYVA-01 Kotlin runtime foundation

**Status:** Functional, testable Kotlin/JVM runtime code for future Android integration. Not an Android APK, an OpenAI client, a UI, or a complete chat application.

The foundation deliberately implements reusable, non-visual mechanics first. This respects the established visual review gate and avoids downloading an Android SDK or generating empty screens.

## Current capabilities

| Component | Purpose | Current test coverage |
| --- | --- | --- |
| **SseEventDecoder** | Frames UTF-8 text Server-Sent Events incrementally, handles split lines, CRLF, multiline data, optional IDs and comments | Split events, multiline/comments/IDs, terminal flush, oversized frames |
| **TurnCoordinator** | Fences active generation by request ID so late events and cancelled turns cannot overwrite the current message | Supersession, cancellation, terminal errors, output length bounds |
| **TokenBudget** | Pure arithmetic enforcing model context reservations and a tool-call limit | Boundary values, invalid reservations, negative inputs |

The SSE decoder receives properly decoded text. The future HTTP adapter must supply a **stateful UTF-8 stream reader** and a JSON event parser. It must handle Responses API event types, tool-call schemas, actual usage fields, cancellation and errors. None of those integrations are claimed here.

The token budget expects **real token counts**, not character length. It is a safety boundary for future OpenAI requests, **not** a live billing calculator.

## Build and test — GitHub-hosted only

Workflow: [Kotlin foundation CI](../.github/workflows/foundation.yml).

Kotlin JVM plugin 2.2.21, Gradle 8.13, JDK 17, and JUnit Jupiter 5.12.2. Workflow runs **gradle :core:test** and **gradle :core:jar**. There is no Android app yet, so no Android SDK, emulator or Android Gradle Plugin is downloaded.

**Network footprint:** Gradle 8.13's binary distribution is about 131 MB compressed, plus Kotlin/JUnit dependencies and build caches on GitHub-hosted runners. No packages are installed on the user's metered laptop.

## Scope boundaries

- The root project currently includes only the **core** JVM module. Compose UI and an Android app are **not** present.
- Never create dummy login/chat/memory/camera/settings controls merely to appear complete.
- Do not embed API credentials or make paid model inference calls in these tests.
- Adding Android source requires the actual Android project, Gradle wrapper, and a genuine workflow executing **assembleDebug**, **lintDebug**, and **testDebugUnitTest**.
- The approved visual direction remains Orbitron, obsidian, electric blue, silver, aluminium and restrained geometric/glitch texture. Screens need visual review before Android UI implementation.
- Core's Android compatibility must be checked by a real Android Gradle build later; JVM tests alone do not establish it.

## Next acceptance milestone

After this PR's JVM CI passes, review the 9:16 UI design; then implement a small real Android chat slice with a complete wrapper, Android CI, actual streaming and local persistence. No unfinished UI controls.
