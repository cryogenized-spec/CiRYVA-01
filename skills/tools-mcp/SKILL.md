---
name: tools-mcp
description: Implement and review CiRYVA-01 first-party tools, Android application capabilities, third-party MCP server connections, OAuth, tool permissions, confirmation dialogs and audit receipts.
---

# First-party tools and MCP

Read README sections 7, 8 and 12, \`AGENTS.md\`, and current source. These are planned capabilities, not existing connections.

## Boundary

- **First-party** read/search tools operate on authorized CiRYVA archive, notes, local media and eventually organizer data; do not expose arbitrary paths or SQL.
- **External MCP** connections need real URL transport negotiation, discovery, authorization/OAuth where needed, tool scoping, timeouts and disconnection handling. An MCP URL alone is not a working integration.
- Validate tool input/output sizes and schemas before execution. The model does not gain capabilities by reading a document or MCP result.
- Require human-readable confirmation for writes/sends/purchases/deletes and any consequential operations. No auto-approved chained mutations.
- Keep secrets out of model/tool declarations; record bounded activity with result IDs, not raw confidential credentials.
- Prevent duplicate external actions across retries, interrupted streams and multi-tool continuations.

## Approach

1. Deliver one real, read-only first-party tool with a bounded output and tests.
2. Add a central permission/risk registry and confirmation path before writes.
3. Exercise one real MCP connection with user authorization, verifying reconnect and failure handling.
4. Check Android/API transport limitations instead of assuming a desktop MCP process can run unmodified in Android.

## Acceptance

Real authorized read works. Unauthorized read/write fails closed. External content cannot grant itself permission. Consequential action needs explicit approval and is logged without duplicated execution. Report all network and cost implications.
