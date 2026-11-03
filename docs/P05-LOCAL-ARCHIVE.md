# P05 — Device-private conversation archive and bounded context

**Status:** Draft implementation on [PR #12](https://github.com/cryogenized-spec/CiRYVA-01/pull/12), stacked on P04 [PR #11](https://github.com/cryogenized-spec/CiRYVA-01/pull/11). **Not merged or released.** No paid OpenAI smoke test was run.

## User-visible vertical slice

The native CiRYVA Home tab now offers a real, locally stored conversation list. A user can create a conversation, reopen it after Activity recreation, inspect the original user/assistant text with provider-authored reasoning summaries and genuine token receipts, and delete it only after an explicit confirmation. The chat composer clears an old thread's draft and one-time API charge consent when switching threads, but restores the same-thread draft across rotation.

A user request is committed to Android's device-private SQLite store **before** the controller starts its chargeable OpenAI request. Responses are saved on terminal completion. SQLite operations and list loading use a dedicated serialized IO executor so disk latency cannot block the UI thread. If storing the final response fails, it remains on screen and blocks another send until the user retries. Leaving the foreground cancels/interrupts ongoing network work, while preserving any already-authoritative provider-completed response and its usage.

## Storage contract

- Built-in Android `SQLiteOpenHelper` only. No new third-party dependency or local SDK download.
- `threads` have UUID identifiers, creation/update times and user-prompt-derived titles.
- `turns` have distinct UUID identifiers, full user and assistant text, provider-authored summary (when supplied), model ID, timestamp, sanitized status and actual provider token receipt (when returned).
- A pending turn may be settled only once. Completed, failed, incomplete, cancelled and interrupted records remain locally visible. **Only completed user/assistant pairs** are eligible for future model context.
- Thread deletion cascades to its source turns. The UI explicitly confirms this irreversible operation.
- Archive list and conversation view currently show up to 100 recent rows; **older rows remain in the database**. Pagination, export/restore and later memory provenance/search are still planned.
- Android app backup remains disabled. This is **app-private SQLite, not encrypted chat-history storage**; AndroidKeystore still protects the separate API key. Protect device access and do not confuse this with end-to-end encryption.

## Request context and cost boundary

The OpenAI Responses transport may encode an ordered array of typed `user`/`assistant` messages before the new current `user` message. It preserves `store: false`. If no context is present it keeps the original single-string request.

- At most **6 complete historical pairs / 12 messages** and a combined **12,000 history characters**, excluding the current prompt. This is a **character bound, not a token or billing quotation**.
- Skips oversized pairs rather than truncating them into misleading fragments. Excludes failed, interrupted and incomplete generations and provider reasoning summaries.
- Validates roles, ordering and budgets in the JVM transport, with a real loopback HTTP test of the JSON body.
- OpenAI API billing remains user-owned and requires the same explicit per-request user authorization. No API keys are present in this repository or in hosted emulator tests.

## Current verification and gaps

- Source and JVM/Android unit build checks should be read from the actual GitHub Actions runs for PR #12. Before any completion claim, inspect the exact run result and job logs.
- [Android 15 device-backed P05 archive/Compose run 41711031158](https://github.com/cryogenized-spec/CiRYVA-01/actions/runs/41711031158) was initiated to test SQLite restart, completed-only context, deletion, and real Home tab interaction. A run being started does **not** imply that the tests passed.
- Physical Android phone and TalkBack/rotation review are not yet verified end-to-end. The parent P04 IME test has shown intermittent hosted-emulator timeouts; do not hide them.
- No actual user-paid OpenAI request has been executed or authorized, so real provider continuity is not yet accepted despite the validated structured input format.
- Future schema migrations must preserve user data, never drop existing tables by default. Dedicated backup/export, more complete search and local memory belong to later milestones.

## Follow-up

After confirming P05-specific emulator tests, keep PR #12 draft until P04 dependencies and real lifecycle/device gates are addressed. Then extend P05 with archive pagination/export, crash recovery and broader UI accessibility before the P06 attachments/persona phase.
