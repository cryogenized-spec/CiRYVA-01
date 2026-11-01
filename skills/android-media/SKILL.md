---
name: android-media
description: Plan or implement CiRYVA Android region screen capture, MediaProjection consent, camera torch/focus/shutter, on-device OCR, voice-to-text drafting and later LaTeX/PDF compilation.
---

# Android capture, OCR, voice and PDF

Read README sections 3, 9, 17 and 18 and \`AGENTS.md\`. Do not install media libraries or large TeX assets without measured footprint and permission.

## Scope and order

1. **Camera:** permission request, real preview, focus, shutter, retake, torch/manual-auto flash if hardware supports it; attachment persists with original source.
2. **OCR:** evaluate Google ML Kit model bundling vs on-demand download; produce editable extracted text, paired with original image, never silently Send.
3. **Screen selection:** invoke an overlay, drag to define rectangle, capture only with Android-compliant MediaProjection user consent. Expect protected/secure content to be unavailable. No background covert capture.
4. **Voice-to-text:** explicit microphone activation, record/cancel, editable transcript insertion at cursor, separate Send action; no obligatory live-streaming speech agent.
5. **PDF/LaTeX (later):** inspect TeXlyre BusyTeX Android/WASM feasibility, licensing and total download/storage before choosing a compiler. Separate math display from full PDF creation.

## Quality checks

- Native device vs emulator permissions; rotation, keyboard, cancellation and app lifecycle.
- No runaway resource usage, no unapproved remote fallback for private documents.
- Original artifacts remain locally accessible; failures never destroy drafts.
- Never label a capture or conversion tool implemented because a nonfunctional button exists.
