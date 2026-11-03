# P06 — Persistent model/persona preferences and provider usage

**Status:** Draft implementation on [PR #13](https://github.com/cryogenized-spec/CiRYVA-01/pull/13), stacked on P05 [PR #12](https://github.com/cryogenized-spec/CiRYVA-01/pull/12) and P04 [PR #11](https://github.com/cryogenized-spec/CiRYVA-01/pull/11). **Not merged or a public release.**

## Working controls in this P06 slice

The native Settings screen now offers a **Conversation mode** selector:

- **Standard assistant:** does not include persona `instructions` in the Responses request.
- **Custom instructions:** exposes a real, editable 1,600-character persona text area. Sending while empty is rejected with a validation message. The text becomes OpenAI Responses API `instructions` on a subsequently authorized Send.
- **Fictional roleplay:** exposes a separate editable 1,600-character scenario/character text area. Sends a clearly labelled roleplay instruction prefix, followed by the optional user-entered scenario. Custom persona text never silently leaks into roleplay mode, and vice versa.

Model choice, reasoning effort, verbosity, sampling controls, output ceiling and provider summary preference now survive Activity restarts using built-in Android `SharedPreferences`, validated against the implemented static model capability catalog. No new dependency is required. These are **global app preferences**; model or persona changes do not retroactively alter saved conversation text.

The Settings screen additionally aggregates **only actual provider-reported token receipts already stored in the local P05 archive**. Totals include completed and incomplete provider outcomes and exclude deleted records, requests without a receipt and unknown usage. This is **neither a live usage estimate nor an API billing statement**; model-specific costs and off-device usage are not inferred.

## Cost, authority and privacy

- No request is sent merely by editing a persona or opening Settings. `LiveChatController` continues to require an AndroidKeyStore-protected personal API key and an explicit one-request billing acknowledgement.
- A text-only user-authored persona of at most 2,048 characters can be included as an `instructions` field. Existing bounded six-pair local history, `store: false` and streaming/cancellation remain unchanged.
- Persona text and model preferences are app-private **but not encrypted at rest**. Do not enter passwords, private keys or sensitive information. The actual API credential stays separately in AndroidKeyStore.
- The roleplay prompt is user-visible and authored/selected by the user; it is not a fabricated hidden model chain of thought. Reasoning summaries, when requested and supplied, remain provider-authored only.

## Test and release boundaries

Included: JVM loopback HTTP tests for exact `instructions` payload and omission in Standard mode; validation of invalid instruction lengths; Android SharedPreferences restart/mode isolation tests; Compose consent/persona settings tests; Android SQLite provider-token sum tests.

Do not mark this phase fully verified until the **exact P06 code** passes Android build, lint and instrumentation and the actual Codex review is complete. No real OpenAI provider request is authorized by these tests.

**Remaining P06 work:** supported image/document attachments with true provider transport, local-media storage and permissions; separately tested appearance/typography preferences; richer per-conversation model configuration, history-aware usage/export and full release matrix. P07 includes physical handset, TalkBack, IME/rotation, consented live OpenAI API integration and signed internal build.
