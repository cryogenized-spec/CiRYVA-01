# P04 BYOK — native Android credential custody

**Engineering checkpoint:** 2026-11-02 (SAST). This module accompanies P04's **offline** Compose chat preview. P02 Android foundation and P03 Kotlin Responses streaming code are already merged. This feature is a secure-key settings panel, **not** an operational AI chat or permission for paid API inference.

## Behavior and threat model

CiRYVA is freely distributable **BYOK** software: each user provides and pays for their **own** OpenAI project API credentials. No developer-funded shared API key, mandatory Cloudflare proxy, or automatic inference is part of this feature.

- `AndroidByokKeyStore` generates a non-exportable AES-256-GCM key in AndroidKeyStore; stores only IV/ciphertext in `Context.noBackupFilesDir`; manifest sets `android:allowBackup="false"`.
- The native settings screen supports private key input, replace/rotation and removal. The saved value is never re-displayed. The key-entry activity uses `FLAG_SECURE`, and the keyboard uses password input flags with autocorrection disabled.
- Restart load, replacement with fresh ciphertext, deletion and absence of plaintext in the ciphertext file are tested with **fake example keys only**.
- AndroidKeyStore provides encrypted storage at rest. It does **not** guarantee hardware-backed keys on every device, nor protect a bearer token after an attacker compromises an Android process, keyboard, accessibility service or operating system. Users should make a minimally privileged dedicated OpenAI project key, rotate when needed and configure usage/spending controls on their own account.
- No network permission is introduced at this stage, and no credentials are sent to the CiRYVA developer, Cloudflare or OpenAI through this settings screen.

## Verified testing and limitations

Earlier [emulator run 41502914698](https://github.com/cryogenized-spec/CiRYVA-01/actions/runs/41502914698) passed five of six instrumented tests but detected a chat-preview visibility assertion failure. After an additional test repair, [Android 15/API35 emulator run 41504134113](https://github.com/cryogenized-spec/CiRYVA-01/actions/runs/41504134113) **passed** on source `a2dd64af51e26acd7523a2f410b44dab3129ad45`. This validates only the implemented fake-key and offline-screen tests; it does not establish physical handset behavior, hardware-backed storage or resistance to rooting. Codex subsequently reported no major findings on that source.

The heavyweight emulator workflow was previously temporarily triggered by pull requests for a focused test. That one-time trigger must **not** be merged; use `workflow_dispatch` only. Normal build/lint/JVM checks remain required for the exact reconciled PR and merge.

## Next integration

[Issue #9](https://github.com/cryogenized-spec/CiRYVA-01/issues/9) tracks the real user-authorized Send/Cancel transport wiring, model and sampling/reasoning controls, streaming UI, authenticated device-owned credential use, provider errors/receipts and careful disclosure of API charges. Do not embed production keys or run live automated inference tests in GitHub CI. The first live chargeable request requires the owner's explicit authorization and budget.
