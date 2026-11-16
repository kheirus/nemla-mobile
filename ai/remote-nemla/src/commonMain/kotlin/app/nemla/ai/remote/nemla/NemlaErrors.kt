package app.nemla.ai.remote.nemla

import app.nemla.ai.api.AiError
import app.nemla.ai.api.AiException
import app.nemla.core.common.AppError
import app.nemla.core.common.AppResult

internal const val REASON_TIMEOUT = "timeout"
internal const val REASON_RATE_LIMITED = "rate_limited"
internal const val REASON_INVALID_RESPONSE = "invalid_response"
internal const val REASON_NOT_SUPPORTED = "not_supported"
internal const val REASON_OFFLINE = "offline"

internal fun AppError.toAiError(): AiError =
    when (this) {
        AppError.Network.Offline -> AiError.Offline
        AppError.Network.Timeout -> AiError.EngineFailure(REASON_TIMEOUT)
        AppError.Unauthorized -> AiError.Unauthorized
        AppError.Forbidden -> AiError.Rejected(null)
        AppError.QuotaExceeded -> AiError.QuotaExceeded
        AppError.RateLimited -> AiError.EngineFailure(REASON_RATE_LIMITED)
        AppError.ConsentRequired -> AiError.ConsentRequired
        is AppError.Validation -> AiError.Rejected(code.name)
        is AppError.Server -> AiError.EngineFailure(status?.let { "server_$it" } ?: "server")
        AppError.NotFound -> AiError.EngineFailure("not_found")
        AppError.Configuration -> AiError.EngineFailure("configuration")
        AppError.NotSupportedYet -> AiError.EngineFailure(REASON_NOT_SUPPORTED)
        AppError.Storage, AppError.Unknown -> AiError.EngineFailure("unknown")
    }

internal fun <T> AppResult<T>.valueOrThrow(): T =
    when (this) {
        is AppResult.Success -> value
        is AppResult.Failure -> throw AiException(error.toAiError())
    }

internal fun invalidResponse(): Nothing = throw AiException(AiError.EngineFailure(REASON_INVALID_RESPONSE))
