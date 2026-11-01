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
- The authoritative CI contract is `docs/CI.md`. GitHub's ruleset **Mandatory CI test pass** (ID `26312198`) is confirmed active for default branch. It restricts direct changes through PRs and blocks force pushes/deletions; **however its `required_status_checks` array was empty** as inspected on 2026-11-01. Administrator must select `Repository integrity` and later `Foundation JVM tests` as required checks.
- Feature branch `foundation/kotlin-runtime-core` created from protected `main`. Added actual Kotlin JVM Gradle project, `core` source and JUnit 5 tests for SSE framing, generation state and token budgets; `.github/workflows/foundation.yml` runs tests and JAR assembly on GitHub-hosted runners. Source/documentation exists in PR branch, not main. See `docs/FOUNDATION.md`.
- No Android app code, local SDK installation, laptop dependency download or Android build has occurred. CI will download Gradle/JVM build dependencies on GitHub runners. No inference API calls were made.

## Next action

1. Inspect actual PR checks for branch `foundation/kotlin-runtime-core` and confirm **Foundation JVM tests** AND **Repository integrity** on that branch or PR. Resolve Kotlin compilation or test issues before approval/merge.
2. Set the required status checks in the active GitHub ruleset: `Repository integrity` and `Foundation JVM tests`. The ruleset exists but its required-check list was empty when checked.
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
