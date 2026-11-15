package app.nemla.core.common

import kotlin.coroutines.cancellation.CancellationException

sealed interface AppResult<out T> {
    data class Success<out T>(
        val value: T,
    ) : AppResult<T>

    data class Failure(
        val error: AppError,
    ) : AppResult<Nothing>
}

val AppResult<*>.isSuccess: Boolean
    get() = this is AppResult.Success

val AppResult<*>.isFailure: Boolean
    get() = this is AppResult.Failure

fun <T> AppResult<T>.getOrNull(): T? =
    when (this) {
        is AppResult.Success -> value
        is AppResult.Failure -> null
    }

fun AppResult<*>.errorOrNull(): AppError? =
    when (this) {
        is AppResult.Success -> null
        is AppResult.Failure -> error
    }

inline fun <T> AppResult<T>.getOrElse(onFailure: (AppError) -> T): T =
    when (this) {
        is AppResult.Success -> value
        is AppResult.Failure -> onFailure(error)
    }

inline fun <T, R> AppResult<T>.map(transform: (T) -> R): AppResult<R> =
    when (this) {
        is AppResult.Success -> AppResult.Success(transform(value))
        is AppResult.Failure -> this
    }

inline fun <T, R> AppResult<T>.flatMap(transform: (T) -> AppResult<R>): AppResult<R> =
    when (this) {
        is AppResult.Success -> transform(value)
        is AppResult.Failure -> this
    }

inline fun <T> AppResult<T>.mapError(transform: (AppError) -> AppError): AppResult<T> =
    when (this) {
        is AppResult.Success -> this
        is AppResult.Failure -> AppResult.Failure(transform(error))
    }

inline fun <T> AppResult<T>.recover(transform: (AppError) -> AppResult<T>): AppResult<T> =
    when (this) {
        is AppResult.Success -> this
        is AppResult.Failure -> transform(error)
    }

inline fun <T, R> AppResult<T>.fold(
    onSuccess: (T) -> R,
    onFailure: (AppError) -> R,
): R =
    when (this) {
        is AppResult.Success -> onSuccess(value)
        is AppResult.Failure -> onFailure(error)
    }

inline fun <T> AppResult<T>.onSuccess(action: (T) -> Unit): AppResult<T> {
    if (this is AppResult.Success) action(value)
    return this
}

inline fun <T> AppResult<T>.onFailure(action: (AppError) -> Unit): AppResult<T> {
    if (this is AppResult.Failure) action(error)
    return this
}

fun <T> AppResult<T>.asUnit(): AppResult<Unit> = map { }

fun <T> T.asSuccess(): AppResult<T> = AppResult.Success(this)

fun AppError.asFailure(): AppResult<Nothing> = AppResult.Failure(this)

inline fun <T> runCatchingApp(
    mapError: (Exception) -> AppError = { AppError.Unknown },
    block: () -> T,
): AppResult<T> =
    try {
        AppResult.Success(block())
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        AppResult.Failure(mapError(e))
    }
