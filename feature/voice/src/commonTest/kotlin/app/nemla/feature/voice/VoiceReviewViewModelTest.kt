package app.nemla.feature.voice

import app.cash.turbine.test
import app.nemla.core.common.AppError
import app.nemla.core.common.AppResult
import app.nemla.core.domain.model.CategoryId
import app.nemla.core.domain.model.ExpenseDraft
import app.nemla.core.domain.model.ExpenseId
import app.nemla.core.domain.model.PendingReview
import app.nemla.core.domain.model.ReviewOrigin
import app.nemla.core.domain.model.ReviewedExpense
import app.nemla.core.domain.model.VoiceEntryId
import app.nemla.core.domain.usecase.ConfirmReviewedExpenses
import app.nemla.core.domain.usecase.DeletePendingExpense
import app.nemla.core.domain.usecase.FindLearnedExpenses
import app.nemla.core.domain.usecase.ListCategories
import app.nemla.feature.voice.review.CategoryChoice
import app.nemla.feature.voice.review.ReviewMessage
import app.nemla.feature.voice.review.VoiceReviewAction
import app.nemla.feature.voice.review.VoiceReviewEffect
import app.nemla.feature.voice.review.VoiceReviewViewModel
import app.nemla.feature.voice.review.display
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class VoiceReviewViewModelTest : MainDispatcherTest() {
    private val reviews = FakeReviews()
    private val categories = FakeCategories()

    private fun TestScope.storeWith(vararg drafts: ExpenseDraft): VoiceReviewStore =
        testStore(scope = backgroundScope).also { it.put(sampleCapture(*drafts)) }

    private fun viewModel(store: VoiceReviewStore) =
        VoiceReviewViewModel(
            store = store,
            listCategories = ListCategories(categories),
            findLearned = FindLearnedExpenses(reviews),
            confirmReviewed = ConfirmReviewedExpenses(reviews),
            deletePending = DeletePendingExpense(reviews),
            today = { testToday },
            decimalSeparator = ',',
        )

    @Test
    fun linesComeFromTheDictationWithLearnedCategories() =
        runTest {
            reviews.learned = setOf("marche")
            val store = storeWith(draft("e-1", "Marché", 1_250), draft("e-2", "Cinéma", 900, categoryId = null))
            val vm = viewModel(store)
            val state = vm.state.value
            assertFalse(state.isLoading)
            assertEquals("marché douze euros", state.transcript)
            assertEquals(2, state.detectedCount)
            assertEquals(listOf("12,50", "9,00"), state.lines.map { it.amountText })
            assertEquals(listOf(true, false), state.lines.map { it.learned })
            assertEquals(listOf(groceries.id, null), state.lines.map { it.categoryId })
            assertEquals(listOf(CategoryChoice(groceries.id, "Courses"), CategoryChoice(leisure.id, "Loisirs")), state.categories)
            assertTrue(state.canConfirm)
            assertNull(viewModel(store).state.value.detectedCount)
        }

    @Test
    fun confirmationSendsEditedValuesAndClosesTheSheet() =
        runTest {
            val store = storeWith(draft("e-1", "Marché", 1_250), draft("e-2", "Cinéma", 900, categoryId = null))
            val vm = viewModel(store)
            vm.onAction(VoiceReviewAction.AmountChanged(ExpenseId("e-1"), "13,4"))
            vm.onAction(VoiceReviewAction.AmountChanged(ExpenseId("e-1"), "13,456"))
            vm.onAction(VoiceReviewAction.CategorySelected(ExpenseId("e-2"), leisure.id))
            vm.onAction(VoiceReviewAction.DateSelected(ExpenseId("e-2"), LocalDate(2026, 11, 2)))
            vm.onAction(VoiceReviewAction.AmountChanged(ExpenseId("e-2"), ""))
            vm.effects.test {
                vm.onAction(VoiceReviewAction.ConfirmClicked)
                assertEquals(VoiceReviewEffect.Close, awaitItem())
            }
            assertEquals(
                listOf(
                    ReviewedExpense(ExpenseId("e-1"), 1_340, testToday, groceries.id),
                    ReviewedExpense(ExpenseId("e-2"), 0, LocalDate(2026, 11, 2), leisure.id),
                ),
                reviews.confirmed,
            )
            assertEquals(listOf(VoiceEntryId("v-1")), reviews.marked)
            assertEquals(2, reviews.outcomes.size)
            assertNull(store.pending.value)
            assertEquals(ReviewOutcome.SAVED, store.outcome.value)
        }

    @Test
    fun futureDatesAreBroughtBackToToday() =
        runTest {
            val vm = viewModel(storeWith(draft("e-1", "Marché", date = LocalDate(2026, 12, 24))))
            assertEquals(
                testToday,
                vm.state.value.lines
                    .single()
                    .date,
            )
            vm.onAction(VoiceReviewAction.DateSelected(ExpenseId("e-1"), LocalDate(2027, 1, 1)))
            assertEquals(
                testToday,
                vm.state.value.lines
                    .single()
                    .date,
            )
            assertEquals("16/11/2026", testToday.display())
        }

    @Test
    fun failedConfirmationKeepsTheSheetOpenWithATranslatedMessage() =
        runTest {
            reviews.confirmResult = AppResult.Failure(AppError.Network.Offline)
            val store = storeWith()
            val vm = viewModel(store)
            vm.onAction(VoiceReviewAction.ConfirmClicked)
            assertEquals(ReviewMessage.OFFLINE, vm.state.value.message)
            assertFalse(vm.state.value.isSaving)
            assertEquals(sampleCapture(), store.pending.value)
            reviews.confirmResult = AppResult.Failure(AppError.Server(500))
            vm.onAction(VoiceReviewAction.ConfirmClicked)
            assertEquals(ReviewMessage.SAVE_FAILED, vm.state.value.message)
        }

    @Test
    fun deletingALineRemovesItImmediately() =
        runTest {
            val store = storeWith(draft("e-1", "Marché"), draft("e-2", "Cinéma"))
            val vm = viewModel(store)
            vm.onAction(VoiceReviewAction.DeleteClicked(ExpenseId("e-1")))
            assertEquals(listOf(ExpenseId("e-1")), reviews.deleted)
            assertEquals(
                listOf(ExpenseId("e-2")),
                vm.state.value.lines
                    .map { it.id },
            )
            assertEquals(
                listOf(ExpenseId("e-2")),
                store.pending.value
                    ?.drafts
                    ?.map { it.id },
            )
            vm.onAction(VoiceReviewAction.DeleteClicked(ExpenseId("e-2")))
            assertTrue(
                vm.state.value.lines
                    .isEmpty(),
            )
            assertFalse(vm.state.value.canConfirm)
        }

    @Test
    fun failedDeletionKeepsTheLine() =
        runTest {
            reviews.deleteResult = AppResult.Failure(AppError.Network.Offline)
            val vm = viewModel(storeWith())
            vm.onAction(VoiceReviewAction.DeleteClicked(ExpenseId("e-1")))
            assertEquals(1, vm.state.value.lines.size)
            assertFalse(
                vm.state.value.lines
                    .single()
                    .isDeleting,
            )
            assertEquals(ReviewMessage.DELETE_FAILED, vm.state.value.message)
            vm.onAction(VoiceReviewAction.MessageDismissed)
            assertNull(vm.state.value.message)
        }

    @Test
    fun unavailableCategoriesAreReported() =
        runTest {
            categories.result = AppResult.Failure(AppError.Network.Timeout)
            val vm = viewModel(storeWith())
            assertEquals(ReviewMessage.CATEGORIES_UNAVAILABLE, vm.state.value.message)
            assertEquals(
                groceries.id,
                vm.state.value.lines
                    .single()
                    .categoryId,
            )
        }

    @Test
    fun deletedCategoriesAreTreatedAsMissing() =
        runTest {
            val vm = viewModel(storeWith(draft("e-1", "Marché", categoryId = CategoryId("gone"))))
            assertNull(
                vm.state.value.lines
                    .single()
                    .categoryId,
            )
        }

    @Test
    fun withoutPendingDictationTheSheetIsEmpty() =
        runTest {
            val vm = viewModel(testStore(scope = backgroundScope))
            assertFalse(vm.state.value.isLoading)
            assertTrue(
                vm.state.value.lines
                    .isEmpty(),
            )
            assertFalse(vm.state.value.canConfirm)
            vm.effects.test {
                vm.onAction(VoiceReviewAction.LaterClicked)
                assertEquals(VoiceReviewEffect.Close, awaitItem())
            }
        }

    @Test
    fun pendingExpensesFromTheDashboardOpenWithoutTranscriptNorAnnouncement() =
        runTest {
            val store = testStore(scope = backgroundScope)
            store.open(
                PendingReview(
                    ReviewOrigin.VOICE,
                    VoiceEntryId("v-9"),
                    listOf(draft("e-1", "", 700), draft("e-2", "", 300, categoryId = CategoryId("gone"))),
                ),
            )
            val vm = viewModel(store)
            val state = vm.state.value
            assertEquals(ReviewOrigin.VOICE, state.origin)
            assertNull(state.transcript)
            assertNull(state.detectedCount)
            assertEquals(listOf("", ""), state.lines.map { it.label })
            assertEquals(listOf(groceries.id, null), state.lines.map { it.categoryId })
            vm.effects.test {
                vm.onAction(VoiceReviewAction.ConfirmClicked)
                assertEquals(VoiceReviewEffect.Close, awaitItem())
            }
            assertEquals(listOf(VoiceEntryId("v-9")), reviews.marked)
            assertTrue(reviews.outcomes.isEmpty())
            assertNull(store.pending.value)
        }

    @Test
    fun importGroupsKeepTheirOriginAndAreNotMarkedAsDictations() =
        runTest {
            val store = testStore(scope = backgroundScope)
            store.open(PendingReview(ReviewOrigin.IMPORT, null, listOf(draft("e-1", ""))))
            val vm = viewModel(store)
            assertEquals(ReviewOrigin.IMPORT, vm.state.value.origin)
            vm.effects.test {
                vm.onAction(VoiceReviewAction.ConfirmClicked)
                assertEquals(VoiceReviewEffect.Close, awaitItem())
            }
            assertTrue(reviews.marked.isEmpty())
            assertEquals(listOf(ExpenseId("e-1")), reviews.confirmed.map { it.id })
        }
}
