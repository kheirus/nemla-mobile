package app.nemla.feature.voice

import app.nemla.core.domain.model.AuthState
import app.nemla.core.domain.model.ExpenseId
import app.nemla.core.domain.model.PendingReview
import app.nemla.core.domain.model.ReviewOrigin
import app.nemla.core.domain.model.UserId
import app.nemla.core.domain.model.VoiceCapture
import app.nemla.core.domain.port.PendingReviewOpener
import app.nemla.core.domain.usecase.ObserveAuthState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

internal enum class ReviewOutcome { SAVED }

internal class VoiceReviewStore(
    observeAuthState: ObserveAuthState,
    private val scope: CoroutineScope,
) {
    private val mutablePending = MutableStateFlow<VoiceCapture?>(null)
    val pending: StateFlow<VoiceCapture?> = mutablePending.asStateFlow()

    private val mutableOutcome = MutableStateFlow<ReviewOutcome?>(null)
    val outcome: StateFlow<ReviewOutcome?> = mutableOutcome.asStateFlow()

    var origin: ReviewOrigin = ReviewOrigin.VOICE
        private set

    private var announcement: Int? = null
    private var owner: UserId? = null

    init {
        scope.launch {
            observeAuthState().collect { state ->
                val user = (state as? AuthState.SignedIn)?.session?.userId
                if (user == null || (owner != null && user != owner)) clear()
                owner = user
            }
        }
    }

    fun put(capture: VoiceCapture) {
        origin = ReviewOrigin.VOICE
        mutablePending.value = capture
        announcement = capture.drafts.size
    }

    fun open(review: PendingReview) {
        origin = review.origin
        announcement = null
        mutablePending.value = VoiceCapture(review.voiceEntryId, transcript = "", drafts = review.drafts)
    }

    fun takeAnnouncement(): Int? = announcement.also { announcement = null }

    fun remove(id: ExpenseId) {
        mutablePending.update { capture ->
            capture?.let { VoiceCapture(it.entryId, it.transcript, it.drafts.filterNot { draft -> draft.id == id }) }
        }
    }

    fun publish(outcome: ReviewOutcome) {
        mutableOutcome.value = outcome
    }

    fun consumeOutcome(): ReviewOutcome? = mutableOutcome.value.also { mutableOutcome.value = null }

    fun clear() {
        mutablePending.value = null
        announcement = null
        origin = ReviewOrigin.VOICE
    }

    fun close() {
        clear()
        scope.cancel()
    }
}

internal class StorePendingReviewOpener(
    private val store: VoiceReviewStore,
) : PendingReviewOpener {
    override fun open(review: PendingReview) = store.open(review)
}
