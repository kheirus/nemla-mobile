package app.nemla.core.common

sealed interface AppError {
    sealed interface Network : AppError {
        data object Offline : Network

        data object Timeout : Network
    }

    data object Unauthorized : AppError

    data object Forbidden : AppError

    data object NotFound : AppError

    data class Validation(
        val code: ValidationCode,
    ) : AppError

    data object QuotaExceeded : AppError

    data object RateLimited : AppError

    data object ConsentRequired : AppError

    data class Server(
        val status: Int?,
    ) : AppError

    data object Storage : AppError

    data object Configuration : AppError

    data object NotSupportedYet : AppError

    data object Unknown : AppError
}

enum class ValidationCode {
    EMAIL_REQUIRED,
    EMAIL_INVALID,
    PASSWORD_REQUIRED,
    PASSWORD_TOO_SHORT,
    PASSWORD_TOO_LONG,
    PASSWORD_TOO_WEAK,
    AGE_OUT_OF_RANGE,
    INVALID_CREDENTIALS,
    EMAIL_NOT_CONFIRMED,
    EMAIL_ALREADY_REGISTERED,
    ALREADY_EXISTS,
    NO_CATEGORY,
    NO_SPEECH_DETECTED,
    NO_EXPENSE_DETECTED,
    UNREADABLE_FILE,
    PERIOD_TOO_LONG,
    PAYLOAD_TOO_LARGE,
    INVALID_INPUT,
}
