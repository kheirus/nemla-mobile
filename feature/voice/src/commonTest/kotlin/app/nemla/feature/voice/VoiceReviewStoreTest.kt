package app.nemla.feature.voice

import app.nemla.core.domain.model.AuthState
import app.nemla.core.domain.model.Session
import app.nemla.core.domain.model.UserId
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

@OptIn(ExperimentalCoroutinesApi::class)
class VoiceReviewStoreTest {
    @Test
    fun signingOutForgetsThePendingDictation() =
        runTest(UnconfinedTestDispatcher()) {
            val auth = FakeAuth()
            val store = testStore(auth, backgroundScope)
            store.put(sampleCapture())
            auth.state.value = AuthState.SignedIn(testSession)
            assertEquals(sampleCapture(), store.pending.value)
            auth.state.value = AuthState.SignedOut
            assertNull(store.pending.value)
        }

    @Test
    fun anotherAccountNeverSeesThePreviousDictation() =
        runTest(UnconfinedTestDispatcher()) {
            val auth = FakeAuth()
            val store = testStore(auth, backgroundScope)
            store.put(sampleCapture())
            auth.state.value = AuthState.SignedIn(Session(UserId("user-2"), email = null, expiresAt = null))
            assertNull(store.pending.value)
            assertNull(store.takeAnnouncement())
        }

    @Test
    fun announcementIsGivenOnce() =
        runTest(UnconfinedTestDispatcher()) {
            val store = testStore(scope = backgroundScope)
            store.put(sampleCapture(draft("e-1", "A"), draft("e-2", "B")))
            assertEquals(2, store.takeAnnouncement())
            assertNull(store.takeAnnouncement())
        }
}
