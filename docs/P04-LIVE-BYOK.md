# P04 live BYOK chat — integration review gate

**Prepared:** 2026-11-02 SAST. Branch `feat/p04-live-byok-chat`, based on merged commit `f596a23c584b5285b76c71afa5025f1f715f1584`.

## What this patch implements

The existing P02 offline draft and P04 offline chat-layout preview remain functional. A **separate** native `LiveChatActivity` can be opened from the home screen for direct HTTPS OpenAI Responses streaming. Its explicit Send action passes a single user prompt and validated model-specific controls to the reviewed P03 `ResponsesHttpTransport` on a background executor, using the **user's own** encrypted AndroidKeyStore credential only after the user checks the API charge acknowledgement. There are no automatic requests at launch, no automatic retries and no Cloudflare proxy.

The selectable model registry is the reviewed static P03 snapshot; actual model eligibility depends on the user's project. Controls appear only when the model's catalogue permits them. Adjustable options include reasoning effort, answer verbosity, temperature **or** top-p, and up to 4096 output tokens. **Reasoning summaries are now optionally requested** as `reasoning.summary=auto` for compatible models and rendered in a collapsible Thinking-summary area only when actual provider delta events arrive. Raw private chain of thought is not exposed or fabricated. The developer cannot guarantee a zero charge or any particular complimentary allowance. The output limit is not a fixed spending cap.

`LiveChatController` uses the same `ResponseTurnSession` generation identity and a single background worker. Only provider-confirmed terminal completion is represented as success; partial text is marked incomplete or cancelled, and usage is only provider-reported. Cancellation disconnects the active HTTP stream and prevents concurrent sends until the request worker exits. `onStop` cancels requests when the app leaves the foreground; `onDestroy` shuts the controller down. State is temporary, not a durable conversation archive.

The Activity declares `INTERNET` permission, requires no new external library, and the BYOK key is **not** passed through saved Compose state, Intent extras, diagnostics, application logs or test fixtures. Client-side custody is not absolute: OpenAI recommends server-side keys because a compromised client can reveal keys at runtime. Users should use separate restricted keys, rotate them and apply spend controls themselves.

## Current limitations and required acceptance

- Each Send is **independent**; earlier messages are not re-sent and no permanent conversation archive is implemented. P05 remains the durable archive phase.
- The new live-chat Compose screen **was executed on a hosted Android 15 emulator** with injected no-network callbacks; see the verified 8/8 run below. The tests do **not** verify a real provider request, a physical handset, or the full app-to-provider network path. No actual OpenAI API request has been authorized or run.
- The model catalogue is a dated snapshot; the selected key may not have access to any chosen model, and unsupported combinations can still be rejected by the provider.
- Secret management is on-device, not a secure server; `store:false` is set by P03 transport where supported, but OpenAI usage/privacy settings and billing remain the user's responsibility.
- The fake-only Compose consent tests now pass under 320dp/1.5x on Android 15. Additional **real live-screen** rotation/background lifecycle acceptance, model-picker interactions and device accessibility review remain before calling P04 complete.
- Device state is intentionally session-only; background/rotation cancels work to prevent duplicate or invisible chargeable requests. Do not claim preserved conversation history.
- No SDK/emulator installation on the owner's HP OMEN. GitHub-hosted CI handles compile, lint and unit tests; costly emulator runs are opt-in and must be separately verified.
- A real provider smoke test requires a key entered by the user on **their own device** and explicit approval of a maximum test budget. Never place real credentials in GitHub Actions, Issues, logs or chat.

## Verification checklist

1. `./gradlew :app:assembleDebug :app:lintDebug :app:testDebugUnitTest :app:compileDebugAndroidTestKotlin :core:test` on GitHub Linux/JDK17 with actual successful workflow logs, not merely workflow presence.
2. Instrumentation in a GitHub-hosted Android 15 emulator using no actual key; fake controller proves visible text, Cancel, disabled Send while active, settings validation and keyboard/viewport behavior.
3. Security/code review of Android key exposure, consent checkbox, request concurrency, process death, rotation, cancellation races and truthful provider receipts.
4. Confirm application still exposes its original offline preview and offline draft controls. Preserve manual-only emulator workflow; do not add unapproved heavy CI triggers.
5. [Issue #9](https://github.com/cryogenized-spec/CiRYVA-01/issues/9) remains open until device and explicit live-provider acceptance tests pass. [Issue #10](https://github.com/cryogenized-spec/CiRYVA-01/issues/10) tracks making Android CI a required branch status.

## Codex review correction

Initial Codex review of PR #11 identified that provider reasoning summaries could not be displayed, the status label could falsely say Completed if cancellation raced settlement, and the billing-consent checkbox lacked a TalkBack label. The summary selector was hidden, terminal status now prioritizes the authoritative settled cancellation state (with a deterministic race regression), and the charge acknowledgement uses a labeled Row toggleable with Checkbox role. The added fake-only Compose test asserts a key plus one-shot charge acknowledgement are both required. Those corrections passed exact-head CI and received a clean Codex review on the earlier reviewed source. The later instrumentation-host corrections must receive fresh final CI and review before merge.

## Verified Android 15 runtime evidence — 2 November 2026

- [Hosted API 35 emulator run 41520526253](https://github.com/cryogenized-spec/CiRYVA-01/actions/runs/41520526253) on actual source commit `553de442e8244ade50c2d50982f0b6b4689cfeb9` ran `:app:connectedDebugAndroidTest` with **8 started, 8 finished, 0 failures, 0 skipped** and `BUILD SUCCESSFUL in 3m 34s`, using 320dp width and 1.5× Android font scale. The P04 live-chat tests used `LiveChatActivity` with fake injected UI callbacks, **no stored real keys** and **no paid OpenAI requests**. The existing offline draft/preview and fake-key AndroidKeyStore regressions remained green.
- Two earlier runs exposed a stale forced-scroll assertion and a missing/incorrect Compose test Activity host. The test now asserts the inserted preview message through semantics without invalid scrolling, and the live screen uses the **real non-exported Activity**, replacing only its Compose content for fake-client tests. There is no test-only Activity or APK manifest addition after the corrections.
- The run uploaded screenshot artifact `ciryva-p04-emulator-large-text` (artifact ID `13177203519`). The script launches **MainActivity** for that screenshot; it is not a picture of the live-chat screen. Do not overstate this visual evidence.
- The temporary PR-triggered heavy emulator workflow was restored to the original `workflow_dispatch`-only version in the immediately following documentation/cleanup commit. No local SDK packages, fonts or emulator were installed.
- Remaining product gates: real Android lifecycle and keyboard/landscape accessibility across the live screen, manual usability, separately **explicitly authorized** user-owned provider smoke test and permitted spending ceiling, model/API entitlement checks and the repository-owner required Android CI status ([issue #10](https://github.com/cryogenized-spec/CiRYVA-01/issues/10)). Until those gates complete, PR #11 remains draft.

## Updated chat design and Thinking summaries

The user chose a cleaner 9:16 portrait chat based on ClipMint's burgundy/rose-gold visual identity and Elara Companion's conversation formatting. Current implementation: `CiRYVANativeChat.kt` + `ChatMarkdown.kt`, no large portrait in chat; model/effort selectors use M3 bottom sheets, advanced controls are in Settings, and the composer stays attached below scrollable chat. Streaming displays raw plain text; after settlement a bounded native Markdown subset is formatted (no HTML execution). `reasoning.summary=auto` remains user-toggleable on models with summary support; only `response.reasoning_summary_text.delta` appears in a separate expandable panel, capped at 8192 characters. See [the current design contract](P04-CHAT-REDESIGN.md). These changes require fresh CI, a hosted emulator check and final Codex review; prior 8/8 passing emulator run predates this redesigned UI.
