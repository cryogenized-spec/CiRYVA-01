# P04 BYOK on-device credential custody

**Draft stacked branch:** `feat/p04-byok-keystore`, built on the still-unmerged P04 chat-preview PR, not yet the P03 HTTP transport.

CiRYVA is free, distributable **BYOK**: each user owns their OpenAI API key and is responsible for charges. This Android prototype uses a non-exportable AES-256-GCM key in `AndroidKeyStore` and stores only the ciphertext/IV in `Context.noBackupFilesDir`. It uses no additional runtime dependency and never transmits user credentials to the CiRYVA developer or to Cloudflare. The key is not embedded in the APK, GitHub files, logs or ordinary SharedPreferences. `FLAG_SECURE` blocks screenshot/recents capture on the key screen. No OpenAI request is currently made by this feature.

The settings screen accepts keys and can remove them; it only reports stored/not stored, never re-displays the secret. It clears its unsaved input on configuration recreation. The existing Android manifest has `android:allowBackup=false`, and the ciphertext file additionally lives inside `noBackupFilesDir`. An app reinstall or AndroidKeyStore reset loses the key and requires re-entry. A rooted device, malicious OS or compromised app can still expose credentials in use; this is **not** equivalent to an isolated server secret. Users should use restricted, rotating project credentials and configure spend caps.

## Acceptance

- Run Android instrumentation tests with fake example keys only; test restart load, encrypted bytes, and clear/delete. No real key or paid provider traffic is required.
- Test on API 35 with accessibility font scaling and keyboard, then request Codex security review of key storage paths, exception handling, Android backup, screenshot restrictions and secret lifetime.
- Treat this as credential custody only. Integrating `ResponsesHttpTransport`, model-capability settings and real streaming into P04 remains dependent on P02 and P03 reviews/merges and explicit live-call authorization.
