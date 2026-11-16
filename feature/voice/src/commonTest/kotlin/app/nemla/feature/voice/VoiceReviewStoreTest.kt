package app.nemla.feature.voice

import app.nemla.core.domain.model.AuthState
import app.nemla.core.domain.model.PendingReview
import app.nemla.core.domain.model.ReviewOrigin
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

    @Test
    fun pendingReviewsOpenWithoutAnnouncementAndKeepTheirOrigin() =
        runTest(UnconfinedTestDispatcher()) {
            val store = testStore(scope = backgroundScope)
            store.open(PendingReview(ReviewOrigin.IMPORT, null, listOf(draft("e-1", ""), draft("e-2", ""))))
            assertEquals(ReviewOrigin.IMPORT, store.origin)
            assertNull(store.takeAnnouncement())
            assertEquals(
                listOf("e-1", "e-2"),
                store.pending.value
                    ?.drafts
                    ?.map { it.id?.value },
            )
            assertEquals("", store.pending.value?.transcript)
            store.put(sampleCapture())
            assertEquals(ReviewOrigin.VOICE, store.origin)
            store.open(PendingReview(ReviewOrigin.IMPORT, null, listOf(draft("e-3", ""))))
            store.clear()
            assertEquals(ReviewOrigin.VOICE, store.origin)
        }

    @Test
    fun importedReviewsAreAnnouncedWithTheirCount() =
        runTest(UnconfinedTestDispatcher()) {
            val store = testStore(scope = backgroundScope)
            store.open(PendingReview(ReviewOrigin.IMPORT, null, listOf(draft("e-1", ""), draft("e-2", "")), announce = true))
            assertEquals(ReviewOrigin.IMPORT, store.origin)
            assertEquals(2, store.takeAnnouncement())
            assertNull(store.takeAnnouncement())
        }
}
