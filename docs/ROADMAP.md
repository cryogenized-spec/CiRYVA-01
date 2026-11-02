# CiRYVA-01 — Master Product and Engineering Roadmap

**Roadmap revision:** 0.1 — 1 November 2026 (SAST)  
**Status:** Proposed delivery sequence, not blanket approval to install dependencies, access services or ship features  
**Product:** Native Android, local-first OpenAI assistant/organizer  
**Source of feature truth:** [README](../README.md)  
**Decisions:** [Decision register](DECISIONS.md)  
**Agent handoff:** [Continuity checkpoint](CONTINUITY.md) and [AGENTS.md](../AGENTS.md)  
**CI contract:** [CI](CI.md)  
**Memory research/design:** [Memory design](MEMORY-DESIGN.md)  
**Prior implementation:** [Kotlin foundation PR #1](https://github.com/cryogenized-spec/CiRYVA-01/pull/1)

> This is an end-to-end map, not a rigid calendar. A phase is **complete only with working evidence**, not when an agent generates folders, declarations, screenshots or a green check unrelated to that feature. Dependencies and implementation order may evolve after verified experiments, but must be documented.

---

## 1. Destination and release strategy

CiRYVA is a coherent personal assistant, not a collection of disconnected integrations. Its defining experiences are a responsive Android conversation, a distinct Orbitron/electric-blue visual identity, genuine OpenAI streaming with bounded cost, dependable local history, and eventually memory and authorized tools.

Build **one reliable vertical slice at a time**. Each feature should have a visible user outcome, genuine tests, clear ownership of stored data, and permission/cost boundaries. Do not ship nonfunctional buttons, mock provider replies, pretend agents, invisible recurring jobs or screens made solely to look feature-rich.

### Three major release horizons

| Horizon | What the user can do | Not required yet |
| --- | --- | --- |
| **Release 1 — Dependable assistant** | Install a real Android app; securely configure the provider; send/receive streamed OpenAI messages; switch eligible models; use safe completed Markdown, local threads, image/file attachments, persona, roleplay, appearance and request usage indicators | Autonomous tools, full memory extraction, live video, PDF compiler |
| **Release 2 — Capable companion** | Search and recollect sourced details, use approved first-party tools/MCP, dictate text, OCR a camera image and ask about a selected screen region | A complex autonomous scheduler, media-player ecosystem, all possible integrations |
| **Release 3 — Personal operator** | Manage real organizer records and approved external workflows, use durable background jobs, export/restore data, optionally generate PDFs, and ship an audited stable release | Unlimited autonomy, free image generation without a proven entitlement, speculative integrations |

Each release has its own **acceptance gate**. "Alpha", "beta", "stable", and "published" are different statuses, and no APK can be declared production-ready merely because JVM-only tests pass.

### Priority semantics

- **P0 — safety / reliability blocker:** credentials, cancellation, real persistence, data protection, truthful usage, budget and test gates.
- **P1 — core experience:** chat, Markdown, Orbitron UI, models, attachments, persona and accessible controls.
- **P2 — value expansion:** memory, first-party retrieval, MCP, OCR, screen selection, voice.
- **P3 — optional enhancement:** organizer automation, TeX/PDF, YouTube, AI image generation, advanced live multimodal.

---

## 2. Starting point and prerequisites (updated 2 November 2026)

- The default branch **main** contains the planning documents, agent skills, passing repository-integrity workflow, and the merged pure Kotlin/JVM core. It does **not** contain an Android application.
- [PR #1](https://github.com/cryogenized-spec/CiRYVA-01/pull/1) **merged** as commit `92949d9781fa06fe0f8bb72d0d77d6e87a590eea`, delivering the tested Kotlin/JVM core: incremental SSE framing, request/turn arbitration and token-budget arithmetic. It is not yet an Android application.
- The active **Mandatory CI test pass** ruleset protects against deletion/force pushes and requires PRs. As confirmed in the live GitHub ruleset on 2 November, **both Repository integrity and Foundation JVM tests are required** with strict status enforcement.
- The native implementation direction is Kotlin + Jetpack Compose with custom Material 3 styling. Orbitron is the chosen display font; Inter vs Manrope for body text is not settled.
- The 9:16 visual direction is established in the [P01 design specification](UI-DESIGN.md), with [candidate tokens](../design/tokens.json). **Rendered screen concepts and user approval are still outstanding.**
- The user prefers minimal downloads and a personal first use case. Whether the product will be widely distributed is still an explicit architectural decision, particularly for API-key custody.

**Immediate steps:** finalize the rendered P01 visual designs and user approval; add Foundation JVM tests as a required status; select a compatible Android toolchain after documenting footprint. Do not mistake a documentation/specification milestone for a completed Android APK.

---

## 3. Dependency graph

The principal execution path is:

**P00 governance / PR #1 → P01 visual approval → P02 Android app & real Android CI → P03 secure OpenAI connection → P04 live chat → P05 durable archive and safe rendering → P06 core settings/models/attachments → P07 Release-1 quality gate.**

After a usable Release 1, the expansion lanes are:

- **P08 memory:** source IDs, archive search and controls must exist before automatic formation.
- **P09 local tools:** tool registry and permission broker before write-capable tools.
- **P10 MCP:** permission broker and real tool trace before remote connectors.
- **P11 OCR / camera** and **P12 screen selection:** each needs independent Android permission and device testing.
- **P13 speech:** editable transcript; never auto-send.
- **P14 Release-2 reliability gate.**
- **P15 organizer/actions → P16 document/PDF and optional media → P17 backup/distribution and stable-release audit.**

Parallelize research, design and tests where safe; avoid concurrent mutations to the same files, schema or API authority. No automatic memory should proceed until the message archive and provenance records are trustworthy.

---

## 4. Phase-by-phase delivery ledger

Use these phase IDs in PR descriptions, issue titles and handoffs. A phase changes from **Not started** to **In progress** only with actual work, and to **Verified** only with passing evidence. The table is a **proposal** rather than a claim of completion.

| Phase | Milestone and substantive deliverable | Depends on | Acceptance / exit evidence | Release |
| --- | --- | --- | --- | --- |
| **P00** | Repository governance, branch protections, review of functional Kotlin core | Existing docs/CI | PR #1 reviewed; current checks verified; build status required; no direct-main bypass | Prerequisite |
| **P01** | Approved Orbitron UI/design-system specification | Existing product spec | Four 9:16 flows reviewed; tokens, font pair and interaction states approved | Prerequisite |
| **P02** | Real Android Gradle project, minimal native host and Android CI | P00, P01 | Installed app launches on supported target; CI assembles, lints and unit-tests | R1 |
| **P03** | Secure settings and OpenAI model/session transport | P02 | Real credential lifecycle; permitted model discovery; cancellation/retry tested | R1 |
| **P04** | First complete conversational vertical slice | P03 | Actual provider response streams to UI; cancelled/late responses cannot corrupt current thread | R1 |
| **P05** | Local archive, final-only Markdown and persistence | P04 | Threads survive restart/process death; formatted messages render once; archive intact | R1 |
| **P06** | Models, attachments, persona, roleplay, appearance and usage | P05 | Genuine working controls; images/files transmitted when supported; no dummy buttons | R1 |
| **P07** | Release-1 alpha acceptance and stabilization | P02–P06 | Real device end-to-end regression, privacy/usage review, signed internal test build | R1 |
| **P08** | Archive search and source-grounded memory | P05, P07 | Recall subtle past detail with accurate source and deletion/consent controls | R2 |
| **P09** | Validated first-party tool registry and authorization | P05, P07 | Real read tool, permission-granted write, approval/deduplication tests | R2 |
| **P10** | External MCP registry/authentication | P09 | User adds, authorizes, invokes and disconnects one real compatible server | R2 |
| **P11** | Camera, files/OCR input | P06, P07 | Camera focus/shutter/torch; editable OCR; original image retained | R2 |
| **P12** | Consent-based screen-region capture | P11 or independent media foundation | Selection, capture/attachment and refusal work on supported Android device | R2 |
| **P13** | Voice transcription draft | P04, P07 | Microphone request; record/cancel; editable transcript; no auto-send | R2 |
| **P14** | Release-2 companion acceptance | P08–P13 | Recollection/tool/OCR/voice real-device & security matrix passes | R2 |
| **P15** | Organizer and authorized scheduled actions | P09, P10, P14 | Real task/note operations; approved scheduled behavior; reliable audit/cancel | R3 |
| **P16** | PDF/document generation and selected optional media | P07; P15 if tool-driven | Real exportable PDF or media feature only after asset/licence/cost gate | R3 |
| **P17** | Backup/restore, accessibility, security, distribution, maintenance | Ongoing, final gate after features | Independent install/restore/release checklist; no critical unresolved issue | R3 |

### P00 — Governance and functional foundation

**Work:** verify PR #1 source and its actual Kotlin tests, review the no-SDK/JVM-only scope, require the **Foundation JVM tests** status check alongside **Repository integrity** in GitHub Settings, select a simple PR merge style, keep ruleset bypasses empty, and test that direct protected-branch changes are rejected. Preserve the branch/commit references in the continuity checkpoint.

**Done when:** foundational Kotlin source is safely merged (if approved), CI checks are genuinely enforced on PRs, and the current GitHub HEAD is recorded with a green run. Never treat an unsigned or unrun source change as verified.

### P01 — Visual system and four approved UX flows

**Work:** turn the approved concept into genuine design artifacts (not production app placeholders), using [P01 Orbitron UI design specification](UI-DESIGN.md): **main chat**, **conversation drawer**, **agent/persona settings**, **appearance settings**. Define layout, top-left drawer and pinned Settings, top-right 4:5 portrait and its action, model selector, paperclip, keyboard/IME behavior, accessible hit targets, message typography and states.

Create reusable design tokens for obsidian (#0A0C10), graphite (#11151B), aluminium (#151A21), raised steel (#1C232C), silver-white (#EAF2FF), electric blue (#27B2FF) and supporting shades. Establish restrained X geometry, crosshatch, soft blurred-visage texture and sparse glitch effects that never overwhelm message text. **Orbitron** is for identity/heading surfaces, not long paragraphs. Compare Inter and Manrope for body messages; JetBrains Mono is a technical candidate. Include loading, streaming, error, offline, no-conversation, long-message and accessibility states. Check representative 9:16 screens and very small phones.

**Done when:** user approves an actual visual representation, the palette/font licences and asset sizes have been checked, contrast and responsiveness reviewed, and interactions are specified. A static mockup is design evidence only; it is not a feature pass.

### P02 — Real Android scaffold, tooling and hardware target

**Work:** decide **minimum supported API level** from the actual device/test needs; choose supported AGP/Kotlin/Compose/JDK/Gradle versions from official compatibility information. For public Google Play publishing, recheck the latest target API requirements; as of this roadmap, new normal mobile apps must target **Android 16 / API 36 or higher** under the requirements effective 31 August 2026. This policy does not alone determine the minimum OS version or whether a personal sideload needs Play publication.

Add a real Gradle wrapper and verified checksum, Android manifest/application ID, a single native activity, Compose theme and lifecycle-aware state handling; minimize initial modules. Package only necessary assets, not bundled ML models or TeX Live. Build and install a real internally usable shell, with only real controls appropriate to its implementation stage. Add GitHub Actions Android CI with **assembleDebug, lintDebug, testDebugUnitTest**, plus a device/emulator lane when justified; make its actual job required for Android PRs.

**Done when:** GitHub-hosted Android CI passes and an installable APK launches on an agreed Android device/emulator. No local Android Studio install without explicit agreement on download/storage budget.

### P03 — Provider credentials, model registry and transport

**Work:** choose personal single-device/BYOK versus distributable backend-based credential custody *before* implementing secrets. On a personal build, use hardware-backed Android Keystore where possible and encrypted, app-private credential storage; never print or redisplay the key (masked last four only). For multiuser/public distribution, design a minimal server-side key authority with user authentication, usage limits and revocation; do not embed shared production/admin keys in a shipped APK.

Implement a small OpenAI Responses API adapter using streaming SSE with correctly decoded UTF-8, validated event envelopes, explicit timeouts, user cancellation, transient/terminal error differentiation and request IDs. Discover model eligibility where supported; distinguish available IDs from a genuinely compatible model/capability matrix. Surface actual response usage rather than inventing a continuously exact token ticker. Design local per-turn, daily and tool-depth caps; state which figures are provider-confirmed versus estimated.

**Done when:** one authorized test request succeeds against a real eligible model, wrong/expired key produces a recoverable error, interruption/cancel cannot duplicate requests, and no credential appears in logs or code.

### P04 — First usable chat experience

**Work:** build the approved chat composition in Compose. Implement text composer, Send, in-progress Cancel, output scrolling with correct keyboard insets, basic user/assistant messages, model display and a genuinely useful setup/recovery path. Stream assistant content as lightweight text; leave heavy Markdown formatting until the response reaches a terminal state.

Integrate the tested SSE, turn arbiter and context-budget components from P00 where appropriate. Separate actual Responses events, tool activity and reasoning summaries; never manufacture private chain-of-thought text. Handle airplane mode, lost connection, late network events, rapid Send/Cancel and older requests returning after a newer one.

**Done when:** a real text prompt produces streamed OpenAI output in the Android UI; Send/Cancel and visible errors operate correctly on an actual device. No fake demo responses in production.

### P05 — Archive durability and safe Markdown

**Work:** create canonical local IDs for thread, message, attachment and generation. Use Room/SQLite for structured records, app-private files for binary originals, schema migrations and safe transactions. Implement create/reopen/rename/delete/search threads, stable conversation ordering and recovery after restart/process death. Introduce final-only CommonMark/GFM-compatible rendering, escaped action italics, code fences, links and long-article scrolling. Treat raw HTML as inert and reject unsafe link schemes. Cache completed layouts; do not parse whole conversations on each incoming delta.

Plan retention/deletion semantics and a minimal archive export early; production-grade backup/restore completes later in P17.

**Done when:** historical messages are source-identifiable, persist through lifecycle events and display correct Markdown once complete. Large transcripts and adversarial Markdown do not freeze the interface or execute HTML/script content.

### P06 — Finish the Release-1 everyday experience

**Work:** dynamic eligible-model selector and compatible capability labels; request-level usage and spending controls; import/share sheet for images and selected files with source retention and bounded payloads; model-supported multimodal attachment sending; removable attachment preview and errors. Add an editable assistant name, system instruction, persona portrait import, roleplay enable switch and conditional prompt editor; support asterisk-delimited Markdown actions and good line breaks.

Implement persisted theme settings, assistant-specific font/text color, 9:16 background selection with safe downsampling and opacity/contrast controls. Bundle minimal offline fonts; optional downloadable fonts are opt-in and cached. Every visible menu item/button has real behavior; later features appear only when implemented.

**Done when:** the complete everyday workflow works across app restarts without overwriting data or bloating prompt context; attachment upload and model incompatibility failures are explained; spending warnings are truthful.

### P07 — Release-1 alpha gate

**Work:** run a documented acceptance matrix on a real phone or credible emulator: clean install, setup/key replacement, streamed prompt, long Markdown, image/file turn, model switch, roleplay on/off, background change, thread restore, account/network error, repeated cancellation, process death, rotation and keyboard use. Inspect APK and dependencies, launch/scroll performance, battery and network consumption, accessibility, untrusted links, build reproducibility and crashes.

**Done when:** all core pathways work without dummy controls, critical defects are resolved, and a traceable internal test APK is produced with its exact commit, CI run and device/test environment. Release 1 is **not** automatically a Play Store launch.

### P08 — Source-grounded memory and useful nuance

**Work:** build archive search, exact source excerpts and IDs before automatic memory extraction. Start with explicit user-directed save, inspect, correct, archive, export and delete. Support project/thread scope, timestamps, confidence, temporal qualifiers and source evidence. Distinguish “today I don't like coffee” from a permanent preference; do not accept model guesses as user statements. Then evaluate opt-in selective/attentive formation, relevant retrieval caps, conflict/supersession and user control of sensitive categories. The user values **tiny meaningful details**, but complete archives must not automatically become context.

Explore public Anthropic memory-tool concepts and open-source Mem0/Letta design patterns as research, not as mandatory Android dependencies.

**Done when:** the assistant can accurately retrieve a small sourced detail later, disclose uncertainty, honor deletion/scope/consent and remain within per-turn token budgets. No model-driven unbounded memory loop.

### P09 — First-party tool system and action authority

**Work:** define a typed registry for read/search of authorized local conversations and files, then for permitted organizer operations. Each tool has schema validation, output-length limits, risk class, timeout, resource scope, audit/event receipt and failure semantics. A central permission/confirmation broker must mediate mutations; duplicate execution across retries must be prevented. Untrusted retrieved text has no authority to authorize another tool.

**Done when:** at least one **real read tool** can retrieve relevant local data, a deliberately denied operation fails closed, a permitted write shows a human-readable confirmation and is auditable, and stale/replayed actions cannot silently repeat.

### P10 — Remote MCP connections

**Work:** implement a server registry with display name, URL, description, capability discovery, authenticated connection where required (OAuth/secure tokens), selected per-tool permissions, timeouts and disconnect/revocation. Distinguish remote MCP transport from local Android tools or a desktop MCP server requiring networking/tunnelling. Test a **real** authorized compatible service such as Notion rather than accepting arbitrary endpoints as “connected.”

**Done when:** a user adds one MCP endpoint, completes real authorization, uses a read capability, views activity, explicitly approves any write, then disconnects and revokes access without losing local conversations.

### P11 — Camera and OCR attachments

**Work:** adopt CameraX only at this phase. Implement camera permission, responsive preview, focus, shutter, torch/flash (manual and supported automatic behavior), retake, image confirmation and local persistence. Evaluate bundled versus on-demand ML Kit OCR models with real download measurements. Show editable recognized text tied to the original image. Use Photo Picker / document providers for user-selected items; avoid broad file permissions.

**Done when:** a captured image and OCR text can be reviewed, edited and sent as appropriate; hardware capability exceptions are handled; no missing SDK downloads or silent cloud recognition are hidden.

### P12 — Android screen-region question flow

**Work:** design a user-invoked contextual entry point (possibly an overlay or Android-supported assistant/share action), visible rectangle drag selection, capture preview and attach-to-chat flow. Respect MediaProjection's foreground service and **consent per capture session**, particularly Android 14+ token restrictions, Android secure/protected surfaces and publication policies. Device-specific feasibility may require a changed UX; do not promise covert/unrestricted capture.

**Done when:** a user can select a visible region on a supported device, authorize the capture, and ask about the image; refusal/cancellation/protected content leaves no unauthorized screenshots.

### P13 — Voice-to-text drafting

**Work:** use explicit microphone interaction with native Android recognition where suitable. Offer recording/cancel/silence/timeout states and optional language selection; insert transcription at the composer cursor without sending. If a future model-based transcription path is added, show provider and billing implications. TTS and continuous voice conversation are independent later decisions.

**Done when:** speech can be transcribed, corrected and submitted on a tested device; cancelled or incorrect recognition does not overwrite other text or dispatch unsolicited messages.

### P14 — Release-2 companion gate

**Work:** cross-check memory relevance, source correctness, permission boundaries, OAuth revocation, MCP prompt injection, OCR/camera denial, overlay consent, voice abort and usage budgets under repetitive tool calls. Measure performance of 1,000+ message histories and sizable local archives using realistic fixtures and a device. Do not send private fixtures to external providers without explicit authorization.

**Done when:** a working assistant reliably recalls details, uses approved tools and handles device-media interactions without runaway costs or unreviewed side effects.

### P15 — Personal organizer and controlled automation

**Work:** define real local task/note/list records and first-party CRUD operations before connecting third-party task managers. Add task due dates, completed state, scoped search and explicit external actions; avoid silently reproducing the full ClickUp/Todoist ecosystems. For reminders/schedules, use WorkManager or exact alarms only where Android policy and the user action require them. Durable jobs have identities, cancellation, bounded retries, visible schedules and a log of outcomes. External sending/updates require consent.

**Done when:** a user can create/review/edit/complete tasks and authorize a reliable reminder or connector operation; duplicate jobs and hidden background activity are prevented.

### P16 — Document generation and optional media

**Work:** separate Markdown/chat math from full PDF compilation. Evaluate TeXlyre-BusyTeX or another viable on-device/authorized service implementation with actual WASM/TeX Live size, licensing and local storage budget. A model-generated document must be reviewable before a safe, bounded compile/export operation. Large binaries or fonts should be downloaded only when the user chooses that feature. Only later investigate YouTube embedding/playback, AI image-generation output or live voice/video, after validating provider access, licences, platform terms and cost.

**Done when:** a requested PDF actually compiles and exports, errors are meaningful, shell escape/arbitrary paths are unavailable, and optional features do not increase base-app footprint without justification.

### P17 — Stable distribution and long-term maintenance

**Work:** clarify personal sideload versus public Play Store distribution (API-key security model fundamentally differs). Implement reliable encrypted export/restore and verified deletion, offline recovery, optional import/migration, release signing and versioning, accessibility/localization checks, dependency licence inventory, vulnerabilities/secret reviews, Android API/permissions compliance and clear privacy disclosures. Add crash reporting only with explicit privacy consent and redaction; establish maintenance/change-log/rollback process and performance regression budgets.

**Done when:** a fresh supported device can install the exact signed build, restore data accurately, perform the entire supported feature set, revoke permissions, and recover from expected network failures. Every release statement maps to traceable CI and on-device test evidence.

---

## 5. Hard quality gates that apply across every phase

**A. No fake completion.** A PR must name a real user outcome, automated tests run, live/device checks, dependencies/download sizes, skipped verifications and remaining problems. A green documentation CI check alone does not count as an Android implementation test.

**B. CI as code.** Keep the existing Repository integrity and Foundation JVM tests; once Android code is present, require the Android build/lint/unit-test job in branch protection. For each feature add deterministic unit/integration tests and, where permissions and device behavior matter, instrumented tests. Keep workflow permissions read-only except for an explicitly justified publish job.

**C. State and data integrity.** No chat, message, attachment, memory or activity record may have competing unsynchronized authorities. Use migrations, canonical IDs, replay-safe network/tool transitions and tests for interrupted writes.

**D. Security and consent.** No secrets in source, logs, PR comments or untrusted tool output. Permission prompts must match a user-visible action. Screen capture, microphone, camera and filesystem operations are restricted to their actual granted scopes. Model output can propose an action but cannot approve it.

**E. Token and monetary discipline.** Bound model requests and tool output; count/preflight when possible, record the provider's actual usage metadata on completion, label uncertain estimates, enforce tool-depth/iteration caps, and never assume complimentary tokens pay for every operation.

**F. Performance and cost.** Record startup, scroll, memory, battery, download size, APK size and network usage for supported device(s) at each release gate. No permanent animated effects or repeated Markdown parsing on every stream delta. Introduce only measured dependencies.

**G. User-centred visual quality.** Orbitron headers, readable body text, electric-blue accent and subtle geometry must remain accessible and efficient; keyboard, text scaling, TalkBack/semantics, reduced motion and landscape/insets must be considered. Decorative themes never obscure chat content.

**H. Documentation continuity.** Update [CONTINUITY.md](CONTINUITY.md) after verified milestones. Reconcile [DECISIONS.md](DECISIONS.md) only after a choice is made, not to prematurely commit proposals. Keep [AGENTS.md](../AGENTS.md) and skills aligned with actual build/test commands.

---

## 6. Verification matrix by risk class

| Risk scenario | Required proof before calling the feature ready |
| --- | --- |
| Provider stream splits a UTF-8 sequence, SSE event or tool argument | Incremental decoder and JSON/event tests; real streamed device turn |
| Rapid send/cancel/retry and app process death | No duplicate/late response or corrupted persisted message |
| Long conversation / tool-result explosion | Measured input/tool budgets, bounded excerpts, graceful refusal/compaction |
| Markdown adversarial HTML, enormous code fence or links | Inert HTML, safe URL policy and smooth terminal rendering |
| Lost credentials, wrong key, account limits and exceeded budget | Correct masked status, protected storage, reliable errors, clear usage |
| Memory inference conflicting with the source | Provenance, temporal qualifier, confidence and user correction |
| Sensitive memory retention / source deletion | Consent settings and tested derived-memory deletion behavior |
| Prompt injection inside document/MCP output | Untrusted content does not change authority or trigger unchecked mutation |
| Interrupted external write / repeated tool event | Idempotency, confirmation, receipts and retry boundaries |
| Camera/voice/screen capture permission denied | App recovers; no background covert capture |
| Large OCR models, fonts, PDF runtimes | Explicit size estimate, opt-in download/cache and licence check |
| Android OS/Play policy drift | Target API and permissions rechecked at release, not inferred from old docs |
| New device data migration | Test export/import, integrity checks and correct encrypted-key behavior |
| CI bypass or stale PR status | Protected main, required job checks, status verified at actual merge commit |

---

## 7. Release acceptance summaries

**Release 1 acceptance:** I can install the APK, add/replace a key safely, start a thread, select a supported model, send a real prompt, read a streamed answer with completed Markdown, attach a real image/file, adjust persona/appearance, leave/reopen the app and find every message and its usage record intact. No inactive feature buttons and no hidden billable loop.

**Release 2 acceptance:** I can ask about a small past detail and inspect its source; request a real local retrieval or remote MCP read; approve or refuse actions; photograph/OCR a document; select a screen area with consent; and dictate an editable message. Each workflow retains original artifacts and obeys privacy, tool and token limits.

**Release 3 acceptance:** I can manage tasks and approved ongoing actions, create/export documents when implemented, restore the app on another device, revoke credentials and permissions, and use a stable signed build whose security, licence, Android compatibility and regression tests are documented.

---

## 8. Effort planning and execution discipline

Do not promise calendar dates until the build environment, target handset and scoped acceptance criteria have been tested. Complexity varies sharply: Android capture overlays and remote OAuth are higher-risk than local chat. Use the following *relative* planning bands only (not time guarantees):

| Workstream | Relative difficulty | Main uncertainty |
| --- | --- | --- |
| JVM core and baseline CI | Low–medium | Native integration, packaging |
| Compose chat and design system | Medium | Keyboard, viewport, streaming, accessibility |
| OpenAI model/capability/usage integration | Medium | API change, streaming event semantics and safe key management |
| Local history/attachments/Markdown | Medium | Migrations, binary assets, long-chat rendering |
| Memory recall/source linking | High | Correctness, sensitive privacy, inference cost |
| First-party tool authority and remote MCP | High | OAuth, external prompt injection, idempotency |
| Android region capture / OCR / voice | Medium–high | Device/OS restrictions, model downloads |
| Organizer and reliable scheduled actions | High | Background execution restrictions and external consistency |
| Full PDF compiler / optional media | High, deferrable | Binary footprint, licences, Android compatibility |
| Release engineering/distribution | Medium–high | Signing, key custody, store policy, recovery |

**Sprint / PR pattern:** agree one small outcome → inspect current HEAD and relevant skill → estimate costs/downloads → implement on branch → run required tests → review screenshots/records as appropriate → merge after checks → update continuity/roadmap status. Do not create a dozen half-implemented features at once. Maintain one primary unblocked development thread; speculative research can proceed separately without mutating implementation.

### Definition of done for every milestone

A milestone is **Verified** only when its function is implemented; its happy path and failure path work; real tests pass; permissions and usage effects are documented; acceptance evidence is linked; the branch is integrated through protected PR checks; and the continuity checkpoint identifies what to do next.

**No amount of planning text, generated mockups or unexecuted scaffolding substitutes for those conditions.**

---

## 9. Open decisions that block specific work

| Decision needed | Must resolve before |
| --- | --- |
| Supported phone model / Android minimum version; API target and build machine/CI budget | P02 |
| Exact initial 9:16 screen design and body-font selection | P02 native UI |
| Personal-only BYOK versus distributable/backend credentials | P03 and especially P17 |
| Actual accessible OpenAI model set, allowed spend and free-token conditions | P03 |
| Whether attachment payloads remain local when not being sent | P06 |
| Memory capture intensity, sensitive categories, scope and deletion contract | P08 |
| Tool action approval/confirmation defaults and MCP OAuth scope | P09–P10 |
| Camera/screen-capture approach on the target Android device | P11–P12 |
| User-initiated versus always-on scheduling permissions | P15 |
| TeX compiler/runtime/licence and download footprint | P16 |
| Sideload vs Google Play; published privacy/data handling | P17 |

Proposals in this document should not silently become user-approved technical or privacy decisions.

---

## 10. Public sources and re-verification points

- [Android architecture recommendations](https://developer.android.com/topic/architecture/recommendations) — layered UI/data, repositories, Compose, ViewModels/Flow.
- [Android MediaProjection guide](https://developer.android.com/media/grow/media-projection) — consent, service type and newer Android restrictions.
- [Google Play target API policy](https://developer.android.com/google/play/requirements/target-sdk) — verify again at the actual publication date.
- [OpenAI Responses streaming guide](https://developers.openai.com/api/docs/guides/streaming-responses) — verify available events, models and usage metadata when implementing.
- [Internal product specification](../README.md), [memory design](MEMORY-DESIGN.md) and [CI contract](CI.md) are the current repository-derived planning sources.

**Maintenance rule:** update this roadmap when a phase is actually completed, a dependency changes, a major technical decision is approved, or release scope materially changes. Do not replace tested implementation truth with an optimistic narrative.
