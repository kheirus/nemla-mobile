package app.nemla.ai.api

import kotlin.coroutines.cancellation.CancellationException

enum class ExecutionLocation { ON_DEVICE, REMOTE }

data class EngineDescriptor(
    val id: String,
    val provider: String,
    val location: ExecutionLocation,
    val creditCost: Int = 0,
)

sealed interface EngineAvailability {
    data object Ready : EngineAvailability

    data class Unavailable(
        val reason: String,
    ) : EngineAvailability
}

interface AiEngine<I : Any, O : Any> {
    val capability: AiCapability<I, O>
    val descriptor: EngineDescriptor

    suspend fun availability(): EngineAvailability = EngineAvailability.Ready

    @Throws(AiException::class, CancellationException::class)
    suspend fun execute(input: I): O
}

sealed interface AiError {
    data object NoEngine : AiError

    data object ConsentRequired : AiError

    data object QuotaExceeded : AiError

    data object Offline : AiError

    data object Unauthorized : AiError

    data class Rejected(
        val userMessage: String?,
    ) : AiError

    data class EngineFailure(
        val reason: String,
    ) : AiError

    data object PrivacyViolation : AiError
}

class AiException(
    val error: AiError,
    cause: Throwable? = null,
) : Exception(error.toString(), cause)

internal val AiError.allowsFallback: Boolean
    get() =
        when (this) {
            is AiError.EngineFailure, AiError.Offline, AiError.QuotaExceeded -> true

            AiError.NoEngine, AiError.ConsentRequired, AiError.Unauthorized,
            is AiError.Rejected, AiError.PrivacyViolation,
            -> false
        }
