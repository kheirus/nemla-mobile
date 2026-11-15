package app.nemla.core.network

import app.nemla.core.common.AppError
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.darwin.Darwin
import io.ktor.client.engine.darwin.DarwinHttpRequestException
import platform.Foundation.NSURLErrorTimedOut

internal actual fun platformHttpEngine(): HttpClientEngine = Darwin.create()

internal actual fun platformNetworkError(error: Throwable): AppError? =
    when {
        error !is DarwinHttpRequestException -> null
        error.origin.code == NSURLErrorTimedOut -> AppError.Network.Timeout
        else -> AppError.Network.Offline
    }
