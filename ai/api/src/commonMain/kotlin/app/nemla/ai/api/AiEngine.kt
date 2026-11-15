package app.nemla.ai.api

import kotlin.coroutines.cancellation.CancellationException

enum class ExecutionLocation { ON_DEVICE, REMOTE }

/**
 * Identity card of an engine. Everything the router needs to choose, nothing about how it works.
 *
 * @property id stable identifier, also used in settings and diagnostics ("nemla-backend.voice").
 * @property provider who runs the model ("device-rules", "nemla-backend", "own-llm"...).
 * @property creditCost credits charged to the user's monthly quota for one call (0 if free).
 */
data class EngineDescriptor(
    val id: String,
    val provider: String,
    val location: ExecutionLocation,
    val creditCost: Int = 0,
)

sealed interface EngineAvailability {
    data object Ready : EngineAvailability

    /** Cannot run right now; the router moves on to the next engine. */
    data class Unavailable(val reason: String) : EngineAvailability
}

/**
 * One provider's implementation of one [AiCapability].
 *
 * This is the only interface a new AI provider has to implement. Engines know nothing about each
 * other, about routing, consent or credits: they do their job or throw an [AiException].
 */
interface AiEngine<I : Any, O : Any> {
    val capability: AiCapability<I, O>
    val descriptor: EngineDescriptor

    /** Cheap check: model downloaded, OS support present, configured... Must not hit the network. */
    suspend fun availability(): EngineAvailability = EngineAvailability.Ready

    /** @throws AiException on any failure. Cancellation must propagate untouched. */
    @Throws(AiException::class, CancellationException::class)
    suspend fun execute(input: I): O
}

sealed interface AiError {
    /** No engine can serve the capability at all. */
    data object NoEngine : AiError

    /** The user has not agreed to send this kind of data off the device. */
    data object ConsentRequired : AiError

    /** Monthly credits exhausted. */
    data object QuotaExceeded : AiError

    data object Offline : AiError

    /** Session missing or expired. */
    data object Unauthorized : AiError

    /** The engine understood the request and rejected it; retrying elsewhere will not help. */
    data class Rejected(val userMessage: String?) : AiError

    /** Transient or provider-side failure; another engine may succeed. */
    data class EngineFailure(val reason: String) : AiError

    /** A payload more sensitive than allowed was about to leave the device. Always a bug. */
    data object PrivacyViolation : AiError
}

class AiException(val error: AiError, cause: Throwable? = null) : Exception(error.toString(), cause)

/** Whether trying the next engine makes sense after this error. */
internal val AiError.allowsFallback: Boolean
    get() = when (this) {
        is AiError.EngineFailure, AiError.Offline, AiError.QuotaExceeded -> true
        AiError.NoEngine, AiError.ConsentRequired, AiError.Unauthorized,
        is AiError.Rejected, AiError.PrivacyViolation,
        -> false
    }
