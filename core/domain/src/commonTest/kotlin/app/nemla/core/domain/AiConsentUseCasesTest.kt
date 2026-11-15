package app.nemla.core.domain

import app.cash.turbine.test
import app.nemla.core.common.AppError
import app.nemla.core.common.AppResult
import app.nemla.core.domain.model.AiConsentLevel
import app.nemla.core.domain.model.AiConsents
import app.nemla.core.domain.usecase.GrantAiConsent
import app.nemla.core.domain.usecase.ObserveAiConsents
import app.nemla.core.domain.usecase.RevokeAiConsent
import app.nemla.core.domain.usecase.RevokeAllAiConsents
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AiConsentUseCasesTest {
    private val repository = FakeAiConsentRepository()

    @Test
    fun grantAndRevokeArePerLevel() =
        runTest {
            ObserveAiConsents(repository)().test {
                assertEquals(AiConsents.None, awaitItem())
                assertEquals(AppResult.Success(Unit), GrantAiConsent(repository)(AiConsentLevel.ANONYMIZED))
                val granted = awaitItem()
                assertTrue(granted.isGranted(AiConsentLevel.ANONYMIZED))
                assertFalse(granted.isGranted(AiConsentLevel.PERSONAL))
                GrantAiConsent(repository)(AiConsentLevel.PERSONAL)
                assertEquals(setOf(AiConsentLevel.ANONYMIZED, AiConsentLevel.PERSONAL), awaitItem().levels)
                RevokeAiConsent(repository)(AiConsentLevel.ANONYMIZED)
                assertEquals(setOf(AiConsentLevel.PERSONAL), awaitItem().levels)
            }
        }

    @Test
    fun revokeAllClearsEveryLevel() =
        runTest {
            GrantAiConsent(repository)(AiConsentLevel.ANONYMIZED)
            GrantAiConsent(repository)(AiConsentLevel.PERSONAL)
            assertEquals(AppResult.Success(Unit), RevokeAllAiConsents(repository)())
            assertEquals(AiConsents.None, repository.state.value)
            assertEquals(AiConsentLevel.entries.toList(), repository.revoked.toList())
        }

    @Test
    fun revokeAllStopsOnFirstFailure() =
        runTest {
            repository.failure = AppResult.Failure(AppError.Storage)
            assertEquals(AppResult.Failure(AppError.Storage), RevokeAllAiConsents(repository)())
            assertTrue(repository.revoked.isEmpty())
        }
}
