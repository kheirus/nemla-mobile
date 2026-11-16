package app.nemla.core.data

import app.nemla.core.common.AppError
import app.nemla.core.common.AppResult
import app.nemla.core.common.getOrNull
import app.nemla.core.data.remote.EXPENSE_COLUMNS
import app.nemla.core.data.remote.UnsupportedRecurringChargeMaterializer
import app.nemla.core.domain.model.CategoryId
import app.nemla.core.domain.model.ChargeFrequency
import app.nemla.core.domain.model.DeferredDebit
import app.nemla.core.domain.model.ExpenseEdit
import app.nemla.core.domain.model.ExpenseId
import app.nemla.core.domain.model.ExpenseQuery
import app.nemla.core.domain.model.ExpenseSource
import app.nemla.core.domain.model.PaymentMethod
import app.nemla.core.domain.model.RecurringChargeId
import app.nemla.core.domain.model.VoiceEntryId
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

class ExpenseRepositoryTest {
    private val row =
        """{"id":"e-1","category_id":"c-1","voice_entry_id":"v-1","recurring_expense_id":null,"montant":12.5,"date":"2026-11-16",
           "date_prelevement":"2026-12-05","source":"voice","moyen_paiement":"carte","confirmed":false,
           "created_at":"2026-11-16T09:30:00.123456+00:00"}"""

    @Test
    fun listReadsAmountsAndDatesWithoutLabels() =
        runTest {
            val harness = DataHarness(store = signedInStore()) { json("[$row]") }
            val expense =
                harness.expenses
                    .list(ExpenseQuery(confirmed = false))
                    .getOrNull()!!
                    .single()
            assertEquals(ExpenseId("e-1"), expense.id)
            assertEquals(CategoryId("c-1"), expense.categoryId)
            assertEquals(VoiceEntryId("v-1"), expense.voiceEntryId)
            assertNull(expense.recurringChargeId)
            assertEquals(1_250, expense.amountCents)
            assertEquals(LocalDate(2026, 11, 16), expense.date)
            assertEquals(LocalDate(2026, 12, 5), expense.debitDate)
            assertEquals(ExpenseSource.VOICE, expense.source)
            assertEquals(PaymentMethod.CARD, expense.paymentMethod)
            assertFalse(expense.confirmed)
            assertEquals(Instant.parse("2026-11-16T09:30:00.123456Z"), expense.createdAt)
            assertNull(expense.label)
            assertFalse(expense.toString().contains("12"))
            val request = harness.requests.single()
            assertEquals("GET", request.method)
            assertEquals("/rest/v1/expenses", request.path)
            assertEquals(EXPENSE_COLUMNS, request.query("select"))
            assertFalse(request.query("select")!!.contains("description"))
            assertEquals("eq.$USER_ID", request.query("user_id"))
            assertEquals("is.false", request.query("confirmed"))
            assertEquals("date.desc,created_at.desc,id.desc", request.query("order"))
            assertEquals("1000", request.query("limit"))
            assertNull(request.query("offset"))
        }

    @Test
    fun dateRangeAndExactDateAreSentAsPostgrestFilters() =
        runTest {
            val harness = DataHarness(store = signedInStore()) { json("[]") }
            harness.expenses.list(ExpenseQuery(dateFrom = LocalDate(2026, 1, 1), dateTo = LocalDate(2026, 12, 31), confirmed = true))
            harness.expenses.list(ExpenseQuery(dateEquals = LocalDate(2026, 11, 2), confirmed = true, limit = 50))
            val (range, exact) = harness.requests
            assertEquals(
                listOf("gte.2026-01-01", "lte.2026-12-31"),
                range.data.url.parameters
                    .getAll("date"),
            )
            assertEquals("is.true", range.query("confirmed"))
            assertEquals(
                listOf("eq.2026-11-02"),
                exact.data.url.parameters
                    .getAll("date"),
            )
            assertEquals("50", exact.query("limit"))
        }

    @Test
    fun largeYearsAreReadPageByPage() =
        runTest {
            val page = (1..1_000).joinToString(",", "[", "]") { """{"id":"e-$it","montant":"1.00","date":"2026-03-01"}""" }
            val harness =
                DataHarness(store = signedInStore()) { request ->
                    if (request.query("offset") == null) json(page) else json("""[{"id":"last","montant":2,"date":"2026-01-01"}]""")
                }
            val expenses = harness.expenses.list(ExpenseQuery(confirmed = true)).getOrNull()!!
            assertEquals(1_001, expenses.size)
            assertEquals(listOf(null, "1000"), harness.requests.map { it.query("offset") })
            assertEquals(LocalDate(2026, 3, 1), expenses.first().debitDate)
            assertEquals(PaymentMethod.CARD, expenses.first().paymentMethod)
            assertEquals(ExpenseSource.MANUAL, expenses.first().source)
        }

    @Test
    fun malformedDatesAreSkippedAndErrorsMapped() =
        runTest {
            val harness = DataHarness(store = signedInStore()) { json("""[{"id":"x","montant":1,"date":"bad"}]""") }
            assertEquals(AppResult.Success(emptyList()), harness.expenses.list(ExpenseQuery()))
            val failing = DataHarness(store = signedInStore()) { json("""{"message":"x"}""", HttpStatusCode.InternalServerError) }
            assertTrue(failing.expenses.list(ExpenseQuery()).failure is AppError.Server)
            val signedOut = DataHarness { json("[]") }
            assertEquals(AppError.Unauthorized, signedOut.expenses.list(ExpenseQuery()).failure)
            assertTrue(signedOut.requests.isEmpty())
        }

    @Test
    fun editPatchesAmountDateCategoryAndPaymentMethod() =
        runTest {
            val harness = DataHarness(store = signedInStore()) { json("""[{"id":"e-1"}]""") }
            val edit = ExpenseEdit(4_999, LocalDate(2026, 11, 3), null, PaymentMethod.CASH)
            assertEquals(AppResult.Success(Unit), harness.expenses.update(ExpenseId("e-1"), edit))
            val request = harness.requests.single()
            assertEquals("PATCH", request.method)
            assertEquals("eq.e-1", request.query("id"))
            assertEquals("eq.$USER_ID", request.query("user_id"))
            assertEquals("""{"montant":49.99,"date":"2026-11-03","category_id":null,"moyen_paiement":"especes"}""", request.body)
            assertFalse(request.body.contains("description"))
            val missing = DataHarness(store = signedInStore()) { json("[]") }
            assertEquals(AppError.NotFound, missing.expenses.update(ExpenseId("e-1"), edit).failure)
        }

    @Test
    fun everyPaymentMethodIsMapped() =
        runTest {
            val harness = DataHarness(store = signedInStore()) { json("""[{"id":"e-1"}]""") }
            PaymentMethod.entries.forEach { method ->
                harness.expenses.update(ExpenseId("e-1"), ExpenseEdit(100, LocalDate(2026, 1, 1), CategoryId("c"), method))
            }
            assertEquals(
                listOf("carte", "virement", "especes", "cheque"),
                harness.requests.map { it.body.substringAfter("\"moyen_paiement\":\"").substringBefore('"') },
            )
        }

    @Test
    fun deleteTargetsTheUsersExpense() =
        runTest {
            val harness = DataHarness(store = signedInStore()) { noContent() }
            assertEquals(AppResult.Success(Unit), harness.expenses.delete(ExpenseId("e-7")))
            val request = harness.requests.single()
            assertEquals("DELETE", request.method)
            assertEquals("eq.e-7", request.query("id"))
            assertEquals("eq.$USER_ID", request.query("user_id"))
        }

    @Test
    fun labelSearchIsNotSupportedYet() =
        runTest {
            val harness = DataHarness(store = signedInStore()) { json("[]") }
            assertEquals(AppError.NotSupportedYet, harness.expenses.searchByLabel("café", 50).failure)
            assertTrue(harness.requests.isEmpty())
            assertEquals(AppError.NotSupportedYet, UnsupportedRecurringChargeMaterializer.materialize(2026).failure)
        }

    @Test
    fun recurringChargesAreReadWithTheirFrequency() =
        runTest {
            val harness =
                DataHarness(store = signedInStore()) {
                    json(
                        """[{"id":"r-1","nom":"Loyer","montant":"850.00","frequence":"mensuelle","jour_prelevement":5,"category_id":"c-3"},
                            {"id":"r-2","nom":"Assurance","montant":240,"frequence":"annuelle","jour_prelevement":1,"category_id":null}]""",
                    )
                }
            val charges = harness.recurring.list().getOrNull()!!
            assertEquals(listOf(RecurringChargeId("r-1"), RecurringChargeId("r-2")), charges.map { it.id })
            assertEquals(listOf(85_000L, 24_000L), charges.map { it.amountCents })
            assertEquals(listOf(ChargeFrequency.MONTHLY, ChargeFrequency.YEARLY), charges.map { it.frequency })
            assertFalse(charges.toString().contains("Loyer"))
            val request = harness.requests.single()
            assertEquals("/rest/v1/recurring_expenses", request.path)
            assertEquals("eq.$USER_ID", request.query("user_id"))
            assertEquals("created_at.asc", request.query("order"))
        }

    @Test
    fun deferredDebitIsReadFromTheProfile() =
        runTest {
            val harness =
                DataHarness(store = signedInStore()) {
                    json("""[{"debit_differe":true,"debit_jour_arrete":25,"debit_jour_prelevement":1,"debit_depuis":"2026-06-01"}]""")
                }
            assertEquals(AppResult.Success(DeferredDebit(true, 25, 1, LocalDate(2026, 6, 1))), harness.debits.current())
            val request = harness.requests.single()
            assertEquals("/rest/v1/profiles", request.path)
            assertEquals("debit_differe,debit_jour_arrete,debit_jour_prelevement,debit_depuis", request.query("select"))
            assertEquals("eq.$USER_ID", request.query("id"))
            val empty = DataHarness(store = signedInStore()) { json("[]") }
            assertEquals(AppResult.Success(DeferredDebit.Immediate), empty.debits.current())
        }
}
