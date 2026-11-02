package dev.ciryva.core.stream

import java.nio.ByteBuffer
import java.nio.CharBuffer
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets

/**
 * Stateful, strict UTF-8 decoding followed by bounded SSE event framing.
 *
 * Network read boundaries are arbitrary: a two-, three-, or four-byte UTF-8
 * character may span several reads. Calling String(bytes, UTF_8) per read can
 * irreversibly corrupt the text, so the charset decoder and its small pending
 * buffer must survive until the transport reaches EOF.
 *
 * This class knows nothing about HTTP, OpenAI JSON schemas or Android. It
 * rejects malformed/truncated UTF-8 rather than silently replacing characters.
 * It is a single-stream, single-consumer object: don't call it concurrently.
 */
class Utf8SseEventDecoder(
    maxLineChars: Int = 65_536,
    maxEventChars: Int = 262_144,
) {
    private val framing = SseEventDecoder(maxLineChars, maxEventChars)
    private val decoder = StandardCharsets.UTF_8.newDecoder()
        .onMalformedInput(CodingErrorAction.REPORT)
        .onUnmappableCharacter(CodingErrorAction.REPORT)

    // Fixed-size buffers: network chunks are processed incrementally even if
    // the caller provides one very large byte array.
    private val input = ByteBuffer.allocate(8192)
    private val output = CharBuffer.allocate(4096)
    private var firstCharacter = true
    private var closed = false

    /** Accept a network read; returns zero or more completely framed SSE events. */
    fun feed(bytes: ByteArray): List<SseEvent> {
        check(!closed) { "SSE stream already finished" }
        val ready = ArrayList<SseEvent>()
        try {
            var offset = 0
            while (offset < bytes.size) {
                val size = minOf(input.remaining(), bytes.size - offset)
                check(size > 0) { "UTF-8 decode made no progress" }
                input.put(bytes, offset, size)
                offset += size
                decode(endOfInput = false, ready)
            }
            return ready
        } catch (failure: Exception) {
            closed = true
            throw failure
        }
    }

    /**
     * Call only on clean EOF. An interrupted or cancelled HTTP request must
     * *not* call finish() and treat a partial SSE frame as completed content.
     */
    fun finish(): List<SseEvent> {
        check(!closed) { "SSE stream already finished" }
        closed = true
        val ready = ArrayList<SseEvent>()
        decode(endOfInput = true, ready)
        while (true) {
            val result = decoder.flush(output)
            deliver(ready)
            if (result.isError) result.throwException()
            if (!result.isOverflow) break
        }
        ready.addAll(framing.finish())
        return ready
    }

    private fun decode(endOfInput: Boolean, ready: MutableList<SseEvent>) {
        input.flip()
        while (true) {
            val result = decoder.decode(input, output, endOfInput)
            deliver(ready)
            if (result.isError) result.throwException()
            if (!result.isOverflow) break
        }
        input.compact()
    }

    private fun deliver(ready: MutableList<SseEvent>) {
        output.flip()
        if (output.hasRemaining()) {
            var text = output.toString()
            if (firstCharacter) {
                firstCharacter = false
                // EventSource allows an optional leading UTF-8 BOM.
                if (text.startsWith("\uFEFF")) text = text.substring(1)
            }
            if (text.isNotEmpty()) ready.addAll(framing.feed(text))
        }
        output.clear()
    }
}
