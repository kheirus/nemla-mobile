package app.nemla.core.network

import app.nemla.core.common.AppError
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.okhttp.OkHttp
import java.io.InterruptedIOException

internal actual fun platformHttpEngine(): HttpClientEngine = OkHttp.create()

internal actual fun platformNetworkError(error: Throwable): AppError? =
    when (error) {
        is java.net.SocketTimeoutException -> AppError.Network.Timeout
        is InterruptedIOException -> if (error.message == "timeout") AppError.Network.Timeout else null
        else -> null
    }
