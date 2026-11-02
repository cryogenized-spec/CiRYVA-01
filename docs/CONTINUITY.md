# CiRYVA-01 — Current Engineering Checkpoint

**Updated:** 2026-11-02, SAST (UTC+02:00).
**Source of truth:** GitHub PR states and CI runs override this checkpoint when newer.

## Merged baseline

- P00 Kotlin/JVM foundation PR #1, master roadmap PR #2, P01 visual direction PR #3 and P02 Android preflight PR #4 are merged.
- P02 native Android PR #5 was merged on 2026-11-02 at `863577398cec6ba3fbf4757010c166707248d817`. It supplies a genuine Jetpack Compose Android host with offline draft Save/Clear, a Gradle wrapper, real Android build/lint/JVM tests, and manual-only hosted emulator checks.
- GitHub Android 15/API35 [run 41451304469](https://github.com/cryogenized-spec/CiRYVA-01/actions/runs/41451304469) passed 3/3 instrumented Compose UI tests at 320dp and 1.5x font scale, including reinstallation and screenshot. P02 physical-phone validation, broader manual accessibility checks and browser preview are **not** complete.
- **Repository safety follow-up:** GitHub ruleset `26312198` still needs the owner to require the `Android build, lint and unit tests` status as well as existing integrity/foundation checks. Native CI did pass on the reviewed P02 head, but this additional branch-protection enforcement was not configured before the urgent merge. Do not portray the ruleset as already updated.

## P03 stream/model code

- [PR #6](https://github.com/cryogenized-spec/CiRYVA-01/pull/6) contains Kotlin-only HTTP Responses streaming transport, strict UTF-8 SSE decoding, generation-safe turn arbitration, guarded output token allowance, and per-model settings validation/tests. Its direct HTTPS client accepts a caller-owned key in memory only, and sends `store:false`.
- The current source includes a 2026-11-02 snapshot of supported model IDs and controls, not a guarantee of user-specific API eligibility, complimentary usage, or pricing. Preserve BYOK: every eventual user supplies and pays for their **own** API key.
- Actual HTTP integration into the Android UI, INTERNET permission, model picker and authentic Send/Cancel are **not** yet implemented or device-verified. Loopback tests never make paid OpenAI calls.

## P04 stacked UI and credentials

- [PR #7](https://github.com/cryogenized-spec/CiRYVA-01/pull/7): native Compose **offline** chat-layout preview, showing only user-authored locally entered messages; no fabricated assistant response, network, provider key or live Send.
- [PR #8](https://github.com/cryogenized-spec/CiRYVA-01/pull/8): AndroidKeyStore AES-GCM user-owned key custody, non-backup ciphertext, password-type keyboard and secure key-entry window; cannot guarantee protection against compromised Android or runtime interception.
- Hosted [P04 emulator run 41504134113](https://github.com/cryogenized-spec/CiRYVA-01/actions/runs/41504134113) completed successfully on PR #8's tested source, including fake-key rotation/removal and UI tests. Retain manual-only emulator trigger before merging any final branch.
- These are stacked features and must be integrated in dependency order with CI and reviews. Neither a live AI client nor a production-grade APK is demonstrated by this work.

## Next acceptance gate

- Finish protected reviewed merges for P03/P04, remove feature-only CI trigger exceptions, confirm exact merged GitHub CI, and record the current commit IDs.
- [Issue #9](https://github.com/cryogenized-spec/CiRYVA-01/issues/9) tracks the first genuine Android BYOK model/settings picker, user-authorized Send/Cancel, streamed assistant messages, and real provider receipts/errors.
- No API credentials in source, logs or automated tests; first paid live inference requires explicit user permission. Do not install Android SDK/emulator/fonts on the owner's HP OMEN without consent.
