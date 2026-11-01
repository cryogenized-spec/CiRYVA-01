# CiRYVA-01

**Native Android AI assistant, organizer, and personal tool client**

> **Status:** Product planning / requirements capture — draft v0.2, 1 November 2026 (SAST).  
> **Implementation:** Not started. This README records the discussion and proposed approach; it is **not** a claim that any described feature is implemented.  
> **Repository:** https://github.com/cryogenized-spec/CiRYVA-01

## 1. Vision and scope

CiRYVA-01 is intended to be a **full-featured, locally oriented Android AI assistant** with a conventional chat experience, an organizer, multimodal inputs, a durable personal archive, optional memory, and extensible tools. It should be able to answer questions *and* help perform explicitly authorized actions on local data or connected services.

The organizer is a capability within the assistant, not the entire application. A key goal is to approach the convenience of an Android system assistant—particularly contextual screen selection and chatting about what is visible—without tying the product to Gemini.

**Generative AI provider: OpenAI only for the initial design.** The Google ML Kit OCR library may be used for on-device text recognition; that is separate from using Google/Gemini as a generative model.

This project is **not** the separate Elara Angelic Utility Applet repository. The two projects should not be conflated or share unreviewed architecture decisions.

## 2. Product principles

1. **Plan the whole feature set before development.** Establish coherent product flows, permissions, boundaries, and acceptance tests before choosing dependencies.
2. **No dummy implementations, dead-end templates, empty screens, or nonfunctional controls.** A capability appears in the shipped interface only when that vertical slice works. Future features remain in the specification, not in misleading UI.
3. **Local-first data.** Chats, images, attachments, and other relevant records should persist on the device without mandatory cloud synchronization.
4. **Reasoning and tool execution must be inspectable.** Display genuine model-provided reasoning summaries when supported, and separate them from a factual log of tool actions. Do not invent or claim access to the model's hidden internal chain of thought.
5. **Strict resource discipline.** Before installing any SDK, library, or toolchain, assess download size, installed footprint, ongoing bandwidth, costs, and lighter alternatives.
6. **Controlled autonomy.** Retrieval and harmless reads can be permitted; writes, sends, purchases, deletion, and other consequential external actions require clearly designed authorization and confirmation.
7. **Cost-aware by construction.** Context, attachments, tool results, token usage, and tool-call iterations must have budgets rather than growing unchecked.
8. **No unnecessary background activity.** The agent should not silently poll, record the screen, upload private information, or execute external actions.

## 3. Android user experience

### 3.1 Chat and threads

- Familiar conversation layout with the composer at the bottom, messages above it, and a model selector.
- Independent chat threads; create, reopen, rename, search, and delete conversations.
- Save messages and their associated images, documents, generated output, and metadata locally.
- Restore complete threads after an app restart. Plan export/backup and restore so the archive is not lost when changing devices or reinstalling.
- Show model identity and relevant generation status clearly; errors should not leave messages spinning indefinitely.
- Preserve conversation history when selecting a different model; determine the context required by the new model per turn.

### 3.2 Composer and attachment menu

The default composer has a text entry field at the bottom and an independent **paperclip** icon on the left. The paperclip opens a consolidated attachment menu, rather than scattering features across the screen.

Planned attachment sources:

- Local photos and image gallery.
- Camera capture.
- Documents and local files.
- **Scan text / OCR** from a document through the camera.
- Browse CiRYVA's local content library.
- Screen-region selection (also reachable from a system-wide assistant invocation, subject to Android permissions).

Attachments remain linked to their source conversation. When appropriate, OCR output is displayed for review before sending it as model context. Preserve the original source image for later inspection.

A voice-to-text control is a planned feature, but **must not be shown as functional until implemented and tested**.

### 3.3 Contextual screen selection

The proposed flagship interaction:

1. Invoke CiRYVA while another Android application is visible.
2. Display a compact assistant overlay.
3. Drag a finger to draw a rectangular selection on the screen.
4. Capture the selected image region with appropriate Android user permission.
5. Open or continue a CiRYVA conversation with the selected image attached as visual context.
6. Ask a question or request an authorized action concerning the selected content.

Investigate Android **MediaProjection** and overlay requirements carefully. User consent, foreground-service restrictions, protected/secure surfaces, Android-version differences, and Play Store policies may limit what the app can capture. Never promise unrestricted access to other apps.

## 4. OpenAI model selection

The model selector should enumerate **models actually available to the user's OpenAI API organization** and label their supported capabilities rather than blindly displaying an unverified static list.

Models mentioned during planning include **GPT-4o**, **GPT-4o mini**, and **o-series reasoning models such as o3**. The conversation also used informal names such as “Orion”; treat those as names to verify against official API model IDs, not guaranteed identifiers or entitlements.

Per model, inspect compatibility with text/images, reasoning summaries, tools/MCP, streaming, context length, and cost. Clearly disclose unavailable capabilities and offer a compatible option instead of failing silently.

**Important billing distinction:** ChatGPT subscription access, separately metered OpenAI API usage, and any complimentary API-token allowance are different arrangements. Complimentary API tokens depend on the organization's actual eligibility, enabled settings, eligible models, terms, and remaining allowances. Never assume API inference is free merely because ChatGPT is subscribed to or a locally hosted MCP server is connected to ChatGPT.

The initial AI integration is proposed around the **OpenAI Responses API**. Verify current endpoint, model, pricing, storage, continuation, and compatibility requirements during implementation.

## 5. Assistant identity and system instructions

Provide editable, selectable assistant instruction profiles (“system prompts” or developer-level persona configuration, according to the API's supported instruction hierarchy).

Desired controls include:

- A custom assistant identity and stable style across threads.
- Multiple user-selectable instruction presets, including practical/professional and more personable styles.
- Optional personality settings such as formality, warmth, humour, and playful expression.
- Clear distinction between user-written instructions, model/provider restrictions, and app-level security and tool permissions.

These are **prompt/personality controls**, not a purported API safety-disable switch. The user's preference is for an adult, conversational assistant that can handle ordinary mature, affectionate, or nuanced topics without needless moralizing, while remaining within applicable provider rules.

The assistant must remain accurate and competent regardless of the chosen character style.

## 6. Reasoning, streaming, and agent activity

### 6.1 Separate two UI surfaces

**Reasoning summary:** a collapsible, model-supplied summary or explanation when a compatible OpenAI reasoning model provides it. This is **not** a verbatim private chain of thought, and not every model supports summaries.

**Agent activity:** a separate, factual event timeline of what the application actually did, for example:

- Retrieved memory or local documents.
- Invoked a first-party tool.
- Called an external MCP server.
- Received and filtered a tool result.
- Requested confirmation before a mutation.
- Completed, failed, or cancelled an action.

Do not manufacture reasoning traces for models that do not supply them. Do not confuse model narration with proof of actual execution.

### 6.2 Streaming

Use supported Responses API streaming events for assistant output, completion state, errors, and tool-call construction. Render incremental text without showing raw protocol envelopes to users.

Only execute tools after their arguments are fully received and validated. Handle cancellation, network interruption, malformed tool arguments, provider errors, and retries without duplicate external mutations.

## 7. First-party tools and local library

CiRYVA needs a reviewed **app-wide first-party tool registry** in addition to third-party MCP integrations.

Candidate capabilities include:

- Search, list, and retrieve local conversation threads or selected messages.
- Search and retrieve local documents, images, screenshots, and recordings from CiRYVA's authorized library.
- Read or update notes, tasks, shopping lists, and organizer records with appropriate permissions.
- Retrieve memory entries and references to the original supporting conversations.
- Produce activity records and inspect prior permitted operations.

Do not grant broad filesystem access just because a model requests it. Each tool requires a defined schema, input/output validation, resource limits, risk classification, and permission boundary. The original local archive remains authoritative for its content.

## 8. External MCP connector manager

Provide a simple MCP connections screen where the user can enter:

- A display name.
- A remote MCP server URL.
- A description.
- Required authentication/authorization details through secure UI.
- Selected tools and permissions, including read-only or confirmation-required access.
- Connection state, test result, and disconnection controls.

**Notion MCP** was mentioned as an illustrative first integration. An MCP URL is not automatically a usable connection: some servers require OAuth, compatibility negotiation, discovery, transport support, and explicit user consent. Discover capabilities from the actual endpoint and fail clearly when unsupported.

The connector design should distinguish **remote hosted MCP services** from **local/device tools**. A direct Android connection to a local desktop/server is a distinct trust and networking problem from passing a remote MCP server URL into the OpenAI API.

External MCP responses are untrusted data. They must never be allowed to change the application's authority, silently request credentials, or self-approve writes. Record the actual operation outcome and avoid transmitting passwords, tokens, or sensitive private material into model-visible tool schemas.

## 9. OCR, camera, and voice

**OCR:** consider **Google ML Kit text recognition** for an on-device, free-to-use recognition path. Compare bundled and dynamically delivered models, supported scripts, download behaviour, device compatibility, and accuracy. Camera → capture → OCR → editable extracted text → attach to chat context is the desired flow.

**Voice-to-text:** required as a future feature, but may be deferred in the first working release. Earlier discussion favoured an economical Android speech-recognition path, optionally with a provider-based fallback; check costs, language support, offline availability, and permission UX before finalizing.

**Text-to-speech:** potential Android system TTS output without obliging the user to buy premium streaming voice.

No dummy microphone/camera/OCR buttons in a release.

## 10. Local storage and modular recollection

Keep three concepts separate:

1. **Conversation archive:** complete messages, threads, images, attachments, and associated metadata.
2. **Long-term memory/recollection:** a selectively maintained index of preferences, enduring facts, recurring activities, and relevant context, with source references and user controls to inspect, correct, and delete.
3. **Agent activity log:** actual tool calls, authorization decisions, outcomes, failures, and timestamps.

Memory is a **separate module** with a defined retrieval interface rather than a monolithic context blob. The user should be able to ask the agent to search the archive or recollect information when relevant.

Proposed Android persistence: structured local records in Room/SQLite, larger media as app-private files, with a backup/export strategy. This is a candidate architecture, not yet a locked implementation choice.

Original records must not be silently replaced by lossy summaries. Cloud sync, if introduced later, is optional and requires separate design and privacy decisions.

## 11. Context-window and token-budget management

This is a **critical requirement** based on a previous failure: conversations that invoked a few tools accumulated disproportionately large context, causing token consumption to explode.

**Invariant: the stored archive must never be automatically replayed in full as model context.**

Design a bounded context assembler that:

- Sets a per-turn maximum input/output token budget, plus optional financial limits.
- Includes the immediate user request, only necessary recent turns, and a limited number of relevant memories.
- Retrieves archived material on demand instead of appending entire records.
- Limits tool schema exposure to the tools relevant for that turn, where the model and API permit it.
- Caps tool result size and extracts bounded useful portions while retaining originals locally.
- Limits tool-call count, recursion/continuation depth, and repetition.
- Recognizes and stops loops, including repeated failing requests.
- Uses preflight token counting where available, recording actual usage afterward.
- Explores OpenAI's current context-compaction mechanisms, with clear compatibility and cost handling.
- Treats continuation mechanisms (including prior response IDs) as **state-management features, not an exemption from token charges**.
- Includes explicit errors or prompts before an operation would exceed the configured budget.

A reliable compaction policy should preserve decisions, user constraints, action outcomes, and unresolved tasks; not simply discard critical recent context to make a number fit. Full transcripts and source artifacts remain in local storage.

## 12. Privacy, permissions, and security

- Store API keys and OAuth credentials in appropriate device-protected storage; never in chat transcripts or tool logs.
- Consider \`store: false\` for API requests where compatible with the chosen continuation approach. Verify the provider's actual retention/data-sharing terms separately.
- Separate read, write, send, delete, financial, and other consequential tool permissions.
- Confirm consequential actions with a human-readable preview. Do not rely on model instructions alone.
- For screen capture, camera, microphone, and files, request only the Android permissions actually needed.
- Preserve a usable audit trail without collecting or duplicating unnecessary secrets.
- Treat third-party documents, pages, messages, and MCP output as evidence, not higher-priority instructions.
- Plan secure export, backup, deletion, and device-loss handling.
- Plan behaviour if offline, if a connector expires, if the API is unavailable, or if a request runs over budget.

## 13. Visual and technical direction

- **Platform:** native Android, mobile-first. Earlier conversations favoured **Kotlin and Jetpack Compose**; confirm final technical selection before implementation.
- **Icons:** **Iconify** preferred. On Android, investigate importing a curated set as vector/image assets rather than adding a web UI runtime. Check selected icon-set licences.
- **App structure:** coherent, testable subsystems for conversation, model transport, context assembly, memory, local library, permissions/tool registry, MCP, OCR, organizer, and UI.
- **Performance:** smooth everyday use on realistic Android hardware, with resource and bandwidth budgets.
- **No unnecessary dependency weight:** quantify SDK/asset size, cold-start impact, expected downloads, and alternatives before adopting components.

## 14. Proposed delivery slices

Each stage is independently functional and testable; the ordering is **provisional**, not a commitment to add inactive placeholders.

| Slice | Functional deliverable | Minimum acceptance test |
| --- | --- | --- |
| 1 | Native Android chat + configured OpenAI model + streaming + model selector | Real prompt receives a real, cancellable streamed response |
| 2 | Local threads, message persistence, and image/file attachments | Conversations and attachments survive app restart |
| 3 | Reasoning-summary display, tool activity trace, and token budgets | Real events and usage shown; runaway context/tool loops bounded |
| 4 | First-party local tools and one real remote MCP connection | Read local archive; authenticate to and use one authorized connector |
| 5 | Camera OCR and selected-area Android screen capture | Extract and use real OCR/screenshot context within consent rules |
| 6 | Recollection/memory module | Recall a stored fact with a reference, without replaying full archive |
| 7 | Working voice and organizer workflows | Successfully complete a real approved task with inspectable outcomes |

If a later feature is not available, keep it in the roadmap/specification rather than exposing fake UI.

## 15. Open design and implementation decisions

These remain undecided and must be resolved through planning or verified experiments:

- Exact launch-day OpenAI model IDs, entitlements, complimentary-token eligibility, and per-model capability matrix.
- How the Android client securely accesses OpenAI, and whether a user-owned backend is necessary for particular credentials and operations.
- Chat architecture for local-first history while using Responses API continuation, reasoning state, compaction, and streaming.
- Which reasoning summary formats are supported by each chosen model.
- Android version targets; overlay invocation, capture consent, and screen-selection UX.
- First-party tool list, exact schemas, permissions, user confirmation, and audit semantics.
- MCP remote transport, authorization flows, connection persistence, and timeout/retry policy.
- Memory extraction criteria, retention, retrieval/ranking, user editing, and archive export.
- OCR deployment choice (bundled vs downloaded recognition models) and supported languages.
- Voice transcription/TTS implementation and its cost/privacy model.
- Organizer data model and which features belong in the first release.
- Actual dependency download/install footprint and CI/build strategy.
- Final visual navigation, icon licence selection, and accessibility design.

## 16. Working agreement

**Planning first; coding later.** Keep architecture and product decisions explicit. Do not introduce stub features, dummy data, placeholder workflows, speculative integrations, unreviewed installations, or hidden recurring costs. When work starts, each committed feature should have a real acceptance test and honest status.

This README is the initial shared record of the discussion and is expected to evolve as feature decisions become definite.
