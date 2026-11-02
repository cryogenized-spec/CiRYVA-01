# P04 — Honest native chat layout preview

**Status:** implementation on stacked **draft** branch `feat/p04-chat-shell-preview`, derived from still-unmerged P02 native branch `feat/p02-native-compose-host`. Not a verified OpenAI conversation. P03 streaming transport remains a separate draft PR and is NOT silently merged into this branch.

## Available behavior

- Launch a second native Android Activity from the P02 home using the functioning **Open chat layout preview** control, and return via Back.
- Observe a real responsive chat header, local transcript, inline composer and separate actions for adding **only user-authored local messages** and clearing the preview.
- The application intentionally labels this screen **OFFLINE PREVIEW · NO MODEL CONNECTED**. It has **no active Send**, fictional assistant messages, model selector, history archive, or prompt dispatch.
- Small capped messages (12 × 1200 characters), session-only; a preview can survive typical Activity recreation through saved Compose state, but it is not a persisted conversation and cannot be relied upon after process death. No INTERNET permission is added.
- Colors and typography follow the P01 palette using native system fonts. Final Orbitron/Manrope implementation and the full four-screen design remain future work.

## Testing and acceptance

- Three local JVM tests verify bounded acceptance and rejection of preview-only user messages.
- A device/emulator Compose instrumentation test asserts offline status, disabled local-add without content, adding user-authored text, and clearing it. In normal Android CI this test is **compiled**, not executed. Run it on a hosted Android emulator before claiming device-verified UI.
- Preserve the existing P02 draft editor and its three previously passing Android UI tests; the P04 preview does not replace the protected P02 foundation.

## Dependency path

P02 PR #5 must pass its required Android ruleset status before merge. P03 PR #6 must settle its Code review, credential custody, approved eligible model, privacy and live API acceptance before P04 can truthfully expose Send/Cancel for actual network conversations. This preview PR is intentionally **stacked against P02**, not targeting `main`, to avoid accidentally incorporating unmerged Android code in a direct-main change. The PR can be rebased to `main` only when P02 lands.

No provider traffic, API keys, emulators, SDK packages, new runtime libraries or laptop downloads are triggered by these changes.

## Short-window accessibility repair

Codex identified that a transcript-only weighted scroll container could push the composer and both action buttons beyond reach on a short landscape screen or with the IME open. The screen now uses a constraint-aware layout: standard-height displays scroll the transcript, while **available-height-under-520dp** displays scroll the entire form, including composer and actions. The local transcript remains capped at 12 messages so this small preview need not virtualize inside an unbounded scroller. A landscape/keyboard UI instrumentation regression has been added. The source must pass Android compile and the new UI test must be **executed** on a device or emulator before claiming this accessibility behavior verified.

## Verified hosted Android runtime acceptance (2 November 2026)

Source commit `61b8e7cee2b0c7044074b4eeb60f71a10e80fa6a` passed the [Android 15 emulator run 41494773866](https://github.com/cryogenized-spec/CiRYVA-01/actions/runs/41494773866). The actual logs report **Starting 5 tests**, **Finished 5 tests**, zero skipped/failed, and **BUILD SUCCESSFUL in 2m 58s**. The run used 320dp available display width with 1.5× system text scale. Tests include the three existing P02 draft-editor tests and the two P04 offline-chat UI tests (including landscape keyboard/composer actions). The workflow also saved `ciryva-p04-emulator-large-text` as a screenshot artifact; that automated screenshot launches **P02 MainActivity**, so it should not be represented as a screenshot of the ChatPreviewActivity itself.

**Resource policy:** the temporary emulator pull-request trigger was immediately restored to manual dispatch only after this successful run, so ordinary development changes do not repeatedly download the Android system image. The restore/documentation commit is not itself a re-run of the five emulator tests, but can still be validated with standard native Android CI. This remains a **preview**, not a working OpenAI conversation or persistent chat history.
