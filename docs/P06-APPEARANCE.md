# P06 — Native appearance and typography controls

**Status:** Implemented and emulator-verified on [draft appearance PR #14](https://github.com/cryogenized-spec/CiRYVA-01/pull/14), stacked on [persona/models PR #13](https://github.com/cryogenized-spec/CiRYVA-01/pull/13), itself stacked on P05 #12 / P04 #11. **Not merged and not a release.**

The app's real Settings screen now provides saved, instantly applied native-Compose preferences:

- **Backdrop:** Classic burgundy (current user-approved default), Midnight plum and Graphite ink. Each option uses a different four-stop full-screen gradient. Chat panels stay legible on each.
- **Reading typeface:** Modern sans-serif or Literary serif. Applies to stored and live message text and safely rendered completed Markdown. Fenced code keeps its monospace face.
- **Decorative background waves:** On/off. Removes the large low-contrast curved strokes from the backdrop without altering any content or intercepting touch input.

All choices use the existing app-private `ChatPreferencesStore`, with a fixed whitelist and safe defaults after invalid/stale data. The settings survive Activity recreation and app restart; they don't need network access, a new font, an SDK install, extra assets or third-party libraries. Changing appearance **never causes a paid API request**, resets no conversation, and does not store billing consent.

**Verification evidence (3 Nov 2026 SAST):** [Android native build/lint/JVM run 41726387944](https://github.com/cryogenized-spec/CiRYVA-01/actions/runs/41726387944) passed; [Kotlin foundation run 41726387937](https://github.com/cryogenized-spec/CiRYVA-01/actions/runs/41726387937) passed. [Hosted Android 15 appearance run 41726863052](https://github.com/cryogenized-spec/CiRYVA-01/actions/runs/41726863052) ran **6/6 real device instrumentation tests, zero skipped/failed**, at 320dp and 1.5× font scale; screenshot artifact was produced. The first Codex review reported a TalkBack label and offscreen-test scrolling issues; both fixed, latest Codex review clean and both threads resolved. All heavy CI/browser preview triggers restored to opt-in/manual-only. No live billable inference test; this is not a physical-handset release.

**Future P06 tasks:** supported local image/file attachments with genuine provider transport, optional wallpaper import and additional legible contrast/font-scale accessibility options. No external wallpaper, image search or customer data is stored automatically; privacy and permission gates apply.
