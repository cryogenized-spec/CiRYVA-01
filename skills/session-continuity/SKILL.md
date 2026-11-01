---
name: session-continuity
description: Recover, audit, and hand off CiRYVA-01 development across chat context limits, agent changes, interrupted sessions, or stalled tasks. Use before resuming prior work or ending a substantive work session.
---

# Session continuity

## Recovery procedure

1. Read root \`AGENTS.md\`, then \`docs/CONTINUITY.md\`. These are indexes/checkpoints, **not** proof of what exists.
2. Consult \`docs/DECISIONS.md\` and the relevant README requirements. Load **one** additional topic skill if needed.
3. Check GitHub/repository files and current branch, latest commits, open PRs and actual test evidence. If repository state contradicts the checkpoint, repository state wins.
4. State the last **verified** work, exact unresolved problem, next smallest testable action and any expense/install approvals needed.
5. Avoid broad re-reading of unrelated documents and never import unrelated private conversations into public repository context.

## Before implementation

- Identify user request and acceptance test. Mark unresolved architecture as proposed.
- Check prior branches/PRs and do not overwrite concurrent work.
- Estimate dependencies, bandwidth and test environment before touching the user's laptop.

## Handoff update

After a meaningful change, update \`docs/CONTINUITY.md\` with:
- Date in SAST and current branch/head commit if known.
- Intent, files changed, checks actually run, failures, expenses/downloads, incomplete work and next exact action.
- A reference to changed decision records, if applicable.

Do **not** write speculative success, private identifiers, secrets or personal memory into the public GitHub repo. Keep the checkpoint short; detailed chronology already exists in Git commits.

## Continuation acceptance test

An independent agent with **only repo access** can read AGENTS.md -> CONTINUITY.md -> one skill and identify what was actually finished and what to do next without a fabricated understanding of prior chats.
