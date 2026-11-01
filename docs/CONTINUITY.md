# CiRYVA-01 — Current Session Checkpoint

**Updated:** 2026-11-01 (SAST).  
**Status:** Master delivery roadmap drafted on separate documentation branch. Main remains planning-only; first JVM core is in open PR #1. **No Android APK or native UI verified.**  
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
- README now contains planning specification **v0.6, 26 sections**, adding the actual repository CI contract; recent README update commit `f84b1283c3be4b353ccb09e7ed061bff8b054155`.
- No Android implementation or known existing open PRs at checkpoint start.
- Root `AGENTS.md`, seven skills under `skills/*/SKILL.md`, `docs/DECISIONS.md`, and `docs/MEMORY-DESIGN.md` were committed and individually fetched successfully on 2026-11-01.
- `docs/DECISIONS.md` records the selected visual identity and preferred native Android direction; its update commit is `0d6562eb3748cf855ac9e6d8bb2094c61e28953a`.
- CI workflow `.github/workflows/ci.yml` and `scripts/ci_gate.py` plus `tests/test_ci_gate.py` are committed. The initial GitHub Actions **Repository integrity** job completed successfully at commit `0e4846c275b3bb0d9bed0c8b49a7eb4142c8425e`, run `41308781532`. All four workflow checks and the gate unit-test step showed success. Later doc commits may have newer runs; inspect them before stating final HEAD is green.
- The authoritative CI contract is `docs/CI.md`. Current GitHub ruleset `Mandatory CI test pass` (ID 26312198) is active; `Repository integrity` is now explicitly required, while the Kotlin `Foundation JVM tests` check still needs selection as required.
- No Android app code, SDK installation, dependency download or Android build has occurred; GitHub-hosted CI only. No inference API calls were made.
- The proposed [master roadmap](ROADMAP.md) is now committed on branch `docs/master-roadmap` (P00–P17). This is planning work only; it does not merge Kotlin PR #1 or create an APK.

## Next action

1. Inspect the **latest GitHub Actions run** for the current repository HEAD and ensure `Repository integrity` is green; resolve any regressions before starting Android code.
2. In GitHub repository Settings, enable a **main-branch ruleset** requiring pull requests and the existing `Repository integrity` check; this remains unconfigured unless verified separately.
3. **Visually review the four UI flows** in README section 25, using Orbitron; approve screens before implementing them.
4. Confirm Android API target, toolchain cost/size and actual build/test workflow before first Kotlin/Compose source. Include `.github/workflows/android.yml` running real assemble/lint/unit tests in that initial Android PR as required by `scripts/ci_gate.py`.
5. Keep the memory decisions in `docs/MEMORY-DESIGN.md` for later; update this checkpoint after verified milestones.

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
