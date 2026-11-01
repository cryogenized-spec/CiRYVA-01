---
name: openai-chat
description: Implement and verify OpenAI Responses API chat, available model selection, reasoning summaries, streaming, per-request usage reporting, budget caps, cancellation, and reliable turn continuation for CiRYVA-01.
---

# OpenAI chat / transport

Read README sections 4, 6, 11 and 19, plus \`AGENTS.md\` and \`docs/CONTINUITY.md\`. Check current OpenAI API documentation on every material change: model IDs, response event schemas, reasoning summary support, usage, pricing and compaction are version-dependent.

## Invariants

- OpenAI is the initial **only** generative provider. Do not silently add Gemini.
- Model selector reflects confirmed models and supported features; no made-up entitlements.
- Separate user-facing assistant text, optional **reasoning summary**, and a factual tool-activity log. Private chain of thought is unavailable and must not be fabricated.
- Preserve final message and token usage returned by the provider; no fabricated live exact counter. If incomplete due to cancellation, label usage unknown.
- Stream text as deltas without expensive Markdown reparsing on each delta. Parse complete final output only.
- Bound context and cost before every request, restrict tool results/depth/iterations, and keep full archives out of default model input.
- Abort or supersede a generation without allowing late responses to overwrite newer saved state; idempotent external tool continuations.

## Steps

1. Write a focused transport contract and an actual test against a permitted environment.
2. Validate streaming event ordering, interruption, network errors, safe argument completion, retry and usage receipt.
3. Add per-turn/period token ceilings and expose real warnings at the boundary.
4. Check credential architecture: **do not embed production or admin API keys in Android**, even if masked.

## Acceptance

With an available authorized API model: a real prompt streams safely, saves a final message, reports real usage, survives cancellation/retry, and never sends the entire archived chat or massive tool responses by default. Report any unexecuted tests.
