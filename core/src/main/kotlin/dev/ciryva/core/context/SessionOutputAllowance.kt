package dev.ciryva.core.context

import dev.ciryva.core.stream.HttpStreamResult
import dev.ciryva.core.stream.TextResponseRequest
import dev.ciryva.core.stream.TokenUsage

/**
 * Process-local conservative OUTPUT reservation gate for the upcoming P03/P04
 * integration. This is NOT a durable daily quota and is NOT USD billing.
 *
 * Requests permanently consume their full max_output_tokens reservation within
 * the lifetime of this gate, including cancelled / rejected / failed requests.
 * This deliberately trades unused allowance for a strict in-process cap that
 * cannot be bypassed by repeatedly cancelling or retrying requests.
 *
 * Actual provider usage is tracked independently and ONLY when reported.
 * Input tokens, pricing and multi-device usage are not constrained by this
 * primitive. A future durable quota store and model/capability price table
 * must precede claims of per-day spending protection.
 */
data class OutputAllowanceSnapshot(
    val reservedOutputTokens: Long,
    val remainingOutputTokens: Long,
    val acceptedRequests: Int,
    val remainingRequests: Int,
    val settledRequests: Int,
    val confirmedUsage: TokenUsage,
    val receiptsWithoutUsage: Int,
)

enum class OutputReservationRejection {
    DUPLICATE_REQUEST, REQUEST_COUNT_EXCEEDED, OUTPUT_ALLOWANCE_EXCEEDED,
}

sealed interface OutputReservationResult {
    data object Accepted : OutputReservationResult
    data class Rejected(val reason: OutputReservationRejection) : OutputReservationResult
}

class SessionOutputAllowance(
    private val maxReservedOutputTokens: Long,
    private val maxRequests: Int,
) {
    init {
        require(maxReservedOutputTokens in 1..1_000_000L)
        require(maxRequests in 1..10_000)
    }

    private data class Reservation(val outputLimit: Int, var settled: Boolean = false)
    private val reservations = HashMap<String, Reservation>()
    private var reserved: Long = 0
    private var settled: Int = 0
    private var usageMissing: Int = 0
    private var inputTokens: Long = 0
    private var outputTokens: Long = 0
    private var totalTokens: Long = 0

    /**
     * This atomic admission MUST happen before making the corresponding POST.
     * Every logical request ID is unique, regardless of retries or cancellation.
     */
    @Synchronized fun reserve(requestId: String, request: TextResponseRequest): OutputReservationResult {
        require(requestId.isNotBlank() && requestId.length <= 128)
        if (reservations.containsKey(requestId)) {
            return OutputReservationResult.Rejected(OutputReservationRejection.DUPLICATE_REQUEST)
        }
        if (reservations.size >= maxRequests) {
            return OutputReservationResult.Rejected(OutputReservationRejection.REQUEST_COUNT_EXCEEDED)
        }
        if (request.maxOutputTokens.toLong() > maxReservedOutputTokens - reserved) {
            return OutputReservationResult.Rejected(OutputReservationRejection.OUTPUT_ALLOWANCE_EXCEEDED)
        }
        reservations[requestId] = Reservation(request.maxOutputTokens)
        reserved += request.maxOutputTokens
        return OutputReservationResult.Accepted
    }

    /**
     * Idempotent terminal accounting. Do NOT accept usage from text deltas or
     * client estimates. Completed and incomplete results may have provider
     * receipts. Unknown counts are never silently rewritten as zero billed.
     */
    @Synchronized fun record(requestId: String, result: HttpStreamResult): Boolean {
        val reservation = reservations[requestId] ?: return false
        if (reservation.settled) return false
        val receipt = when (result) {
            is HttpStreamResult.Completed -> result.usage
            is HttpStreamResult.Incomplete -> result.usage
            else -> null
        }
        // Validate before marking a reservation settled; callers can then
        // reject invalid receipts without corrupting accounting state.
        if (receipt != null) {
            require(receipt.inputTokens >= 0 && receipt.outputTokens >= 0 && receipt.totalTokens >= 0) {
                "Provider receipt contains negative token counts"
            }
        }
        reservation.settled = true
        settled++
        if (receipt == null) {
            usageMissing++
        } else {
            // Conservation of provider-confirmed receipts only, never estimates.
            inputTokens += receipt.inputTokens
            outputTokens += receipt.outputTokens
            totalTokens += receipt.totalTokens
        }
        return true
    }

    @Synchronized fun snapshot(): OutputAllowanceSnapshot =
        OutputAllowanceSnapshot(
            reservedOutputTokens = reserved,
            remainingOutputTokens = maxReservedOutputTokens - reserved,
            acceptedRequests = reservations.size,
            remainingRequests = maxRequests - reservations.size,
            settledRequests = settled,
            confirmedUsage = TokenUsage(inputTokens, outputTokens, totalTokens),
            receiptsWithoutUsage = usageMissing,
        )
}
