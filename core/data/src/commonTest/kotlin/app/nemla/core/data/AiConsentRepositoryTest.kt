package app.nemla.core.data

import app.cash.turbine.test
import app.nemla.core.common.AppResult
import app.nemla.core.common.NoOpLogger
import app.nemla.core.common.getOrNull
import app.nemla.core.data.consent.SecureStoreAiConsentRepository
import app.nemla.core.domain.model.AiConsentLevel
import app.nemla.core.domain.model.AiConsents
import app.nemla.core.security.InMemorySecureStore
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Instant

class AiConsentRepositoryTest {
    private val clock = FixedClock()

    @Test
    fun grantsArePersistedWithTheirDate() =
        runTest {
            val store = InMemorySecureStore()
            val repository = SecureStoreAiConsentRepository(store, clock, NoOpLogger)
            assertEquals(AppResult.Success(Unit), repository.grant(AiConsentLevel.ANONYMIZED))
            val reloaded = SecureStoreAiConsentRepository(store, clock, NoOpLogger).current().getOrNull()!!
            assertEquals(mapOf(AiConsentLevel.ANONYMIZED to Instant.fromEpochMilliseconds(now.toEpochMilliseconds())), reloaded.grants)
            assertEquals("""{"ANONYMIZED":${now.toEpochMilliseconds()}}""", store.snapshot()[SecureStoreAiConsentRepository.KEY])
        }

    @Test
    fun grantingTwiceKeepsTheFirstDate() =
        runTest {
            val repository = SecureStoreAiConsentRepository(InMemorySecureStore(), clock, NoOpLogger)
            repository.grant(AiConsentLevel.PERSONAL)
            clock.instant = Instant.fromEpochSeconds(now.epochSeconds + 60)
            repository.grant(AiConsentLevel.PERSONAL)
            assertEquals(now, repository.current().getOrNull()!!.grants[AiConsentLevel.PERSONAL])
        }

    @Test
    fun observeEmitsChanges() =
        runTest {
            val repository = SecureStoreAiConsentRepository(InMemorySecureStore(), clock, NoOpLogger)
            repository.observe().test {
                assertEquals(AiConsents.None, awaitItem())
                repository.grant(AiConsentLevel.PERSONAL)
                assertEquals(setOf(AiConsentLevel.PERSONAL), awaitItem().levels)
                repository.revoke(AiConsentLevel.PERSONAL)
                assertEquals(AiConsents.None, awaitItem())
            }
        }

    @Test
    fun clearRemovesEverything() =
        runTest {
            val store = InMemorySecureStore()
            val repository = SecureStoreAiConsentRepository(store, clock, NoOpLogger)
            repository.grant(AiConsentLevel.PERSONAL)
            repository.clear()
            assertEquals(AiConsents.None, repository.current().getOrNull())
            assertTrue(store.snapshot().isEmpty())
        }

    @Test
    fun unreadableEntryMeansNoConsent() =
        runTest {
            val store = InMemorySecureStore(mapOf(SecureStoreAiConsentRepository.KEY to "garbage"))
            assertEquals(AiConsents.None, SecureStoreAiConsentRepository(store, clock, NoOpLogger).current().getOrNull())
            val unknownLevel = InMemorySecureStore(mapOf(SecureStoreAiConsentRepository.KEY to """{"FUTURE":1,"PERSONAL":2}"""))
            assertEquals(
                setOf(AiConsentLevel.PERSONAL),
                SecureStoreAiConsentRepository(unknownLevel, clock, NoOpLogger).current().getOrNull()!!.levels,
            )
        }
}
