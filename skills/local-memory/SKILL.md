---
name: local-memory
description: Design, implement or test CiRYVA-01 local chat persistence, source-linked nuanced personal memories, archive search, relevance-gated recall, deletion semantics, memory migrations and context retrieval.
---

# Local archive and memory

**Read \`docs/MEMORY-DESIGN.md\` before implementation.** Then read README sections 3, 10 and 11, \`docs/DECISIONS.md\`, and current source/tests. Claude's consumer memory is not open-sourced for copying; implement independently with locally controlled storage.

## Required separation

1. **Archive:** full local messages and media, source IDs and timestamps.
2. **Memory:** small source-linked observations and explicit remembered facts, preserving nuance, time, uncertainty and subject/project scope.
3. **Active context:** bounded relevant selections for a single model turn. Never inject the whole archive.
4. **Activity history:** tool receipts are separate from personal memory.

## Work procedure

- Begin with durable source archive, local search and exact evidence retrieval before automatic extraction.
- Specify schema and provenance: asserted vs inferred, source ID, timestamp, subject/topic, uncertainty, expiry/supersession.
- Offer inspect, correct, delete/export and sensitive-category controls. Prevent credential secrets from entering memory.
- Preserve distinctions such as "today" vs permanent preferences. When correcting a fact, retain audit provenance without reintroducing a stale claim into active recall.
- Index locally first; avoid embedding services/server dependencies until lexical retrieval is measured inadequate.
- Cap candidate records and excerpt sizes; log usage accurately. Avoid automatic model call loops for memory consolidation.
- Define and test chat deletion -> derived-memory behaviour before shipping.

## Acceptance

Recover a subtle past detail **with a source link**, distinguish historical from current claims, obey erase/opt-out, resist malicious prompt injection from archived data, survive restart, and keep retrieval within the configured token budget. No automatic memory feature may be claimed before these tests succeed.
