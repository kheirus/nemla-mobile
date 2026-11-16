package app.nemla.core.data

import app.nemla.core.common.AppError
import app.nemla.core.common.AppResult
import app.nemla.core.data.remote.PREFER_HEADER
import app.nemla.core.domain.model.CategoryId
import app.nemla.core.domain.model.CategoryOutcome
import app.nemla.core.domain.model.ClassificationCorrection
import app.nemla.core.domain.model.ExpenseId
import app.nemla.core.domain.model.ReviewedExpense
import app.nemla.core.domain.model.VoiceEntryId
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ExpenseReviewRepositoryTest {
    private val expense = ReviewedExpense(ExpenseId("e-1"), 1_250, LocalDate(2026, 11, 16), CategoryId("c-1"))

    @Test
    fun confirmationPatchesAmountDateCategoryWithExactDecimals() =
        runTest {
            val harness = DataHarness(store = signedInStore()) { json("""[{"id":"e-1"}]""") }
            assertEquals(AppResult.Success(Unit), harness.reviews.confirm(expense))
            val request = harness.requests.single()
            assertEquals("PATCH", request.method)
            assertEquals("/rest/v1/expenses", request.path)
            assertEquals("eq.e-1", request.query("id"))
            assertEquals("eq.$USER_ID", request.query("user_id"))
            assertEquals("return=representation", request.data.headers[PREFER_HEADER])
            assertEquals("""{"montant":12.50,"date":"2026-11-16","category_id":"c-1","confirmed":true}""", request.body)
        }

    @Test
    fun confirmationWithoutCategorySendsAnExplicitNull() =
        runTest {
            val harness = DataHarness(store = signedInStore()) { json("""[{"id":"e-1"}]""") }
            harness.reviews.confirm(ReviewedExpense(ExpenseId("e-1"), -5, LocalDate(2026, 1, 2), null))
            assertEquals("""{"montant":-0.05,"date":"2026-01-02","category_id":null,"confirmed":true}""", harness.requests.single().body)
        }

    @Test
    fun confirmingAMissingExpenseIsNotFound() =
        runTest {
            val harness = DataHarness(store = signedInStore()) { json("[]") }
            assertEquals(AppError.NotFound, harness.reviews.confirm(expense).failure)
        }

    @Test
    fun confirmationErrorsAreMappedAndSignedOutIsRejectedLocally() =
        runTest {
            val offline = DataHarness(store = signedInStore()) { json("""{"code":"42501","message":"x"}""", HttpStatusCode.Forbidden) }
            assertEquals(AppError.Forbidden, offline.reviews.confirm(expense).failure)
            val signedOut = DataHarness { json("[]") }
            assertEquals(AppError.Unauthorized, signedOut.reviews.confirm(expense).failure)
            assertTrue(signedOut.requests.isEmpty())
        }

    @Test
    fun deletionTargetsTheUsersExpense() =
        runTest {
            val harness = DataHarness(store = signedInStore()) { noContent() }
            assertEquals(AppResult.Success(Unit), harness.reviews.delete(ExpenseId("e-9")))
            val request = harness.requests.single()
            assertEquals("DELETE", request.method)
            assertEquals("/rest/v1/expenses", request.path)
            assertEquals("eq.e-9", request.query("id"))
            assertEquals("eq.$USER_ID", request.query("user_id"))
        }

    @Test
    fun voiceEntryIsMarkedConfirmed() =
        runTest {
            val harness = DataHarness(store = signedInStore()) { noContent() }
            assertEquals(AppResult.Success(Unit), harness.reviews.markVoiceEntryConfirmed(VoiceEntryId("v-1")))
            val request = harness.requests.single()
            assertEquals("PATCH", request.method)
            assertEquals("/rest/v1/voice_entries", request.path)
            assertEquals("eq.v-1", request.query("id"))
            assertEquals("""{"statut":"confirmed"}""", request.body)
        }

    @Test
    fun learnedKeysAreReadFromMerchantRules() =
        runTest {
            val harness = DataHarness(store = signedInStore()) { json("""[{"merchant_key":"marche"}]""") }
            val learned = harness.reviews.learnedMerchantKeys(setOf("marche", "cinema pathe"))
            assertEquals(AppResult.Success(setOf("marche")), learned)
            val request = harness.requests.single()
            assertEquals("/rest/v1/merchant_rules", request.path)
            assertEquals("merchant_key", request.query("select"))
            assertEquals("in.(\"marche\",\"cinema pathe\")", request.query("merchant_key"))
            assertEquals("eq.$USER_ID", request.query("user_id"))
        }

    @Test
    fun unusableOrUnsafeKeysAreNeverSent() =
        runTest {
            val harness = DataHarness(store = signedInStore()) { json("[]") }
            assertEquals(AppResult.Success(emptySet()), harness.reviews.learnedMerchantKeys(setOf("ab", "a\",b", "x)y")))
            assertTrue(harness.requests.isEmpty())
        }

    @Test
    fun categoryOutcomeCallsTheLearningRpcWithoutTheLabel() =
        runTest {
            val harness = DataHarness(store = signedInStore()) { noContent() }
            val outcome = CategoryOutcome(ExpenseId("e-1"), "marche", CategoryId("c-2"), "Courses", "Loisirs", wasCorrection = true)
            assertEquals(AppResult.Success(Unit), harness.reviews.recordCategoryOutcome(outcome))
            val request = harness.requests.single()
            assertEquals("POST", request.method)
            assertEquals("/rest/v1/rpc/record_category_correction", request.path)
            assertEquals(
                """{"p_merchant_key":"marche","p_label":"","p_category_id":"c-2","p_suggested_category":"Courses",""" +
                    """"p_chosen_category":"Loisirs","p_was_correction":true,"p_expense_id":"e-1"}""",
                request.body,
            )
        }

    @Test
    fun shortMerchantKeysAreNotLearned() =
        runTest {
            val harness = DataHarness(store = signedInStore()) { noContent() }
            val outcome = CategoryOutcome(ExpenseId("e-1"), "ab", null, null, null, wasCorrection = false)
            assertEquals(AppResult.Success(Unit), harness.reviews.recordCategoryOutcome(outcome))
            assertTrue(harness.requests.isEmpty())
        }

    @Test
    fun classificationLogIsNotSupportedYet() =
        runTest {
            val harness = DataHarness(store = signedInStore()) { noContent() }
            val correction = ClassificationCorrection(ExpenseId("e-1"), null, null, null, 100, 200)
            assertEquals(AppError.NotSupportedYet, harness.reviews.logCorrection(correction).failure)
            assertTrue(harness.requests.isEmpty())
        }
}
