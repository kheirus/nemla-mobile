package app.nemla.core.common

import kotlinx.coroutines.CancellationException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AppResultTest {
    private val success: AppResult<Int> = AppResult.Success(2)
    private val failure: AppResult<Int> = AppResult.Failure(AppError.NotFound)

    @Test
    fun mapTransformsOnlySuccess() {
        assertEquals(AppResult.Success(4), success.map { it * 2 })
        assertEquals(failure, failure.map { it * 2 })
    }

    @Test
    fun flatMapChainsAndShortCircuits() {
        assertEquals(AppResult.Success("2"), success.flatMap { AppResult.Success(it.toString()) })
        assertEquals(AppResult.Failure(AppError.Forbidden), success.flatMap<Int, String> { AppResult.Failure(AppError.Forbidden) })
        assertEquals<AppResult<*>>(failure, failure.flatMap { AppResult.Success(it.toString()) })
    }

    @Test
    fun foldSelectsBranch() {
        assertEquals("ok 2", success.fold({ "ok $it" }, { "ko" }))
        assertEquals("ko NotFound", failure.fold({ "ok" }, { "ko $it" }))
    }

    @Test
    fun accessorsReturnValueOrError() {
        assertEquals(2, success.getOrNull())
        assertNull(failure.getOrNull())
        assertEquals(AppError.NotFound, failure.errorOrNull())
        assertNull(success.errorOrNull())
        assertEquals(7, failure.getOrElse { 7 })
    }

    @Test
    fun callbacksRunOnMatchingBranch() {
        var seen = 0
        var error: AppError? = null
        success.onSuccess { seen = it }.onFailure { error = it }
        assertEquals(2, seen)
        assertNull(error)
        failure.onSuccess { seen = 99 }.onFailure { error = it }
        assertEquals(2, seen)
        assertEquals(AppError.NotFound, error)
    }

    @Test
    fun mapErrorAndRecover() {
        assertEquals(AppResult.Failure(AppError.Unknown), failure.mapError { AppError.Unknown })
        assertEquals(AppResult.Success(0), failure.recover { AppResult.Success(0) })
        assertEquals(success, success.recover { AppResult.Success(0) })
    }

    @Test
    fun runCatchingAppWrapsExceptions() {
        val result = runCatchingApp<Int>(mapError = { AppError.Storage }) { throw IllegalStateException("boom") }
        assertEquals(AppResult.Failure(AppError.Storage), result)
        assertEquals(AppResult.Success(3), runCatchingApp { 3 })
    }

    @Test
    fun runCatchingAppDefaultsToUnknown() {
        val result = runCatchingApp<Int> { throw IllegalArgumentException() }
        assertEquals(AppResult.Failure(AppError.Unknown), result)
    }

    @Test
    fun runCatchingAppRethrowsCancellation() {
        assertFailsWith<CancellationException> {
            runCatchingApp<Int> { throw CancellationException("cancelled") }
        }
    }

    @Test
    fun flagsReflectState() {
        assertTrue(success.isSuccess)
        assertTrue(failure.isFailure)
    }
}
