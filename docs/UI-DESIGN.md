# CiRYVA-01 — P01 Orbitron UI System and Screen Review

**Status:** First-pass visual design specification; **not yet user-approved** as rendered screens.  
**Date:** 1 November 2026 (SAST).  
**Roadmap:** [P01](ROADMAP.md). **Implementation:** no Android UI has been built or tested.  
**Visual identity:** Industrial Futurist / Tactical Neo-Noir. Native Android (Kotlin/Compose) when implementation begins.

## 1. Creative intent

**CiRYVA should look like a precision-engineered private assistant console, not a generic messaging application or a busy gamer dashboard.** The atmosphere is obsidian and aluminium, readable silver-white, deliberate electric-blue focus lights, Orbitron display typography, a restrained motif of X intersections, faint gridlines and occasional ghosted/glitch fragments. Depth should come from measured contrast, fine outlines, layered planes and whitespace—not expensive real-time blur or visual clutter.

The chat is a tool for reading and working. **High-frequency content stays quiet and readable.** Theatrical effects are reserved for identity/header surfaces and user-enabled wallpaper. Visual motifs are decorative only and must not imply background capture, surveillance or streaming.

### Style constraints

- **Dark-first**; ensure the base application works fully without a wallpaper image.
- **Orbitron** is the selected brand/display family for the app wordmark, major section titles and small emphasis. **Do not use it for paragraphs, Markdown output, user typing or small technical text.**
- **Body font** decision stays open: Inter versus Manrope. Use a reliable system sans-serif fallback. JetBrains Mono is a candidate for code, technical tables and usage figures.
- Prefer a few native/vector assets to an icon/font/CDN runtime. Google-font downloads are **opt-in**, cached and size-disclosed. Do not bundle unapproved font binaries in this design PR.
- **Motion** is functional, short, optional and honours reduced-motion. Glitch is never applied to incoming message text.

## 2. Proposed theme and contrast

Palette source is the prior approved color direction; **hex values remain design proposals** pending a rendered review.

| Token | Hex | Intended use |
| --- | --- | --- |
| canvas | #0A0C10 | Full-screen base |
| backgroundRaised | #11151B | Drawer and secondary navigation |
| surface | #151A21 | Cards, assistant areas and editor |
| surfaceElevated | #1C232C | Menus and popovers |
| contentPrimary | #EAF2FF | Primary body/heading text |
| contentSecondary | #9FB0C5 | Secondary metadata |
| electricBlue | #27B2FF | Pressable focus, selected item, active icon |
| luminousBlue | #66D4FF | Tiny edge glow, rarely |
| accentMuted | #3E6D8A | Decorative contours, **not normal body text** |

Using the WCAG relative-luminance formula, proposed text contrasts include: contentPrimary on canvas **17.38:1**, contentPrimary on surface **15.52:1**, contentSecondary on surface **7.90:1**, electricBlue on surface **7.41:1**. The muted accent on surface is only **3.13:1** and therefore should **not** carry ordinary-size instructional text. Contrast ratios of composites and text over images must be independently measured.

On error and success states, use accessible distinct status icons and labels, not color as the only signal.

## 3. Type, geometry and spacing (candidate tokens)

**Typography (sp):**

| Role | Family | Size / line | Weight |
| --- | --- | --- | --- |
| App title | Orbitron | 20 / 27 | 600 |
| Section heading | Orbitron | 16 / 23 | 500 |
| Navigation label | System sans-serif | 14 / 20 | 500 |
| Assistant and user message | Inter / Manrope candidate | 16 / 24 | 400 |
| Input composer | Same readable body face | 16 / 24 | 400 |
| Metadata | Sans-serif | 12 / 17 | 400–500 |
| Code/usage | JetBrains Mono or system monospace | 13 / 20 | 400 |

Keep semantic text scaling enabled and test at large system font scales, including button wrap, code-block scrolling and drawer truncation. Use dynamic type rather than fixed bitmap text.

**Shape/spacing:** 4 dp grid; 8/12/16/24 dp spacing; 12–16 dp panel radii, 8–10 dp utility radii, 1 dp thin outlines, minimum **48 dp touch targets**. Avoid overlapping gesture edges. Chat message content has generous left/right padding and constrained line length on larger screens.

## 4. Phone frame and safe areas

Use **9:16 portrait reference canvases**, e.g. 360×640 dp and 412×732 dp, but do not hardcode app height or assume every phone has a 9:16 display. Artwork may be cropped from a 9:16 original. An Android device can have different aspect ratio, font scale, cutouts, navigation bars, IME height and landscape mode.

On newer Android versions, edge-to-edge layouts must honour status/navigation bars, cutouts and IME insets. The future Compose Scaffold / drawer implementation should consume its layout padding without **double applying insets**. Keyboard opening must move the composer into view without discarding draft text, jumping the scroll position or burying the send/cancel control.

Android official references:
- [Material 3 inset behavior](https://developer.android.com/develop/ui/compose/system/material-insets)
- [Edge-to-edge setup](https://developer.android.com/develop/ui/compose/system/setup-e2e)

## 5. Four screen contracts

### Screen A — Main chat / default home

**Header:** small 48 dp hamburger hit area at top left; Orbitron **CiRYVA** identity or thread title centred/left of safe portrait region; a compact accessible model selector; optional **4:5 aspect image source** shown as a small portrait crop at top right. Tapping the portrait always opens the currently selected agent's settings. The control is absent/uses an intentional accessible monogram when the user has not selected a portrait (not a misleading image generator).

**Transcript:** quiet background. User messages have a subtly differentiated steel panel; assistant messages favor open text on dark canvas, with optional light outlines. Time/usage metadata is available on demand, not visually repeated on every paragraph. Long responses use readable body type, headings and code blocks. Expandable actual tool-event receipts and model-supplied reasoning summaries appear only if genuinely supported by the current model and response.

**Composer:** pinned above IME/insets at bottom. From left: paperclip attachment chooser (show **only available actions**), multiline text input, contextual Send while composing / Stop while a request is in progress. If voice dictation has not been implemented, no nonfunctional microphone appears. Use disabled state only for a transient true reason (e.g. sending or no internet with a clearly explained error).

**Generation:** incoming deltas appear as **lightweight unformatted text**; full Markdown conversion occurs once after terminal completion/cancellation. Progress indication uses a tiny blue accent or unobtrusive pulse, never a glitch animation over text. Cancel fences the generation from late events.

**States to visually review:** empty local history, first-run key configuration entry path, selected thread, long code block, streaming, cancellation, offline/network error, attachment preview, high system text scale.

### Screen B — Thread drawer

Draws **from left to right**, with a matte graphite surface, very thin steel divider, narrow blue active-thread marker and an optional understated X motif in the header background. Top entry offers **New conversation**, followed by search if implemented, then a vertical saved conversation list (recent first). Show genuine thread names, last activity and appropriate empty-state copy. Thread deletion has a confirm/undo flow where supported; do not fake content in production.

**Settings is pinned at the bottom** and remains reachable when the thread list scrolls. Drawer closes on selection, exterior scrim tap, system Back or a swipe in the proper direction. Never trap the keyboard behind the drawer. Search must be a real local search before its UI is shipped.

### Screen C — Agent/persona configuration

Entered by tapping the top-right portrait or through Settings. A framed 4:5 portrait preview; controls to **select/import/crop/remove** a local portrait when available; no AI-generated portrait action until image generation is actually implemented and funded. Persona name, system instruction editor and model preference are separate editable fields with transparent precedence. Do not replicate a persona instruction secretly in multiple system prompts.

**Roleplay mode** is an explicit toggle. Enabling it reveals a separate editable instruction box (syntax/format conventions) and a small safe preview of asterisk-delimited actions as Markdown italics; when disabled, roleplay instructions no longer contribute to model context. Save/revert behavior is predictable; no unsolicited memory-writing of roleplay facts.

### Screen D — Appearance settings

Sample the **actual app messages** in a read-only preview area (design mockup may use explicitly labeled demonstration text). Options: dark base palette, assistant message color, readable text font, scale, background (solid/gradient/local image), image dimming, geometry-overlay opacity, motion preference, portrait crop/fit and reset to legible defaults. Header and body font responsibilities remain separate; a selected optional Google Font must be cached after a disclosed download.

App theme controls may not alter or regenerate original chat text. Decorative imagery must not be transmitted to OpenAI unless the user separately attaches it to a message.

## 6. Navigation and state ownership

- The app shell owns navigation/drawer state and safe area geometry.
- Chat view owns visible streaming text and scroll/IME state; saved messages live in the canonical archive.
- Persona settings own explicit per-agent instructions. Model capability availability determines whether a control is meaningful.
- Appearance settings persist separately from chat content, ideally in DataStore after implementation.
- All text/contrast preferences support screen reader semantics and accessibility focus.
- A portrait is a navigation control, not a passive network-uploaded attachment.

Proposed navigation: Chat ↔ Drawer → Settings → Appearance / Agent settings; portrait shortcut from Chat → Agent settings → back to original thread. Back from the drawer dismisses it before leaving the chat activity. Destructive actions have explicit confirmation.

## 7. Visual motif system and performance ceilings

**Motif A: precise X grid** — thin diagonal crossings at 4–8% opacity, concentrated in header/drawer empty space. Do not tile over live assistant text.

**Motif B: ghosted visage** — blurred abstract face/helmet silhouette, only from opt-in background art or a static decorative asset. It must be clearly decorative and should be masked out behind the composer and content area.

**Motif C: signal glitch** — a small 1–2px static chromatic offset along a decorative accent, not full-screen distortion or animation. No strobe/flashing, and always disabled under reduced-motion or low-performance mode.

Prefer simple Compose gradients, borders and vector shapes; no mandatory runtime shaders, backdrop-filter layer stacks, video wallpapers or remote assets.

## 8. Implementation handoff and acceptance

**P01 design-exit evidence** is a real visual review of **all four screens** on a 9:16 layout (and one narrow-font-scale stress case) and user acceptance of: general visual hierarchy, Orbitron usage, body font, portrait placement, chat composer, background density and drawer navigation. This specification alone **does not complete P01**.

Before P02 implementation:
1. Confirm actual selected mockup/visual review and any requested corrections.
2. Confirm font licences, dimensions and icon asset licensing. Orbitron's public source states **SIL Open Font License 1.1**; retain the corresponding licence notice if bundling the font later.
3. Record contrast over actual blended effects and all disabled states.
4. Resolve target Android API, CI SDK/toolchain size and supported test phone.
5. Begin with a **real functional vertical slice**, not UI full of inactive future integrations.

References:
- [Orbitron upstream licence](https://github.com/googlefonts/orbitron-vf)
- [Google Fonts SIL OFL guide](https://googlefonts.github.io/gf-guide/license-file.html)
- [Android Compose inset guidance](https://developer.android.com/develop/ui/compose/system/material-insets)

**No font binary or external visual asset has been copied into this repository by this design-only PR.**
