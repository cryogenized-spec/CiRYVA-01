# CiRYVA-01 — Current Session Checkpoint

**Updated:** 2026-11-01 (SAST).  
**Status:** First functional Kotlin/JVM runtime foundation is under development in a feature branch with genuine unit tests. **No Android source code or verified APK.**  
**Authority:** Actual GitHub files/commits and tests outrank this checkpoint. Confirm current branch and latest files before acting.

## Project objective

Build a native Android local-first OpenAI assistant and organizer. The user wants familiar chat threads, custom persona and optional roleplay, attachment/camera OCR, contextual Android screen selection, native tools and external MCP, memory, voice input, usage/cost accounting, and eventually PDF generation. Do **not** conflate this with Elara.

## Confirmed requirements

- **OpenAI only** for generative AI initially; model selector must reflect real API entitlements.
- **No dummy screens or placeholders** and no unapproved downloads or heavyweight frameworks.
- Chat messages and attachments persist locally; memory and tool logs are separate.
- Stream assistant raw text, but parse/render Markdown only after generation ends. Treat HTML as inert; preserve safe formatting.
- The UI uses Iconify assets, a left drawer with Settings at bottom, top-right tappable assistant portrait, customizable fonts/colors/background, plain composer with paperclip, future voice input.
- Optional roleplay mode with separate instructions, asterisk-emphasized actions and good line breaks.
- On-device camera OCR/screen-region capture subject to Android permissions and consent.
- OpenAI response usage accounting; protect API keys; enforce usage/tool budgets.
- User has voluntarily enrolled their OpenAI organization in the eligible shared-traffic complimentary token program; this does not guarantee all requests/tools are free.
- Desired **Claude-like nuanced recollection**: capture small, useful context, preserve exact archive source, retrieve relevant memories, provide user review/correction/deletion. Do not equate full recording with sending full history every turn.

## Current verified repository state

- Repository: https://github.com/cryogenized-spec/CiRYVA-01 , default branch main.
- README in this branch contains planning specification **v0.7, 27 sections**, with the new Kotlin runtime foundation. Its last observed README update commit was `90d314e542c50dab15c006ebb2d3462eab25379a`.
- No Android implementation or known existing open PRs at checkpoint start.
- Root `AGENTS.md`, seven skills under `skills/*/SKILL.md`, `docs/DECISIONS.md`, and `docs/MEMORY-DESIGN.md` were committed and individually fetched successfully on 2026-11-01.
- `docs/DECISIONS.md` records the selected visual identity and preferred native Android direction; its update commit is `0d6562eb3748cf855ac9e6d8bb2094c61e28953a`.
- CI workflow `.github/workflows/ci.yml` and `scripts/ci_gate.py` plus `tests/test_ci_gate.py` are committed. The initial GitHub Actions **Repository integrity** job completed successfully at commit `0e4846c275b3bb0d9bed0c8b49a7eb4142c8425e`, run `41308781532`. All four workflow checks and the gate unit-test step showed success. Later doc commits may have newer runs; inspect them before stating final HEAD is green.
- The authoritative CI contract is `docs/CI.md`. GitHub ruleset **Mandatory CI test pass** (ID `26312198`) is active for default branch and requires pull requests while prohibiting force pushes/deletion. **Repository integrity** is now required. **Foundation JVM tests** passes but is not yet a required check.
- Feature branch `foundation/kotlin-runtime-core` created from protected `main`. Added actual Kotlin JVM Gradle project, `core` source and JUnit 5 tests for SSE framing, generation state and token budgets; `.github/workflows/foundation.yml` runs tests and JAR assembly on GitHub-hosted runners. Source/documentation exists in PR branch, not main. See `docs/FOUNDATION.md`.
- **Open pull request #1 (ready for review)**: https://github.com/cryogenized-spec/CiRYVA-01/pull/1 . Kotlin runtime foundation is proposed for merging into main after checking required statuses.
- **Verified build (2026-11-01):** PR-head commit `a6115d330aafe02a8b173d1b5871a5bcca47c473` had `Foundation JVM tests` **success** (run `41312880033`; Kotlin compile, JUnit tests, JAR assembly all passed) and `Repository integrity` **success** (run `41312885654`). A later documentation checkpoint commit may trigger new runs; check latest SHA before merge.
- The initial PR attempt failed `Repository integrity` because Android readiness incorrectly treated a pure JVM Gradle settings file as evidence of an Android app; corrected `scripts/ci_gate.py` and added a regression test for the JVM-only case.
- No Android app code, local SDK installation, laptop dependency download or Android build has occurred. CI downloaded Gradle/JVM build dependencies on GitHub runners. No inference API calls were made.

## Next action

1. Review **PR #1 (ready for review)** and verify both **Foundation JVM tests** and **Repository integrity** on its latest HEAD. Commit `e603599216487f6c5f56b6f2aa8a84e4c0ee630f` passed both checks: Foundation JVM tests runs `41313058025` and `41313054322`, Repository integrity run `41313058026`. Later documentation commits require re-verification.
2. `Repository integrity` is confirmed as required in GitHub ruleset `26312198`. Add the passing `Foundation JVM tests` check as another required check before merging Kotlin code.
3. **Visually review the four UI flows** in README section 25 using Orbitron; approve screens before building native UI.
4. Before adding Android app code, decide Android API levels, estimated SDK/dependency footprint and add a real Gradle wrapper and workflow executing `assembleDebug`, `lintDebug`, `testDebugUnitTest`.
5. Update this checkpoint with verified PR and workflow conclusions. No claim of Android success until actual build evidence exists.

## Session handoff template — replace/update after each milestone

**Date/time (SAST):** 
**User objective:**
**Current branch/head commit:**
**Files actually changed:**
**Evidence/tests run (results, limitations):**
**Decision made (link DECISIONS.md if any):**
**Incomplete work and blockers:**
**Next exact step:**
**Side effects / installs / network spending:**

Do not include secrets, raw personal conversations, authentication tokens, sensitive workplace data or unverified test statuses in a public repo checkpoint.
