# P03 — Safe streaming UTF-8 foundation (independent of provider credentials)

**Status (2026-11-02):** [Draft PR #6](https://github.com/cryogenized-spec/CiRYVA-01/pull/6) implements and verifies the standalone P03 streaming primitive on the pure Kotlin/JVM core. **All eight new JUnit tests passed and Codex reported no findings on implementation commit `91477273ce`.** It does **not** implement OpenAI transport, authentication, the Responses API JSON event schema, cost accounting or an Android UI. P02's protected merge gate remains open.

## Reason for this slice

The existing `SseEventDecoder` consumes **already decoded Kotlin strings**. Networking libraries may deliver arbitrary byte chunks, even splitting a single Unicode code point across reads. Calling `String(bytes, UTF_8)` on each arbitrary chunk corrupts non-ASCII responses, including emoji and many user languages. This pure JVM layer now inserts a **stateful, strict** `CharsetDecoder` between network bytes and bounded SSE framing. It is useful independently of Android and avoids a speculative network-client dependency.

## Contract

- `Utf8SseEventDecoder.feed(ByteArray)` accepts raw network read bytes, preserves incomplete UTF-8 across reads, and emits fully framed `SseEvent` results.
- Internal buffers are fixed at **8 KiB for bytes** and **4 Ki UTF-16 code units** for decoded text, while line/event limits remain enforced by the existing parser. Input arrays belong to the caller, and a single oversized read is drained incrementally.
- Accepts an optional Unicode BOM **only at the stream start**, recognized even if its three bytes are split.
- Rejects malformed and incomplete terminal UTF-8 by throwing, rather than adding replacement characters to a provider event. A decoder that errors or ends cannot be reused.
- `finish()` is only for genuinely **clean EOF**. Do not call it on cancelled streams, network errors, or aborted HTTP responses: the existing SSE framer intentionally flushes a final non-empty event without the last blank line, which is wrong for an interrupted partial response.
- Pure JVM; no Android APIs, HTTP client, model endpoints, dependencies, keys, downloads or inference. Existing JUnit5 core test suite tests malformed payloads, all two-way byte boundaries, single-byte chunking, BOM/CRLF, event bounds, clean EOF and many successive events.
- **Not thread-safe**. A future transport must serialize reads, cancellation, EOF and UI events; preserve state and terminal errors consistently.

## Gates before P03 can be declared complete

1. Decide approved **personal BYOK vs distributable backend** credential custody. Avoid implementing a credential screen before that decision.
2. Implement a bounded OpenAI Responses API HTTP adapter with real stateful UTF-8 SSE framing and validated event schemas.
3. Test timeout, cancellation, malformed JSON, wrong key, provider-side errors and backpressure.
4. Verify one authorized **actual** streamed OpenAI response and provider-reported usage without credentials in logs or Git history.
5. Integrate model selection and per-turn cost budgets; no fake precise live counters.

**Parallel work note:** this is a narrow library primitive **prepared ahead of P03's main gate** while P02's verified native Android PR #5 waits for the extra required GitHub Android CI status and actual phone checks. It does not alter, merge or bypass P02.

## Verified evidence

On `91477273ced67ea604e353c0ac8a2fae981d0621`, the [Kotlin foundation GitHub Actions run](https://github.com/cryogenized-spec/CiRYVA-01/actions/runs/41389022850) completed `:core:test` successfully. The logs independently list **eight PASSED** `Utf8SseEventDecoderTest` test cases and a passing core JAR build. All six checks for that implementation commit completed successfully. [Codex review in PR #6](https://github.com/cryogenized-spec/CiRYVA-01/pull/6) explicitly reported no major findings. This evidence is **pure JVM**, not Android emulator, actual network traffic, or OpenAI usage. The PR intentionally stays draft until the preceding P02 gate is accepted.

## Bounded Responses event envelopes — follow-on component

The draft also includes `ResponsesEventParser`, a schema-aware **text/lifecycle subset** classifier that consumes complete `SseEvent` objects and validates JSON using the pinned `kotlinx-serialization-json:1.9.0` runtime (compatible with the repository's Kotlin 2.2.x baseline). It distinguishes `response.output_text.delta`, `response.completed`, `response.failed`, `response.incomplete`, and `error`. Unknown tool/reasoning events remain opaque instead of being presented as visible assistant text. A completed response may expose **provider-reported** usage counts, or leave them null when absent; no estimates are fabricated. It rejects malformed event data, SSE/JSON type mismatch, invalid indices, forged completion status and negative usage counts.

Official reference (checked 2 Nov 2026): https://developers.openai.com/api/reference/resources/responses/streaming-events . The JSON runtime adds a small Maven Central dependency resolved **only in GitHub CI**, not a local install. Measured download and cache size remain unrecorded; no paid OpenAI inference occurs. This is **not** a complete API client and does not establish terminal event ordering across streams; transport cancellation and the final success receipt remain P03 implementation responsibilities.

## Codex parser strictness repairs (2 November)

Codex identified and we corrected two P2 schema checks: empty `delta: ""` values are valid (while identifiers remain nonempty), and numeric token counts/indices must be genuine JSON numbers, not quoted number strings. Regression tests explicitly check both. This is still not a network client, and the amended commit requires fresh Kotlin CI and Codex review.
