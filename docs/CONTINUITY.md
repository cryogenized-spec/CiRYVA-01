# CiRYVA-01 — Engineering checkpoint

**Updated:** 2026-11-02 (SAST, UTC+02:00).
**Ground truth:** Current GitHub PR metadata, exact-commit CI, and source code supersede this checkpoint.

## Latest P04 9:16 chat redesign (verification in progress)

The user explicitly selected the ClipMint-inspired burgundy/rose-gold/Japanese ornamental direction, narrowed to a **9:16 native mobile chat** with no avatar taking active conversation space. Elara-Companion-current is the formatting reference, not a framework dependency. On draft PR #11 branch `feat/p04-live-byok-chat`, the new `CiRYVANativeChat.kt` gives an anchored composer, M3 navigation and native bottom-sheet model choices. `ChatMarkdown.kt` formats a safe Markdown subset **after** stream settlement, retaining all table columns. New provider `response.reasoning_summary_text.delta` events are separated from assistant text, displayed under an expandable Thinking **summary** panel only when the provider sends them, and capped at 8192 chars. No raw/private CoT is available. Output usage is provider-reported only; no paid request was authorized or executed.

The earlier **8/8 Android 15** emulator result (run `41520526253`) proves the older version, not this redesign. Revised code passes repository integrity and Kotlin foundation on source `99b11ae`, but Android instrumentation compilation failed on a redundant `assertExists` import. That import has been corrected in the next pending commit; **do not claim native CI or emulator pass until checked on the exact new SHA**. Codex review flagged expensive Markdown parsing per delta, an unscrollable model bottom sheet, and silent truncation of table columns; these were addressed. [P04 design and pending gates](P04-CHAT-REDESIGN.md). Keep PR #11 draft pending new checks, accessibility, and separately authorized live provider smoke test.


## Merged implementation baseline

- PR #1 Kotlin foundation, #2 master roadmap, #3 visual specification and #4 P02 Android preflight are merged.
- [PR #5](https://github.com/cryogenized-spec/CiRYVA-01/pull/5) P02 native Compose offline draft merged at `863577398cec6ba3fbf4757010c166707248d817`. Hosted Android 15 run `41451304469` passed 3/3 real UI tests at 320dp/1.5x, but no physical Android phone has been verified.
- [PR #6](https://github.com/cryogenized-spec/CiRYVA-01/pull/6) P03 Kotlin Responses HTTP/UTF-8 SSE streaming, model catalogue, validated generation settings, cancellation and token accounting merged at `48906a8e280f75bebecb834cf985252d8504c26d`.
- [PR #7](https://github.com/cryogenized-spec/CiRYVA-01/pull/7) P04 offline Compose chat-preview screen merged at `608aa88d94dcf422427e4171644a82a5d165a9da`. UI remains intentionally local-only; no fabricated assistant responses.
- [PR #8](https://github.com/cryogenized-spec/CiRYVA-01/pull/8) P04 AndroidKeyStore-encrypted device-owned API-key settings merged at `f596a23c584b5285b76c71afa5025f1f715f1584`. Fake-key Android 15 emulator run `41504134113` passed. OpenAI warns that keys in mobile applications are not secure against compromised devices.
- All four merge PRs are closed/merged and final `main` commit above had real native Android, Kotlin and repository CI green on 2026-11-02. No user key or paid API request was used.

## Active P04 live BYOK integration — draft PR #11

[PR #11](https://github.com/cryogenized-spec/CiRYVA-01/pull/11), branch `feat/p04-live-byok-chat`, built on merged `main`, introduces a separate optional native live-chat screen. The previous offline preview and draft editor are retained. User must save their OWN key in AndroidKeyStore, explicitly acknowledge charges and press Send to trigger a single HTTPS OpenAI Responses request. Network work runs off UI thread; `LiveChatController` prevents overlapping sends, cancels on background/lifecycle teardown, and only considers provider terminal completion real success.

Model selection uses the existing reviewed catalogue; optional settings include compatible reasoning effort, verbosity, temperature or top-p, and bounded output tokens. Provider reasoning summaries are **not yet rendered**; the summary control is purposely hidden after Codex's first P1 review. Same review raised an incorrect status on cancel-vs-complete race and unlabeled consent semantics: both addressed with deterministic regression and Row toggleable accessibility. Tests use fake credentials only.

**Verified latest runtime evidence:** The [Android 15 API35 emulator run 41520526253](https://github.com/cryogenized-spec/CiRYVA-01/actions/runs/41520526253) on source commit `553de442e8244ade50c2d50982f0b6b4689cfeb9` **ran 8/8 Compose instrumentation tests successfully** at 320dp and 1.5x font scale. The tested live UI used injected fake send callbacks and the real `LiveChatActivity`, not an external OpenAI request. The same source passed Android build/lint/JVM tests, Kotlin foundation and repository integrity. A clean Codex review preceded the test-host adjustments; fresh exact-head review/CI still needed after documentation/CI-trigger restoration. Any *real* OpenAI response remains **unverified**. This is not yet a production-ready AI chat APK. The user has not authorized a paid smoke test; do not make one. The live screen currently sends each request independently without previous conversation context, durable history, tools, persona or summary display; these belong to later milestones.

See [P04 Live BYOK acceptance plan](P04-LIVE-BYOK.md). The emulator UI acceptance passed with fake data. Keep draft until final review, real lifecycle/handset testing and separately authorized live-provider acceptance are complete.

## Follow-up gates

- [Issue #9](https://github.com/cryogenized-spec/CiRYVA-01/issues/9): genuine BYOK streaming-chat integration acceptance. Stay open pending real Android runtime and provider smoke test.
- [Issue #10](https://github.com/cryogenized-spec/CiRYVA-01/issues/10): active GitHub branch ruleset `26312198` still needs repository owner to require `Android build, lint and unit tests` in addition to integrity and JVM checks. Connected tools cannot edit rulesets.
- No real credentials in tests, public GitHub or logs. Do not install SDK/emulator/fonts on the owner's HP OMEN; use GitHub-hosted runner. Heavy Android emulator workflow remains manual-only.
