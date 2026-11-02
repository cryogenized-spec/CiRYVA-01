# CiRYVA-01 — Current Session Checkpoint

**Updated:** 2026-11-02 (SAST, UTC+02:00).  
**Status:** Kotlin foundation and master roadmap merged to main; P01 visual-system specification drafted on its own feature branch. **No Android source, APK or approved final UI visuals.**
**Authority:** Actual GitHub branch/PR/CI records and current files supersede the checkpoint.

## Current verified state

- Repository: https://github.com/cryogenized-spec/CiRYVA-01
- **Foundation PR #1 merged** as commit `92949d9781fa06fe0f8bb72d0d77d6e87a590eea`; includes a true Kotlin/JVM core (SSE event framing, generation arbiter, token budget) tested under GitHub Actions.
- **Master roadmap PR #2 merged** as commit `34a552243cedf3f326f1bf1a179939a34239b8e3`; see [ROADMAP.md](ROADMAP.md) for 18 phases P00–P17 and three release horizons.
- Active ruleset `Mandatory CI test pass` (ID `26312198`): main requires pull requests, blocks force push/deletion, and requires **Repository integrity**. **Foundation JVM tests is now configured as required** alongside Repository integrity (verified via live ruleset read on 2 November).
- [P01 UI specification](UI-DESIGN.md), [design tokens](../design/tokens.json) and the [four-screen 9:16 draft visual board](../design/p01-four-screen-review.svg) are **review artifacts**, not an approved design or compiled Android UI.
- User-approved direction: Orbitron heading/display font; obsidian black, electric blue, silver white, aluminium grey; restrained X/grid/glitch/blur motifs. Top-left thread drawer, Settings anchored at bottom, top-right agent portrait shortcut, bottom composer, roleplay/persona options.
- Core product: native Kotlin/Compose, OpenAI only for generative AI, safe terminal-only Markdown, local archives, granular source-backed recollection and authorization-gated tools/MCP, with camera/OCR/screen/voice in later phases.
- **No new libraries, font files, assets or Android SDK were installed on the user's laptop.** Design-stage GitHub file changes only.

## Current milestone and next action

**P01 — visual design review is in progress.** The written spec defines four required mobile screens (chat, conversation drawer, agent configuration, appearance), proposed tokens, responsive/IME and accessibility requirements. **P01 is not done until the actual rendered 9:16 visuals are shown and approved by the user.**

1. Review PR #3's current CI and [four-screen visual board](../design/p01-four-screen-review.svg); obtain explicit user approval or corrections to hierarchy, portrait, composer, font and background density. **Do not merge as approved before this gate.**
2. Verify exact Orbitron appearance in a rendered design environment, compare Inter/Manrope, and visually stress-test narrow and larger accessibility font scales; the SVG uses fallback font when Orbitron is unavailable.
3. Both **Repository integrity** and **Foundation JVM tests** are already required in the active ruleset; no GitHub Settings change is pending for these checks.
4. For P02, choose supported min/target Android API and real handset/emulator, measure Gradle/Android SDK costs, then create a genuine Android application and real CI executing assembleDebug, lintDebug, testDebugUnitTest. Never call a JVM build an Android build.
5. Once PR #3 is explicitly approved, rerun/verify final CI and update phase status. No SDK, keys, runtime fonts, or network inference were installed or used for this design-only commit.

## Handoff requirements

Before action read root AGENTS.md, this checkpoint, README and the relevant skill. Record exact branch/commit, tests actually executed, limitations, data/download costs and next step. Do not put credentials or private conversation content in a public repo.
