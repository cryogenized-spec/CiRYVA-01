# P06 — Native appearance and typography controls

**Status:** Draft, stacked [appearance PR](https://github.com/cryogenized-spec/CiRYVA-01/pulls) on [persona/models PR #13](https://github.com/cryogenized-spec/CiRYVA-01/pull/13), itself stacked on P05 #12 / P04 #11. **Not merged and not a release.**

The app's real Settings screen now provides saved, instantly applied native-Compose preferences:

- **Backdrop:** Classic burgundy (current user-approved default), Midnight plum and Graphite ink. Each option uses a different four-stop full-screen gradient. Chat panels stay legible on each.
- **Reading typeface:** Modern sans-serif or Literary serif. Applies to stored and live message text and safely rendered completed Markdown. Fenced code keeps its monospace face.
- **Decorative background waves:** On/off. Removes the large low-contrast curved strokes from the backdrop without altering any content or intercepting touch input.

All choices use the existing app-private `ChatPreferencesStore`, with a fixed whitelist and safe defaults after invalid/stale data. The settings survive Activity recreation and app restart; they don't need network access, a new font, an SDK install, extra assets or third-party libraries. Changing appearance **never causes a paid API request**, resets no conversation, and does not store billing consent.

**Tests in this PR:** real Android SharedPreferences reload and invalid-value fallback, Compose settings selections and no-API-without-authorization assertions; native Gradle build/lint and Android 15 emulator tests are the verification gates. Avoid claims of verification until the corresponding runs complete.

**Future P06 tasks:** supported local image/file attachments with genuine provider transport, optional wallpaper import and additional legible contrast/font-scale accessibility options. No external wallpaper, image search or customer data is stored automatically; privacy and permission gates apply.
