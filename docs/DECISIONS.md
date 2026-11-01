# CiRYVA-01 — Decisions and Open Questions

This file distinguishes **confirmed choices** from **proposals**. Changes require explicit user choice or a clearly documented implementation decision. For full product requirements, read README.md.

## Confirmed by user — 2026-11-01

- **App:** CiRYVA-01, Android-first organizer and AI companion, distinct from Elara.
- **Generative model provider:** OpenAI only initially. Offer a selector for eligible available models.
- **Archive:** local conversations, threads, files, images, user-visible history.
- **UX:** top-left hamburger opens a slide-out left drawer; Settings at bottom. Tappable top-right character portrait leads to persona settings. Paperclip attachment menu, camera/OCR, customizable font/color/9:16 background.
- **Markdown:** safe formatting, no raw HTML execution, no continuous Markdown reparse while streaming. Roleplay actions can use asterisk/italics convention.
- **Persona:** configurable system instructions, optional roleplay mode with conditional instruction editor.
- **Tools:** first-party tools and simple URL-based external MCP connector manager, requiring authentication where relevant.
- **Memory goal:** rich Claude-like nuanced recollection including small details. Original records must remain retrievable.
- **Costs/privacy:** resource and API usage transparency; avoid surprises. User chose sharing API traffic with OpenAI to contribute to model improvement.
- **Implementation discipline:** no dummy features or blank mocked functionality; deliver working tested slices.
- **Icons:** Iconify preferred.
- **Deferrals:** YouTube, live voice/video, costly image generation and large TeX runtime are not MVP blockers.

## Candidate architecture — NOT final

- Kotlin + Jetpack Compose, Room/SQLite and app-private media storage.
- OpenAI Responses API streaming with local conversation authority and capped context assembly.
- Android Keystore for personal testing; production key handling via user-owned protected backend/proxy.
- Google ML Kit for OCR, subject to library size/permission evaluation.
- Memory as source-linked fact/episode records plus direct full-archive search; no mandatory cloud/vector database.
- Skills structured as \`skills/<topic>/SKILL.md\`; short root AGENTS.md and a handoff checkpoint.
- BusyTeX or alternative for later PDF compiler if footprint/licensing acceptable.

## Open decisions

- Android min/target API levels and supported handset.
- Exact model list/capability detection and API billing budget.
- Whether the app is personal-only or destined for wider distribution (affects API key architecture).
- Detailed memory retention, sensitive-category opt-in, similarity search, cost of extraction and deletion semantics.
- Tool permissions and MCP authentication transport.
- On-device vs provider speech recognition, voice style.
- Exact Android implementation and CI environment, library download sizes.
- First working vertical slice and acceptance test.

Only mark choices **confirmed** after user agreement; do not use this document to manufacture approval.
