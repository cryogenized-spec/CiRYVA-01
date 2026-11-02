# P04 live BYOK chat — integration review gate

**Prepared:** 2026-11-02 SAST. Branch `feat/p04-live-byok-chat`, based on merged commit `f596a23c584b5285b76c71afa5025f1f715f1584`.

## What this patch implements

The existing P02 offline draft and P04 offline chat-layout preview remain functional. A **separate** native `LiveChatActivity` can be opened from the home screen for direct HTTPS OpenAI Responses streaming. Its explicit Send action passes a single user prompt and validated model-specific controls to the reviewed P03 `ResponsesHttpTransport` on a background executor, using the **user's own** encrypted AndroidKeyStore credential only after the user checks the API charge acknowledgement. There are no automatic requests at launch, no automatic retries and no Cloudflare proxy.

The selectable model registry is the reviewed static P03 snapshot; actual model eligibility depends on the user's project. Controls appear only when the model's catalogue permits them. Adjustable options include reasoning effort, answer verbosity, temperature **or** top-p, and up to 4096 output tokens. **Reasoning-summary controls are intentionally hidden** until the parser can separately capture and render provider summaries; no summaries are invented. The developer cannot guarantee a zero charge or any particular complimentary allowance. The output limit is not a fixed spending cap.

`LiveChatController` uses the same `ResponseTurnSession` generation identity and a single background worker. Only provider-confirmed terminal completion is represented as success; partial text is marked incomplete or cancelled, and usage is only provider-reported. Cancellation disconnects the active HTTP stream and prevents concurrent sends until the request worker exits. `onStop` cancels requests when the app leaves the foreground; `onDestroy` shuts the controller down. State is temporary, not a durable conversation archive.

The Activity declares `INTERNET` permission, requires no new external library, and the BYOK key is **not** passed through saved Compose state, Intent extras, diagnostics, application logs or test fixtures. Client-side custody is not absolute: OpenAI recommends server-side keys because a compromised client can reveal keys at runtime. Users should use separate restricted keys, rotate them and apply spend controls themselves.

## Current limitations and required acceptance

- Each Send is **independent**; earlier messages are not re-sent and no permanent conversation archive is implemented. P05 remains the durable archive phase.
- No cloud/physical Android runtime execution of the new live screen has yet been recorded, and **no actual OpenAI API request has been authorized or run**. The previous emulator verification applies to P04 preview and BYOK storage, not this new network screen.
- The model catalogue is a dated snapshot; the selected key may not have access to any chosen model, and unsupported combinations can still be rejected by the provider.
- Secret management is on-device, not a secure server; `store:false` is set by P03 transport where supported, but OpenAI usage/privacy settings and billing remain the user's responsibility.
- A rotation/background lifecycle test, **execution** of the newly compiled fake-only Compose consent screen tests, and an accessibility check at 320dp and 1.5x font scale remain prerequisites before calling P04 complete.
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

Initial Codex review of PR #11 identified that provider reasoning summaries could not be displayed, the status label could falsely say Completed if cancellation raced settlement, and the billing-consent checkbox lacked a TalkBack label. The summary selector was hidden, terminal status now prioritizes the authoritative settled cancellation state (with a deterministic race regression), and the charge acknowledgement uses a labeled Row toggleable with Checkbox role. The added fake-only Compose test asserts a key plus one-shot charge acknowledgement are both required. These corrections must pass exact-head CI and fresh review before merge.
