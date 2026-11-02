# P03 — Safe streaming UTF-8 foundation (independent of provider credentials)

**Status (2026-11-02):** [Draft PR #6](https://github.com/cryogenized-spec/CiRYVA-01/pull/6) has tested SSE byte-framing and Responses JSON envelope parsing, and now implements an actual, bounded **HTTP streaming adapter** on source commit `b171fe3`. Its nine new loopback HTTP tests passed in [GitHub CI run 41453709531](https://github.com/cryogenized-spec/CiRYVA-01/actions/runs/41453709531), alongside the existing UTF-8 and event parser tests. **This is a transport implementation, not a successful authenticated OpenAI API call or usable Android chat.** Credential custody, live inference, a complete model registry, UI, usage-budget integration and P02 protected merge all remain outstanding.

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

## P03 — HTTP streaming transport milestone (2 November)

**Verified source:** `b171fe3c442461b54d15dc5c56c1e825b6869b07`, [Kotlin foundation run 41453709531](https://github.com/cryogenized-spec/CiRYVA-01/actions/runs/41453709531). All **nine** newly introduced `ResponsesHttpTransportTest` cases were listed as **PASSED** in actual Gradle logs, and six repository checks succeeded. Codex was requested to inspect the implementation; **do not assume its final review passed until the live receipt appears.**

The new `ResponsesHttpTransport` connects over the Android/JVM built-in `HttpURLConnection` interface. Production construction is fixed to `https://api.openai.com/v1/responses`, with no endpoint selection in the UI. The internal injectable connection factory exists solely for offline loopback testing. The request includes the caller's explicitly provided `model`, bounded text `input`, `stream: true`, `store: false`, `background: false`, and `max_output_tokens` with a conservative maximum of 4096. Headers send the caller-supplied bearer token; the implementation does **not** read, persist, log, display or manufacture API keys. API key ownership remains an **unresolved P03 decision**; do not add a key entry UI or shared app key before that decision is made.

The client disables HTTP redirects, uses a 10-second connection timeout, 15-second socket read timeout, 120-second elapsed wall-clock check (which can overshoot by a blocked read), maximum **4 MiB** streaming bytes, **2000** complete SSE events and **131,072** delivered text characters. Existing UTF-8 and SSE frame limits apply. The network adapter invokes its listener from the *calling thread*; a future Android integration **must** call it from a worker and marshal events to UI state safely.

`StreamCancellation` aborts active reads by disconnecting and checks cancellation between parsing and callbacks. Cancellation does not call the SSE decoder's clean-EOF completion path. The client never retries automatically. A provider `response.completed` event is required to report success, and token numbers are taken only from that event. HTTP 401/403/429/503, redirects, incomplete/provider-error events, invalid media types, malformed UTF-8 or JSON, pre-cancel and mid-stream cancellation are tested with a loopback `HttpServer` — no paid external request or key.

**Important scope limits:** The client is synchronous and requires background-thread scheduling by P04. It does not yet verify provider model eligibility, meter price, obtain a credential securely, handle all emerging Responses event variants, enforce a shared daily budget, or perform a live network test with a valid key. No retry semantics beyond "no implicit retries" have been approved. Absolute deadline enforcement currently depends on periodic reads and the read timeout; a continuously stalled socket is bounded by the read timeout, not an independent watchdog. Do not call this a complete P03 implementation.

### Dependency and bandwidth assessment

This follow-on adapter uses only Java/Android networking and the **already approved** `kotlinx-serialization-json:1.9.0` dependency in the draft branch; it introduces **zero additional runtime libraries**. Its nine tests use JDK 17's built-in loopback `HttpServer`; no mock SaaS, paid network calls, or background daemon are started outside the CI job. The changes increase source/test code and compilation work but **do not download** an SDK, emulator, APK or model to the user's laptop. Actual future OpenAI API traffic, if authorized, will incur provider token charges according to model/pricing, so the project should verify the model/budget before sending any requests.

**Current official API reference:** [Responses creation](https://developers.openai.com/api/reference/resources/responses/methods/create) and [streaming events](https://developers.openai.com/api/reference/resources/responses/streaming-events), checked 2 November 2026. API docs show streamed SSE with `stream: true`, event-specific data, and optional `store: false`.
