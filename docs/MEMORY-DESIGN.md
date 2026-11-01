# CiRYVA-01 — Memory and Recollection Architecture (proposal)

**Status:** Research/design only. Nothing here is implemented.  
**Aim:** Claude-like continuity and the ability to remember *small, meaningful nuances* without sacrificing local ownership, source accuracy or bounded API usage.

## Research and reuse boundaries

Claude's consumer memory system is **not provided as a drop-in open-source engine**. Its public documentation describes topic-based memory, project context, chat search and user correction controls. Anthropic's **client-executed memory tool** documents an instructive design: the assistant requests a memory operation, but the host app executes it on storage it controls. This is a general pattern we can implement independently with OpenAI model tool calls.

Potential open-source references, **not approved dependencies**:
- [Mem0](https://github.com/mem0ai/mem0) — Apache-2.0, memory extraction and retrieval; some managed-platform algorithms are proprietary. Python/server and embedding requirements require footprint review.
- [Letta / MemGPT](https://github.com/letta-ai/letta) — stateful-agent research and implementations. Do not import an entire server to achieve local Android memory.
- [Claude memory overview](https://support.claude.com/en/articles/11817273-use-claude-s-chat-search-and-memory-to-build-on-previous-context).
- [Anthropic client-side memory-tool documentation](https://platform.claude.com/docs/en/agents-and-tools/tool-use/memory-tool).

## Three authorities, no duplicated truth

1. **Immutable source archive:** full local conversations and attachments. A source ID, date/time, conversation/thread ID, speaker and (when possible) message span identify every memory's evidence. Edits/deletions must be handled explicitly; no fabricated quote.
2. **Recollection catalogue:** small, independently stored candidate records derived from sources or explicitly saved by the user; may be corrected, superseded, archived or deleted. Do not replace the original archive with summaries.
3. **Active turn context:** a temporary, bounded selection of relevant memories, recent messages and on-demand source excerpts. Never automatically inject all remembered material or entire tool outputs.

The activity log is a **fourth domain**, separate from memory: actual tool operations/approvals are inspectable but not automatically accepted as enduring personal facts.

## Capture small details without hallucinated permanence

Proposed memory record fields:
- \`id\`, \`topic\`, \`claim\`, \`category\` (preference, person, project, routine, event, goal, observation), \`scope\` (global, thread/project), \`sourceRefs[]\`.
- \`origin\` (explicit user assertion, observed conversation, authorized tool result, inferred hypothesis).
- \`createdAt\`, \`lastConfirmedAt\`, optional \`validFrom\`/\`validUntil\`, \`confidence\`, \`sensitivity\`, \`reviewStatus\`, \`supersedes[]\`.
- Distinguish exact assertions from model inferences. Low-confidence interpretations should be **searchable as hypotheses**, not silently promoted into definitive facts.

**Detail capture philosophy:** preserve exact anecdotes and subtle statements in the archive. Extract compact, context-rich memories when they may matter later. For example, an immediate preference (“not in the mood for coffee today”) must never be rewritten as a lifetime preference (“doesn't like coffee”). Store temporal context.

Use **user-configurable capture intensity** (suggested: explicit-only, selective, attentive); a user who wants high-detail recollection can choose attentive. More capture increases local storage, model-extraction calls, risk of stale claims and privacy exposure. No silent unlimited memory-processing loop.

## Privacy and user control

- The user can inspect, search, edit, pin, archive, export and delete individual memories. Explain which source message produced each memory.
- Consent controls for categories of sensitive information. Do not store passwords, API keys, access tokens, payment account numbers or private keys as memory under any mode.
- Thread/project boundaries and consent apply to retrieval; do not mingle separate project memories by default.
- Define exactly what deleting a source conversation means for its derived memories; avoid orphaned records that survive unexpectedly. Offer clear, consistent deletion semantics and test them.
- Avoid sending entire memory archives to OpenAI. The organization has opted into OpenAI input/output sharing; this is a known user preference, but the app must make transmission scope transparent.

## Retrieval and continuity

Start with an **index over the local archive + structured memory records**, not a remote database dependency.

- Keyword/date/topic search first; evaluate optional on-device full-text search and later embeddings only if proven useful and within size budget.
- Pull a limited number of relevant memory records for the current turn. Display sources and let user inspect details.
- Explicit **search archive** tool can retrieve more context on demand with strict excerpt/byte limits. This gives “remember every detail” *retrieval* without putting every detail in the active context window.
- Detect outdated/contradictory records; mark supersession rather than silently overwriting evidence.
- Support targeted questions (“What was that detail about the workshop?”) and broad recall (“What do you remember about this project?”) as separate retrieval modes.

### Example workflow

User says: “Last month I preferred to buy from supplier A when freight was cheaper.” The archive retains their words. An optional memory extraction records a *conditional purchasing preference* with source/date; it does **not** claim supplier A is always cheaper. At a later shopping query, relevant memory is retrieved with its condition; fresh price data still determines the recommendation.

## Runtime boundaries and safe tool operations

- Implement memory tools through CiRYVA's own **validated first-party tool registry**; no arbitrary filesystem paths or unconstrained SQL exposed to the model.
- Candidate functions: \`memory.search\`, \`memory.get\`, \`memory.propose_save\`, \`memory.propose_update\`, and \`archive.search\`. Exact names/contracts to be approved when implementation begins.
- A model can propose new memories. The application validates provenance, categorization, size/duplication and user's recording policy before persistence. User review is available, and sensitive changes require explicit consent.
- Enforce per-turn context and tool quotas; tool responses return excerpted text and source IDs, not full transcript dumps.
- Memory processing must not recursively trigger unbounded model calls, and interrupted extraction must not corrupt chat persistence.

## Initial implementation sequence (proposal, no stub modules)

1. Durable local conversations and attachments with reliable source IDs.
2. Archive search/retrieval with bounded snippets.
3. Explicit user-directed save/update/delete of source-linked memories.
4. Selective background or post-turn extraction **only after** explicit settings, usage costs, and quality tests are agreed.
5. Optional semantic similarity/indexes if measurable retrieval gaps justify dependencies.

## Required tests before calling it ready

- Correct source attribution and retrieval of small details.
- Contradictory/time-varying facts do not become permanent incorrect beliefs.
- User deletion and sensitive opt-in are honoured across storage and retrieval.
- Retrieval stays within token/byte budgets across huge archives.
- Duplicate/failed tool calls cannot create conflicting durable memories.
- Project scope isolation and local/offline archive access work.
- Data migration/restart and cancelled extraction do not lose transcripts.

## Open questions for the user

How attentive should automatic capture be by default? Should personal memories be global or project-scoped? How should deletion of a chat affect derived memories? Should on-device search support exact text only at launch, or a semantic index later? These decisions remain open.
