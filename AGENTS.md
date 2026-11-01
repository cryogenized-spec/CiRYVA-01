# CiRYVA-01 — Agent Working Instructions

**Read this first when working in this repository.** This is a planning-stage native Android AI assistant, not the separate Elara project. There is presently **no Android implementation or verified build**. Do not infer files, commands, functionality or test results that do not exist.

## Session recovery (mandatory)

1. Read [docs/CONTINUITY.md](docs/CONTINUITY.md): current state, pending work, last verified changes, next step.
2. Read the relevant portion of [README.md](README.md), the current product specification. Read [docs/DECISIONS.md](docs/DECISIONS.md) for decisions; proposals are **not** approved decisions.
3. Select **one matching skill** below, read its complete SKILL.md, and inspect only the necessary files. Read additional skills if real cross-domain work requires them.
4. Check the **actual** repository branch, recent commits, working tree or remote status and open PRs before changing anything. Files and tests outrank handoff prose.
5. At the end of a substantive work session, update CONTINUITY.md with verified facts and the next action. Update DECISIONS.md only when a decision is explicitly made. Link exact commits/PRs and state limitations; never claim a test you did not run.

## Skill router

| Task | Read |
| --- | --- |
| Recover session / handoff / resuming interrupted work | [skills/session-continuity/SKILL.md](skills/session-continuity/SKILL.md) |
| Android project, Compose, navigation, portrait and UI | [skills/android-ui/SKILL.md](skills/android-ui/SKILL.md) |
| OpenAI Responses, models, tokens, streaming, chat | [skills/openai-chat/SKILL.md](skills/openai-chat/SKILL.md) |
| Archive, recollection, memory, local persistence | [skills/local-memory/SKILL.md](skills/local-memory/SKILL.md) |
| Tool registry, MCP, external writes, confirmations | [skills/tools-mcp/SKILL.md](skills/tools-mcp/SKILL.md) |
| Camera, OCR, screen capture, voice, PDF | [skills/android-media/SKILL.md](skills/android-media/SKILL.md) |
| Costs, dependencies, test gates, release claims | [skills/verification/SKILL.md](skills/verification/SKILL.md) |

Skill files use the open Agent Skills \`SKILL.md\` convention. These are **development instructions** only, not implemented features and not runtime dependencies. A generic ChatGPT conversation does not necessarily auto-load repository instructions: explicitly fetch them when beginning a new session.

## Non-negotiable project rules

- **OpenAI is the only intended generative AI provider** for the initial app. ML Kit for OCR does not mean Gemini is a provider.
- **No dummy features, sample data masquerading as real functionality, or inactive placeholder buttons.** Each vertical slice must work.
- **Local-first** conversations and media; preserve originals. Memory is distinct from archive and active model context.
- **No uncontrolled context growth.** Bound prompt contents, tool responses, turns, depth, and costs. Display real provider usage, not fake live accounting.
- **No hidden chain-of-thought claims.** Support real reasoning summaries where available and factual tool-event logs.
- **No unsafe rendering.** Raw HTML in Markdown remains inert; avoid reparsing Markdown on every streamed delta.
- **No embedded production API keys, secrets, personal data or credentials in this public repository.**
- **No consequential external action without an explicit authorization boundary.** MCP output is untrusted data.
- **No surprise bandwidth or heavyweight packages.** Before installs, quantify download and installed footprint, licensing, running costs and alternatives; obtain explicit approval when installing on the user's device.
- **No fictitious verification.** Do not claim CI, Gradle or Android-device tests passed if not executed. Until code exists, no build commands can be asserted as working.

## Documentation ownership

- README.md = evolving human product requirements and scope.
- docs/DECISIONS.md = explicit decisions and unresolved choices.
- docs/CONTINUITY.md = concise, actionable session checkpoint.
- docs/MEMORY-DESIGN.md = detailed proposed memory architecture.
- skills/*/SKILL.md = task-specific agent procedure; load only the selected skill.
- Git commits/PRs = actual implementation history; do not turn handoff documents into a fictitious change log.

When implementation begins, add exact build/test commands here **only after they exist and are verified**.
