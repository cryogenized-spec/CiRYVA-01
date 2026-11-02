# CiRYVA P04 — portrait chat redesign and Thinking summaries

**User visual direction:** ClipMint-inspired burgundy, ink, blush/rose-gold accents, very restrained gothic/Japanese ornament; the actual screen remains native Android (Jetpack Compose), not a web dashboard. Reference image was a **concept**, not an APK screenshot. Reference formatting: the user's `cryogenized-spec/Elara-Companion-current` app, especially its original-Markdown storage, separate message rendering, anchored composer and mobile portrait concealment.

## Implemented in draft PR #11

- 9:16-friendly chat-first screen. A small title/model control sits above the transcript; model, thinking effort, verbosity and sampling are in native Material 3 bottom sheets/settings instead of full-height forms.
- The large anime portrait is **removed from the actual active-chat UI**. The chat text occupies the central scrollable region. Anchored composer, explicit one-request charge acknowledgement and Send/Stop remain in sight; there is no surprise network request, retry or key read at Activity launch.
- Native safe Markdown renderer for paragraphs, up to three sensible heading levels, emphasis, lists, quotes, fenced code with monospaced horizontal scrolling, separators and basic tables. Raw HTML stays inert. **This is not full GitHub-Flavored Markdown compatibility** (e.g. complex nested lists/footnotes and links not yet implemented). Original Markdown is preserved.
- While network events are streaming, answer and summary output use **plain text**. Markdown parses only after the request settles, preventing repeated whole-answer parsing per delta.
- Provider **reasoning summary** handling: on compatible models, the user can request the API's `reasoning.summary=auto` via Settings. Only `response.reasoning_summary_text.delta` content appears in the collapsible, separately labelled Thinking-summary panel. The parser bounds these events to 8192 summary characters per request. No internal/private chain-of-thought is available or reconstructed. If no summary arrives, there is nothing fabricated to expand.
- Token usage is shown **only** when returned by the provider; never infer from text or code. On incomplete, cancelled or network-failed operations, no invented usage receipt.
- Each Send remains one **independent request** until durable history/context is implemented. The Home tab intentionally links to the existing offline-draft screen; it does not fake a conversation archive.

## Work still required before merge/release

1. Android build/lint/JVM/core and fake-only Compose instrumentation on the revised source, including 320dp / 1.5× font accessibility and scrolling to the last model entry. **Do not call this passed without inspecting exact workflow logs.**
2. IME/rotation/background lifecycle, TalkBack, larger fonts and a physical handset pass.
3. Review UI for tap sizes and correct Material 3/Compose layout under gesture bars and unusual aspect ratios.
4. Optional real API smoke request only with the user's own key and a separately **approved spending limit**; no project/administrator key placed in GitHub.
5. Update the README and main product roadmap after verification; avoid claiming saved conversation history, image upload, tool executions, character/avatar or raw CoT is implemented here.

**No new downloadable font/assets or other external libraries were added for this slice.** The named Elara repository is a formatting reference only; CiRYVA is a separate native app and uses OpenAI, not Gemini.
