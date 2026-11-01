---
name: verification
description: Check CiRYVA-01 project work for real acceptance tests, safety of API credentials and MCP actions, bandwidth and dependency costs, source-versus-doc accuracy, version control and truthful release readiness.
---

# Verification and resource discipline

Read \`AGENTS.md\`, \`docs/CONTINUITY.md\`, \`docs/DECISIONS.md\`, relevant README contracts and affected files. Do not claim build, device, CI or API success without execution evidence.

## Before changing dependencies or building

- Quantify expected **download**, **unpacked install**, ongoing bandwidth, on-device storage, build cache, startup and runtime cost separately.
- Compare smaller alternatives and confirm licensing. Obtain user approval before material installs on their machine; never run \`npm install\`, Python pip, Android SDK download or model downloads casually.
- Verify API calls/tool usage may be separately billable even during complimentary token enrollment.
- No credentials in GitHub, screenshots, test fixtures, copied logs or assistant output.

## Review matrix

- Contract: no dummy UI, all visible controls work; no unapproved additions.
- Chat: streaming, Markdown final-only rendering, retry/cancel/recovery, local persistence, accurate usage.
- Context: bounded token budgets, no huge tool-result replay, no infinite tool loops.
- Security: permissions, OAuth, secrets, external prompt injection, duplicate writes, safe local memory.
- Android: device compatibility, IME, MediaProjection, camera permissions, restart and offline behavior.
- Repo: tests for actual code changes, documentation synchronizes with confirmed decisions, no conflicting PRs.

## Report format

State **which tests ran**, exact command/environment when known, pass/fail status, any unexecuted tests, side effects and remaining risks. Update \`docs/CONTINUITY.md\` after substantial verified work. Do not invent Gradle commands until a real Android project exists.
