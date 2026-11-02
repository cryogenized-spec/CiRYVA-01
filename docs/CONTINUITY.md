# CiRYVA-01 — Current Session Checkpoint

**Updated:** 2026-11-02 (SAST, UTC+02:00).
**Status:** P00 foundation/roadmap merged; P01 design specification and four-screen board merged after visual-direction approval and resolved Codex feedback. **P02 Android implementation not started; no Android source, app build, SDK installation or APK exists.**
**Authority:** GitHub commits/PRs, branch rulesets and actual workflow runs supersede this checkpoint.

## Verified results

- Repository: https://github.com/cryogenized-spec/CiRYVA-01.
- PR #1 Kotlin/JVM foundation merged: `92949d9781fa06fe0f8bb72d0d77d6e87a590eea`; genuine SSE framing, generation arbitration and token-budget arithmetic with JUnit tests.
- PR #2 P00–P17 master roadmap merged: `34a552243cedf3f326f1bf1a179939a34239b8e3`.
- PR #3 P01 design merged: `2f773c96c6f15a2f3f2c70957471a7b79f595d51`; corrected two Codex P2 findings (clipped screen captions; body/composer scale), followed by Codex re-review without major findings and six successful CI checks. User accepted the appearance direction. Actual font composition, Android viewport stress cases and real-device testing **remain open**.
- Ruleset **Mandatory CI test pass** (ID `26312198`) is active, with no bypass actors, strict required checks **Repository integrity** and **Foundation JVM tests**. The real Android CI job must be added as required before Android code merges.
- P02 engineering preflight is documented in [P02-ANDROID-PREFLIGHT.md](P02-ANDROID-PREFLIGHT.md) on branch `engineering/p02-android-preflight`; its stack is **proposed**, not build tested.

## Next exact action

1. Review/merge the P02 preflight **as documentation only** after genuine CI checks pass; do not mistake planning for an Android deliverable.
2. Produce the genuine Gradle wrapper JAR/scripts and checksum, Android app module, manifest, a meaningful real native interaction with unit tests and a GitHub-hosted Android CI workflow, all on a separate implementation PR.
3. Run and inspect actual assembleDebug, lintDebug and testDebugUnitTest; set the resulting Android job required before accepting Android source on main.
4. Verify launch, IME, accessible text scale and rotation on an agreed handset/emulator before P02 is called Verified.
5. No Android SDKs or fonts are to be installed on the user's laptop without explicit permission.

## Handoff requirement

Read AGENTS.md, ROADMAP.md, P02-ANDROID-PREFLIGHT.md and relevant skill. Record exact commit, tests and open issues. No pretend Android features, tokens, SDK cost, or hidden personal details in the public repo.


## P03 parallel HTTP transport milestone — 2 November SAST

- P03 draft [PR #6](https://github.com/cryogenized-spec/CiRYVA-01/pull/6) now includes a functional, bounded `HttpURLConnection` streaming adapter in `:core` on commit `b171fe3`, layered on already-tested UTF-8 SSE framing and Responses JSON event decoding.
- [Kotlin CI run 41453709531](https://github.com/cryogenized-spec/CiRYVA-01/actions/runs/41453709531): nine new loopback HTTP tests **passed**; all six checks green. No external OpenAI request, keys, locally installed SDK or paid inference.
- Fixed production endpoint uses HTTPS; explicit caller-owned bearer token; no credential storage; no automatic retries; `store:false`; cancellation, timeout/resource ceilings and terminal receipt checks. See [P03 documentation](P03-STREAM-FOUNDATION.md#p03--http-streaming-transport-milestone-2-november).
- P03 remains a **draft/unmerged dependency** awaiting P02 protected merge and a credential custody decision (single-user BYOK vs distributable backend). Do not expose API key entry in Android, make paid requests, or call it usable chat yet.
- P02 native [PR #5](https://github.com/cryogenized-spec/CiRYVA-01/pull/5) remains open because the ruleset `26312198` still omits the Android required job. Its Android 15 emulator 3/3 test run and screenshot are verified; the last post-screenshot system-bar styling commit also has passing Android CI. Owner must update the ruleset; no bypass.


## Latest P03 handoff — 2 November 2026 SAST

- **Draft PR #6** current source milestone `809f25bee4bf79d0732dab1cf3e8369613156509`: tested `ResponseTurnSession` (turn ID **and exact token identity**, with same-ID restarts fenced) and `SessionOutputAllowance` (process-local conservative output-token reservations). Nine new session and nine new allowance tests passed, all six CI checks green; [Kotlin run 41464968397](https://github.com/cryogenized-spec/CiRYVA-01/actions/runs/41464968397).
- Codex had flagged same-ID restart collision on earlier source `5429047`; fixed and covered by a dedicated regression. An invalid usage receipt no longer mutates quota state before validation. Follow-up Codex review still required.
- No actual Android UI integration, credential store, model registry, durable daily billing protection, live OpenAI call or API charges. Treat reserved output tokens as a **local admission ceiling**, not actual token usage or maximum spend.
- P02 PR #5 remains open, reviewed and Android-tested; GitHub branch protection still requires administrator addition of `Android build, lint and unit tests` in ruleset `26312198` before merging. No bypass or installation on the user's laptop.
- Next: inspect fresh Codex feedback and fix any findings; after the P02 ruleset/merge gate, implement an authorized P03 credential architecture and live transport acceptance test (without writing keys to Git history), then P04 Compose chat.
