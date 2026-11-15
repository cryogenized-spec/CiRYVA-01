# CiRYVA Release-1 integration candidate — 15 November 2026 (SAST)

## Scope and provenance
- Candidate source branch: `integration/ciryva-r1-20261115`, initially at `151c8fe57ffa74b518c12b9255f06222f66416df` (theme PR #42 exact green Android/foundation/CI head).
- Contains features developed in draft PR #38 (BYOK `gpt-transcribe`), PR #40 (native GFM composer, attachments/OCR, integrated voice), PR #41 (System/Display, private wallpapers/fonts and truthful tool activity), and PR #42 (ten palettes plus validated custom hex).
- PR #39 (Accessibility Agent Mode) intentionally **excluded** from this candidate.
- PR #40 late recording-ownership / import transcript-restoration fixes were independently carried into PR #41 and inherited by #42; the two draft branches have divergent commit SHAs but the relevant `PendingImportedText.kt`, `AudioDraftOwnershipTest.kt` and `PendingImportedTextTest.kt` blobs match exactly.
- The one-line PR #41 Compose test-import correction is also already present on PR #42; PR #41 now has green Android, foundation and integrity CI.

## Release gates (not yet satisfied)
- [x] Candidate founded on a source tree whose GitHub source-level native Android build, lint, core tests, instrumentation test compilation and foundation checks passed.
- [ ] Candidate exact-head GitHub Actions and actual Android 15/API 35 emulator instrumentation suite pass, with failures triaged rather than waived.
- [ ] Verify composer, real IME, GFM, clipboard paste/hex, PDFs/images/OCR, dynamic theming, wallpaper/font persistence, rotation, process death, voice permissions and cancellation on an Android device.
- [ ] Separate security review: no silent key/API use, local media ownership, private data retention, stopped/cancelled requests, explicit transcription opt-in, zero fabricated activity/tool execution.
- [ ] Conduct owner-approved physical handset acceptance; obtain a separate explicit money ceiling before any billed real OpenAI smoke test.
- [ ] Final application-only release candidate has no temporary automated emulator-trigger workflow; maintain the repository's manual-on-demand default for heavy emulator runs.
- [ ] Review and reconcile PR ancestry before any promotion to protected `main`; do not merge drafts or claim production/alpha certification based solely on compile-only tests.

## CI and APK provenance
- PR #42 exact-head source verification: Android native build run `43666051312`, artifact `ciryva-p02-debug-apk` (debug only).
- Do **not** distribute this artifact as a signed stable release or claim its instrumented/runtime acceptance.
- The integrated candidate must have its own traceable artifact and full acceptance evidence before promotion.

## Constraints
Do not run paid OpenAI requests or upload user photos, recordings, API keys, personal documents, or sensitive data to CI. No remote device automation. Keep PRs #38/#40/#41/#42 as draft until independently reviewed and reconciled; preserve all branch-protection and review requirements.
