# CiRYVA-01

**Native Android AI assistant, organizer, and personal tool client**

> **Status:** Product planning / requirements capture — draft v0.8, 1 November 2026 (SAST).  
> **Implementation:** Pure Kotlin/JVM runtime core merged from PR #1; native Android app, Compose UI and APK not yet implemented or verified. This README remains a requirements specification.  
> **Repository:** https://github.com/cryogenized-spec/CiRYVA-01

**Development roadmap:** [P00–P17 master roadmap, dependencies and release acceptance](./docs/ROADMAP.md). See [CONTINUITY.md](./docs/CONTINUITY.md) for the verified handoff.

**Current P01 design review:** [Orbitron UI specification](./docs/UI-DESIGN.md) and [candidate design tokens](./design/tokens.json). Four rendered screen concepts still require user approval before P02 native UI code.

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


## 17. Expanded interface and presentation requirements (1 November 2026)

These requirements refine CiRYVA, not the separate Elara app.

### Navigation, portrait and appearance

- A top-left **hamburger** opens a left-to-right drawer with chat threads vertically listed and **Settings pinned at the bottom**.
- The optional **4:5 assistant portrait** sits at the top right of the 9:16 mobile chat surface. **Tapping it opens agent/persona settings.**
- Portraits can be chosen or imported locally. AI-generated portraits are deferred until image-generation costs and access are established.
- Separate assistant and user message styling: **selectable assistant font, text color**, accessible sizes and contrast, plus chat background (solid, gradient or user image). Background artwork should be suited to 9:16, locally cached and reasonably compressed.
- Offer a few offline fonts, plus **opt-in Google Fonts** fetched and cached once as needed, subject to download-size limits. Do not silently fetch font families.
- Iconify is the preferred icon source; for native Android, select and package vector assets rather than importing an unnecessary browser UI framework.
- Settings should group model, persona/system instruction, roleplay, appearance, API credentials, usage, memory and tool permissions.

### Markdown must not be reparsed throughout streaming

**Network streaming and Markdown layout are separate lifecycle stages.**

1. The composer stays plain editable text. Parse user-authored Markdown **after the user presses Send**, not on every keystroke.
2. Incoming model text may arrive as streaming deltas, but during generation it is shown in a lightweight **plain-text** view (or another stable unformatted progress representation).
3. **Parse and render completed Markdown once after generation finishes.** If cancelled or interrupted, treat any partial text as a terminal partial message and parse it at most once after termination.
4. Keep rendered prior messages cached/memoized so a new delta or new message does not reparse the whole archive.
5. Support appropriate Markdown/GFM-style formatting: paragraphs, headings, italics, bold, code, lists, blockquotes, safe links and eventually tables and math.
6. **Ignore raw HTML** within Markdown. Reject unsafe links and scripting. Preserve deliberate line breaks and escaped literal asterisks.
7. Monitor CPU/memory and long-chat scrolling performance in real Android tests.

Elara's source has a memoized Markdown renderer with GFM, raw HTML skipped, restricted elements and HTTPS-only links. Transfer that **design discipline**, not its React implementation.

### Roleplay and character controls

- Separate **Roleplay Mode** toggle. Only while enabled, reveal a **roleplay-specific editable instruction** field and include those instructions in the effective persona for the selected turn.
- Adopt conventional roleplay formatting: **asterisk-delimited actions appear in italics**, dialogue remains readable, and deliberate paragraphs/line breaks survive rendering. Avoid conflicting custom text markup.
- Keep ordinary persona instructions, fictional-world data, personal memories and message history separate. No hidden duplicate system-persona instructions.
- Consider Elara's persistently stored Roleplay World Canvas and confirmed world-edit tools **later**, not in the initial chat release.

### Camera, OCR and voice

- The camera action requires live preview, focus, an obvious **shutter**, **torch/flashlight** (manual plus automatic behaviour where supported), retake, and a user confirmation step.
- OCR inserts **editable recognized text** into the chat drafting flow and retains the captured image as its source.
- Voice-to-text should insert an editable draft **without automatically sending**. Live voice conversations and continuous camera/video sharing are low priority.

## 18. LaTeX/PDF, image generation and media scope

### BusyTeX / LaTeX documents

The compiler previously integrated into Elara is **TeXlyre-BusyTeX** (sometimes just BusyTeX), likely what was recalled as “Pizetex.” Elara uses it with LuaLaTeX and pinned assets in an isolated browser worker to produce PDF documents from bounded source.

For CiRYVA, plan an explicit **LaTeX source -> local PDF -> preview/export** operation, with errors and compiler logs bounded and no model-controlled shell escape, arbitrary file paths or arbitrary executable flags.

**Do not assume the browser/WASM BusyTeX package is a lightweight native Android dependency.** First inspect the compiled asset size, Android compatibility, on-demand downloads, cache behaviour, licences (including upstream AGPL obligations) and alternatives. Do not bundle heavy TeX Live data in the initial application by default.

Inline mathematical expression rendering in chat and actual **full LaTeX-to-PDF compilation** are distinct requirements.

### Deferred

- **YouTube** discovery, embedding, playback and policy/quotas: Elara has a large implementation, but not an MVP priority here.
- **Image-generation output** until an actual affordable or complimentary provider option is verified.
- **Live voice/video**, continuous camera stream, ornamental animations and unimplemented toggles.

## 19. API credentials and factual usage/cost reporting

The user's OpenAI organization is enrolled in the complimentary daily token programme for eligible traffic shared with OpenAI, and the user **explicitly wants to contribute** to improving models. Do not silently opt out of this setting or imply that all models and tools are free; eligibility, caps and overages still matter.

**Credentials:** An entered OpenAI API key should be displayed only as a masked status/optional **last four characters**, with replace/remove controls. No full key in app UI, logs, GitHub or model input. **Local storage does matter for security:** OpenAI explicitly recommends not deploying ordinary API keys inside mobile clients. Android Keystore-backed local storage can reduce casual exposure for a personal single-device/BYOK experiment but cannot make a mobile-distributed secret unextractable. Before wider distribution, plan a minimal user-owned backend/proxy to hold the production secret and enforce request budgets.

**Authoritative per-request usage:** OpenAI Responses results provide usage fields for input, output and total tokens, and sometimes cached/reasoning breakdowns. Use these **actual provider values**, usually after a response completes, rather than pretending to stream an exact live token counter. If the stream is interrupted before final usage arrives, mark it unavailable/estimated rather than zero.

**Organization reporting:** OpenAI documents API reporting endpoints for organization completions usage and costs. These require **administrative permission/credentials** and return aggregated historical data, not a live token ticker. An admin key must never be inserted into a native client. A bounded reporting service could present organization or project totals after authorization. Do not assume the reporting API exposes a directly queryable balance of complimentary daily tokens. Keep **CiRYVA per-request usage**, **organization totals**, **reported costs**, and **allowance estimates** distinct.

Apply per-turn token/tool limits, optional daily spend safeguards, and clear reporting of external tool costs. Organization totals can include non-CiRYVA projects and should not be confused with this app's ledger.

## 20. Lessons selectively reused from Elara

Reviewed: Elara current canonical documentation for UI, chat, character/roleplay, memory, voice, documents, media and persistence, and relevant actual implementation sources.

Useful design precedents:

- **Generation arbiter and cancellation semantics:** only one active elected generation may commit, a superseded response cannot overwrite a newer one, a stalled generation times out, and regeneration lineage is recorded.
- **One authoritative repository per domain** with durable migrations. Messages reference locally stored artifacts rather than copying bulky image/document bytes into chat records.
- **Memoized and safe Markdown**, separate appearance settings, carefully managed growing composer and Android keyboard geometry.
- **Roleplay mode** and user-written persona instructions without a second hidden persona; persistent world modelling can be optional.
- **Voice capture as draft insertion**, with cancellation, silence detection and no implicit Send.
- **Evidence-based memory:** separate factual records from observations, keep provenance, respect retrieval caps, allow user controls and reject credential leakage. Do not port Elara's entire multi-stage memory lifecycle into the MVP.
- **Action confirmation and strict tool boundaries:** external results are data, not authority; mutations require review; duplicate/replayed tool actions must not silently repeat consequences.
- **Bounded document compilation** away from the UI thread. Avoid unnecessary TeX and YouTube dependencies.
- **Real verification:** source and tests outrank prose; actual Android/device tests are essential for keyboard, overlay, camera and permissions.

Avoid copying Elara's Gemini execution, React/Vite PWA shell, entire Cloudflare Worker architecture, complex YouTube player and large bundled assets.

## 21. Next planning step: development skills

Before writing features, define small, testable engineering workstreams: native Android shell/navigation; OpenAI streaming/model/usage; local chat/archive; safe Markdown and math; persona/roleplay/appearance; tool/MCP authority; Android camera/overlay/OCR/voice; then memory, document generation and organizer actions.

These are **workstreams, not placeholder app modules**. Each stage should deliver working, tested behaviour with explicit storage, runtime-cost and download-size limits. Keep the MVP focused on a dependable everyday chat rather than prematurely shipping every Elara subsystem.


## 22. Persistent development-agent instructions and memory design

CiRYVA now keeps **development continuity** separate from the eventual **end-user memory engine**.

For new coding sessions start with [AGENTS.md](./AGENTS.md), read the brief [current checkpoint](./docs/CONTINUITY.md), consult the [decisions register](./docs/DECISIONS.md), then load one applicable [focused skill](./skills/session-continuity/SKILL.md) as directed by the skill routing table. An agent must inspect actual repository changes and tests before relying on a checkpoint; work is never marked verified merely because a planning document claims it.

The user's desired Claude-like memory design is explored in [docs/MEMORY-DESIGN.md](./docs/MEMORY-DESIGN.md). It emphasizes **full local source archives, fine-grained source-linked recollections, project/topic scope, user controls, and bounded on-demand retrieval**. Claude's consumer memory engine is not open source for direct reuse; openly documented memory-tool patterns and the independently open-source Mem0 and Letta projects are research references, **not installed dependencies or approved code imports**.

Seven agent skills exist for continuity, Android UI, OpenAI chat, local memory, MCP tools, Android media, and verification. They are Markdown instructions for future development sessions, **not app functionality**. Future agents must continue updating the handoff checkpoint after significant verified work. The repository still contains no implemented Android application at the planning stage.


## 23. Native Android codestack — chosen direction and pending validation

**Planning decision (1 November 2026):** Build CiRYVA-01 as a **native Android application using Kotlin and Jetpack Compose**. No Flutter, React Native, browser shell, or bundled web UI runtime for the core application. The initial AI provider remains **OpenAI only**. Do not import the Elara web app's React/Vite/Gemini architecture.

**Status of dependencies:** The list below is a **proposed technology baseline**, not a list of installed packages, resolved versions, tested dependencies, or an authorized download. Check current official documentation, licences, security posture and approximate download/installed size before adopting any dependency.

| Area | Preferred technology | Purpose and adoption rule |
| --- | --- | --- |
| Language and native UI | **Kotlin + Jetpack Compose** | Primary Android application and first working chat UI. |
| Design system | **Material 3, extensively themed** | Accessibility, controls and behavior; no generic stock-Material look. |
| State and asynchronous work | **ViewModel, Kotlin coroutines, Flow/StateFlow** | Lifecycle-aware screen state, streaming and cancellation. |
| Navigation | **Navigation Compose** | Chat, drawer, settings, persona and subsequent real feature screens. |
| Architecture | **Pragmatic MVVM + repositories** | Separate UI, model transport, storage, tools and permissions without premature abstraction. |
| Structured local storage | **Room (SQLite)** | Threads, messages, memory records, tool receipts, metadata and local indexes. |
| Small preference storage | **DataStore** | Theme, model, personality and UI preferences; **not** plaintext API-key storage. |
| Large local content | **App-private file storage** | Original images, recordings, OCR inputs and generated documents, referenced by stable IDs. |
| Network/streaming | **OkHttp + kotlinx.serialization** | Direct, bounded event streaming and validated OpenAI response/tool envelopes. |
| Images | **Coil for Compose**, if footprint acceptable | Cached local image loading and portrait/background rendering. |
| Camera | **CameraX** (later slice) | Live preview, focus, shutter, torch control and photo capture. |
| On-device OCR | **Google ML Kit text recognition** (later slice) | OCR is distinct from adopting Gemini as a generative provider. Verify dynamic vs bundled model size. |
| Speech transcription | **Android SpeechRecognizer** (later slice) | Transcribed text enters the editable composer; no automatic Send. |
| Screen-region capture | **Android MediaProjection + appropriate overlay/UI permission handling** (later slice) | Drag-select rectangular region with Android-compliant consent and protected-content restrictions. |
| Durable scheduled tasks | **WorkManager** only when needed | User-approved background jobs, not a default always-running agent. |
| Dependency injection | **Hilt preferred candidate; manual DI acceptable for first slice** | Adopt Hilt only when complexity justifies its dependency and build overhead; avoid a large framework solely for scaffolding. |
| Markdown | **Lightweight CommonMark-compatible parser with a Compose renderer** | Final-only, cached rendering. Parser implementation is not yet selected; raw HTML must stay inert. |
| Icons | **Curated Iconify assets as native vectors** | Check each icon set's licence; no runtime browser engine for icons. |

**Module organization:** Begin with a small, clear application structure. Logical areas are app/shell, core UI, local data, network/OpenAI, chat, settings/persona, and later memory, tools/MCP and media. These are **ownership boundaries**, not an instruction to immediately create nine Gradle modules or dummy feature folders. Split into physical modules only when it reduces actual coupling or build cost.

**OpenAI transport and security:** Use the Responses API with explicit budgets, provider usage metadata and cancellation. API-key presentation is masked; a personally controlled prototype may use Keystore-backed credentials, while production/public distribution requires an appropriate server-side secret strategy. Organization-admin keys are never packaged in Android. A chosen stack does not supersede these security rules.

**Performance invariant:** Stream incoming model text cheaply, and **parse completed Markdown exactly once when the generation reaches a terminal state**. Persist originals and source IDs; images are not duplicated into the database. Keep large media, models, voice and TeX runtime assets out of the MVP until a bounded, tested need exists.

## 24. CiRYVA visual identity — Industrial Futurist / Tactical Neo-Noir

**Approved visual direction (1 November 2026):** Dark mode, electric blue, obsidian black, aluminium/graphite grey and silver-white. The user explicitly selected **Orbitron** as the preferred futuristic display font. The design should feel like a precise, elegant, advanced personal operator console—not a generic messaging application, neon overload or distracting gamer dashboard.

### 24.1 Color tokens (proposed initial values for visual testing)

| Token | Hex | Role |
| --- | --- | --- |
| Obsidian | **#0A0C10** | Primary background |
| Deep graphite | **#11151B** | Secondary background |
| Anodized aluminium | **#151A21** | Primary cards/panels |
| Raised steel | **#1C232C** | Secondary surfaces |
| Silver white | **#EAF2FF** | Primary text |
| Muted steel blue | **#9FB0C5** | Secondary text |
| Electric blue | **#27B2FF** | Active control, selected item, interactive accent |
| Ice-blue glow | **#66D4FF** | Very restrained glow/focus accent |
| Muted accent | **#3E6D8A** | Secondary active outlines and inactive status |

These are **starting design tokens**, not a promise of verified contrast ratios. Final combinations require WCAG/touch-accessibility and real-device testing. Error and success colors must remain distinguishable, not simply decorative neon.

### 24.2 Typography

- **Orbitron — chosen display font** for app identity, major headings, section labels and selective hero numbers. Treat it as a deliberately limited accent, **not** the body font for long AI conversations.
- **Inter or Manrope — body-font candidates** for messages, editable inputs and settings. Select one through UI comparison and readability testing.
- **JetBrains Mono — technical-font candidate** for code, usage figures, status and tool logs.
- Prefer small, licensed, carefully subsetted/bundled fonts for offline essentials. Optional Google Font choices must be opt-in and cache locally after downloading, with visible data-size considerations.
- The user's font and color choices apply to rendered assistant text without mutating or regenerating stored message content.

### 24.3 Signature geometry and effects

Create an understated geometric motif library inspired by **X-shaped marks, diagonal intersections, crosshatch, faint grids, circuit-like lines, blurred/ghosted visages, chromatic offset and occasional glitch fragmentation**.

- Ambient patterns may decorate splash/header/persona surfaces and backgrounds at **low opacity**, not compete with readable chat text.
- Use subtle surface grain, a faint edge/bloom around selected controls, metallic separators and a carefully controlled blue highlight.
- A blurred visage or glitch motif is **decorative artwork**, not actual camera imagery, passive screen capture or implied user surveillance.
- Make effects **static by default**. Use brief, optional motion only where it clarifies transitions or status, and respect reduced-motion preferences.
- Prefer rendering with lightweight Compose shapes and curated local graphics over continuous GPU blurring, large animated assets or heavyweight shader dependencies.

### 24.4 Component shape and navigation

- Panels/cards: restrained **12–16 dp** corner radius; utility controls: tighter **8–10 dp** radius. Avoid excessively rounded or bubbly controls.
- Thin metallic/steel dividers, subtle dark elevation and electric-blue focus/active states.
- Minimum practical touch targets and clear focus/pressed states; readable primary/secondary hierarchy.
- Left drawer enters horizontally with a short, smooth transition; threads are vertically listed, Settings anchored at the bottom.
- Portrait is framed lightly or frameless at top right, with **4:5** source crop and access to persona configuration on tap.
- Mobile visual composition is developed in **9:16 portrait** first. Use edge-to-edge/insets correctly; never hardcode keyboard height.
- Composer remains at the bottom: paperclip/attachment menu left, flexible text input centre, voice control when implemented and send/cancel right. A system-wide screen-selection affordance is designed separately, subject to Android restrictions.
- Model selector and activity/reasoning controls must be visible without crowding the portrait or composer.

## 25. First visual review and approval gate

Before scaffolding actual feature UI, create and review a coherent visual specification for these **four real flows**:

1. **Main chat** — Orbitron identity, top-right portrait, model selector, finished Markdown, safe streaming state and fixed composer.
2. **Conversation drawer** — thread list, search/navigation and Settings at the bottom.
3. **Agent/persona settings** — portrait, name, system instructions, roleplay toggle and conditional editor, model preference.
4. **Appearance settings** — background image/color, fonts, message colors, accessibility and restrained geometric/glitch decoration.

Visual review should include a **mobile 9:16 layout**, light-touch interaction states, legibility over artwork, small-screen keyboard behavior, and a comparison of the actual font specimens. Do not equate an unimplemented mockup with a functioning feature.

**Build gate:** the user approved the overall dark/blue visual direction and [P01 PR #3](https://github.com/cryogenized-spec/CiRYVA-01/pull/3) merged. The first P02 native app is a real offline draft interaction, not a simulated OpenAI chat; actual Orbitron rendering and accessibility still require device validation. After Android scaffold/device verification, P03 implements secure OpenAI transport and P04 delivers a **real cancellable streamed conversation**, as specified in [the master roadmap](./docs/ROADMAP.md). Optional media, PDF, memory extraction, MCP and scheduling remain later work.


## 26. Continuous integration: planning-stage gates and Android admission

CiRYVA now has a real, dependency-free [GitHub Actions workflow](./.github/workflows/ci.yml), the [CI gate source](./scripts/ci_gate.py), [gate regression tests](./tests/test_ci_gate.py) and a detailed [CI contract](./docs/CI.md).

The **Repository integrity** status check runs on pushes to main, PRs targeting main, and manual dispatch. It checks required planning documents, working relative Markdown links, routed skill metadata, likely credential patterns, and Android source readiness. Its Python-standard-library unit tests verify the guard's expected failures as well as success cases.

**Verification status (2 November 2026):** `main` has already passed repository-integrity and Kotlin-foundation tests. The open [P02 implementation PR #5](https://github.com/cryogenized-spec/CiRYVA-01/pull/5) adds an authentic Gradle Wrapper, native Compose application, real Android build/lint/unit-test workflow and a debug APK. Android CI **passed** on implementation commit `8b3373ad48` and final documentation commit `7bc0c9fd` (verify live PR for any newer changes). This does **not** mean the APK has been tested on a handset; device/emulator verification is a separate gate. The Android build check is **not yet a required status on main** and must be added to GitHub Ruleset ID `26312198` before merging.

GitHub branch rulesets/required checks are an additional administrative setting: workflow creation alone does not enforce pull-request-only merges. See [CI contract](./docs/CI.md) for the precise rule to enable and to distinguish recorded status from repository write protection.

This setup adds **no laptop installations, SDK downloads or API inference charges**; jobs run on GitHub-hosted runners. Resource/performance and native UI quality gates will be expanded alongside real code instead of adding pretend tests before source exists.


## 27. First Kotlin foundation (development PR)

A **separate foundation branch** introduces a small, real Kotlin/JVM module before Android UI implementation. The work and limits are described in [docs/FOUNDATION.md](./docs/FOUNDATION.md).

It contains tested logic for incremental Server-Sent Events framing, stale/cancelled generation fencing, and conservative token-context reservations with a bounded tool-call count. These are **actual reusable algorithms**, not stubbed model/network or Android features. A real [Kotlin foundation workflow](./.github/workflows/foundation.yml) compiles the module, runs its unit tests and creates a library JAR on GitHub-hosted infrastructure.

**What remains after the Kotlin core:** The native P02 Android scaffold, genuine wrapper and one offline draft feature exist on an open implementation branch, but have not yet merged. OpenAI HTTP transport and model selector, conversation archive, attachments, MCP, memory, OCR and camera remain future phases. The visual-direction review was approved and merged separately in P01.

The repository administrator activated the **Mandatory CI test pass** ruleset. As verified on 2 November 2026, **Repository integrity** and **Foundation JVM tests** are both required on `main`. **Android build, lint and unit tests** exists on PR #5, but is not yet required; an administrator must add it before Android source merges. 


## 28. Verified delivery status and next implementation gate (2 November 2026)

The [phase ledger](./docs/ROADMAP.md) controls the execution sequence. **P00:** tested JVM foundation, governance and roadmap merged. **P01:** user-approved design direction merged, but actual bundled fonts, 9:16 layouts and accessibility remain subject to device verification. **P02:** first native Kotlin/Compose offline draft host built in [PR #5](https://github.com/cryogenized-spec/CiRYVA-01/pull/5), with functioning Save/Clear logic, unit tests, a real GitHub-hosted APK build and an optional [Android emulator test workflow](./.github/workflows/android-emulator.yml). P02 is **not called complete** until required Android CI protection and actual device/emulator validation are confirmed. **P03 onward has not begun:** secure OpenAI credentials and streamed provider transport precede the first real chat.

The project must remain local-first, Android-native and OpenAI-only as the initial generative provider. Preserve the original README feature scope; do not expose inert feature controls or mistake release gates for completed functionality. For current source evidence and limitations see [P02 implementation record](./docs/P02-IMPLEMENTATION.md) and [continuity checkpoint](./docs/CONTINUITY.md).
