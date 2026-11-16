package app.nemla.core.domain

import app.nemla.core.common.AppError
import app.nemla.core.common.AppResult
import app.nemla.core.common.ValidationCode
import app.nemla.core.domain.model.Category
import app.nemla.core.domain.model.CategoryBudget
import app.nemla.core.domain.model.CategoryIcon
import app.nemla.core.domain.model.CategoryId
import app.nemla.core.domain.model.CategoryOutcome
import app.nemla.core.domain.model.CategoryType
import app.nemla.core.domain.model.ExpenseDraft
import app.nemla.core.domain.model.ExpenseId
import app.nemla.core.domain.model.ReviewDecision
import app.nemla.core.domain.model.ReviewedExpense
import app.nemla.core.domain.model.VoiceEntryId
import app.nemla.core.domain.usecase.ConfirmReviewedExpenses
import app.nemla.core.domain.usecase.FindLearnedExpenses
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ReviewUseCasesTest {
    private val reviews = FakeExpenseReviewRepository()
    private val day = LocalDate(2026, 11, 16)
    private val groceries = category("c-1", "Courses")
    private val leisure = category("c-2", "Loisirs")

    private fun category(
        id: String,
        name: String,
    ) = Category(CategoryId(id), name, "#000000", CategoryIcon.MORE, CategoryType.VARIABLE, true, 1, true, CategoryBudget.None)

    private fun draft(
        id: String?,
        label: String,
        cents: Long = 1_000,
        categoryId: CategoryId? = groceries.id,
    ) = ExpenseDraft(id?.let(::ExpenseId), cents, label, day, categoryId, null)

    @Test
    fun everyLineIsConfirmedThenTheDictationIsMarked() =
        runTest {
            val decisions =
                listOf(
                    ReviewDecision(draft("e-1", "Marché"), 1_000, day, groceries.id),
                    ReviewDecision(draft("e-2", "Cinéma Pathé"), 1_250, LocalDate(2026, 11, 15), leisure.id),
                )
            val result = ConfirmReviewedExpenses(reviews)(VoiceEntryId("v-1"), decisions, listOf(groceries, leisure))
            assertEquals(AppResult.Success(Unit), result)
            assertEquals(
                listOf(
                    ReviewedExpense(ExpenseId("e-1"), 1_000, day, groceries.id),
                    ReviewedExpense(ExpenseId("e-2"), 1_250, LocalDate(2026, 11, 15), leisure.id),
                ),
                reviews.confirmed,
            )
            assertEquals(listOf(VoiceEntryId("v-1")), reviews.marked)
        }

    @Test
    fun learningReportsSuggestedAndChosenCategoryNames() =
        runTest {
            val decisions =
                listOf(
                    ReviewDecision(draft("e-1", "Marché"), 1_000, day, groceries.id),
                    ReviewDecision(draft("e-2", "Cinéma Pathé"), 1_000, day, leisure.id),
                )
            ConfirmReviewedExpenses(reviews)(null, decisions, listOf(groceries, leisure))
            assertEquals(
                listOf(
                    CategoryOutcome(ExpenseId("e-1"), "marche", groceries.id, "Courses", "Courses", wasCorrection = false),
                    CategoryOutcome(ExpenseId("e-2"), "cinema pathe", leisure.id, "Courses", "Loisirs", wasCorrection = true),
                ),
                reviews.outcomes,
            )
            assertEquals(1, reviews.corrections.size)
            assertTrue(reviews.marked.isEmpty())
        }

    @Test
    fun amountChangesAreLoggedAsCorrectionsAndUnusableKeysAreNotLearned() =
        runTest {
            val decisions = listOf(ReviewDecision(draft("e-1", "le 12"), 900, day, groceries.id))
            ConfirmReviewedExpenses(reviews)(null, decisions, listOf(groceries))
            assertTrue(reviews.outcomes.isEmpty())
            assertEquals(1_000, reviews.corrections.single().suggestedAmountCents)
            assertEquals(900, reviews.corrections.single().finalAmountCents)
        }

    @Test
    fun aFailedConfirmationStopsBeforeMarkingAndLearning() =
        runTest {
            reviews.confirmResult = AppResult.Failure(AppError.Network.Offline)
            val decisions = listOf(ReviewDecision(draft("e-1", "Marché"), 1_000, day, groceries.id))
            val result = ConfirmReviewedExpenses(reviews)(VoiceEntryId("v-1"), decisions, listOf(groceries))
            assertEquals(AppResult.Failure(AppError.Network.Offline), result)
            assertTrue(reviews.marked.isEmpty())
            assertTrue(reviews.outcomes.isEmpty())
        }

    @Test
    fun learningFailuresDoNotFailTheConfirmation() =
        runTest {
            reviews.outcomeResult = AppResult.Failure(AppError.Server(500))
            val decisions = listOf(ReviewDecision(draft("e-1", "Marché"), 1_000, day, null))
            assertEquals(AppResult.Success(Unit), ConfirmReviewedExpenses(reviews)(null, decisions, listOf(groceries)))
        }

    @Test
    fun linesWithoutIdentifierAreRejected() =
        runTest {
            val decisions = listOf(ReviewDecision(draft(null, "Marché"), 1_000, day, groceries.id))
            val result = ConfirmReviewedExpenses(reviews)(null, decisions, emptyList())
            assertEquals(AppResult.Failure(AppError.Validation(ValidationCode.INVALID_INPUT)), result)
            assertTrue(reviews.confirmed.isEmpty())
        }

    @Test
    fun learnedExpensesAreMatchedByMerchantKey() =
        runTest {
            reviews.learned = AppResult.Success(setOf("marche"))
            val learned = FindLearnedExpenses(reviews)(listOf(draft("e-1", "Marché"), draft("e-2", "Cinéma"), draft("e-3", "le")))
            assertEquals(setOf(ExpenseId("e-1")), learned)
            assertEquals(setOf("marche", "cinema"), reviews.learnedQueries.single())
        }

    @Test
    fun learnedLookupFailureMeansNothingLearned() =
        runTest {
            reviews.learned = AppResult.Failure(AppError.Network.Offline)
            assertEquals(emptySet(), FindLearnedExpenses(reviews)(listOf(draft("e-1", "Marché"))))
            val none = FindLearnedExpenses(FakeExpenseReviewRepository())(listOf(draft("e-1", "le")))
            assertEquals(emptySet(), none)
        }
}
