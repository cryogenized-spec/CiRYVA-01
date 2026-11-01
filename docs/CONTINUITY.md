# CiRYVA-01 — Current Session Checkpoint

**Updated:** 2026-11-01 (SAST).  
**Status:** Planning and developer-agent instruction setup completed. **No Android source code or verified APK.**  
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
- README contains planning specification v0.4 with 22 sections; last observed README update commit `f5c71acaf9fcc7270c1d75c27cc4b339e77f464a`.
- No Android implementation or known existing open PRs at checkpoint start.
- Root `AGENTS.md`, seven skills under `skills/*/SKILL.md`, `docs/DECISIONS.md`, and `docs/MEMORY-DESIGN.md` were committed and individually fetched successfully on 2026-11-01. No app code was written, no dependencies installed, and no build/test commands run.

## Next action

1. Confirm the current GitHub HEAD, agent skill routes and user approval before beginning implementation.
2. Review the user's priorities; next work is a **small, verifiable first Android slice**, not the complete feature set at once.
3. Before writing code, establish the Android toolchain/test strategy and estimated dependency footprint. Do not install anything without approval.
4. For eventual memory implementation, resolve open choices in `docs/MEMORY-DESIGN.md` (capture policy, consent, retrieval limits, deletion, storage, expense).
5. After a significant task, replace this checkpoint with verified changes, tests and next action.

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
