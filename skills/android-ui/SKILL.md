---
name: android-ui
description: Design or implement native Android UI and navigation for CiRYVA-01, including Jetpack Compose, chat shell, drawer, portrait, composer, custom fonts and colors, and Android viewport/keyboard behavior.
---

# Native Android UI

## Before work

Read \`AGENTS.md\`, \`docs/CONTINUITY.md\`, README sections 3, 13 and 17, and the actual UI sources once present. Kotlin/Jetpack Compose is **proposed**, not an already verified build. Check toolchain footprint and alternatives before installation.

## Core UI contract

- Top-left hamburger -> left drawer containing threads, Settings at bottom.
- Optional top-right 4:5 character portrait -> persona settings.
- Main chat scroll with bottom composer; paperclip opens camera, gallery, OCR, files and local library.
- Streaming text is lightweight plain text; finalized messages use safe Markdown rendered once. Never allow raw HTML execution.
- Roleplay setting conditionally reveals its own editable instructions; normal chat remains ordinary.
- Font/color/background preferences persist locally and do not alter conversation data.
- Target 9:16 portrait ergonomics, accessible contrast, touch target sizes, dynamic keyboard insets, a stable viewport and saved scroll position.

## Execution

1. Start with one complete, testable vertical slice; never create placeholder screens/controls for unbuilt features.
2. Keep Compose presentation separate from transport, storage, credentials and tool execution.
3. Verify lifecycle behavior across rotation, background/foreground, app recreation and keyboard appearance.
4. Load only needed vector Iconify assets; check licences and asset size.

## Acceptance

A functional UI control must perform a real action, maintain state across restart if intended, and pass focused tests. Claim device-level validation only when run on a real emulator/handset. Do not claim Compose, Gradle or Android Studio setup exists until it does.
