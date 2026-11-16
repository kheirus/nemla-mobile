package app.nemla.core.domain

import app.nemla.core.common.AppError
import app.nemla.core.common.AppResult
import app.nemla.core.domain.model.CategoryId
import app.nemla.core.domain.model.DebitSettings
import app.nemla.core.domain.model.DeferredDebit
import app.nemla.core.domain.model.Expense
import app.nemla.core.domain.model.ExpenseEdit
import app.nemla.core.domain.model.ExpenseId
import app.nemla.core.domain.model.ExpenseQuery
import app.nemla.core.domain.model.ExpenseSource
import app.nemla.core.domain.model.PaymentMethod
import app.nemla.core.domain.model.ReviewOrigin
import app.nemla.core.domain.model.VoiceEntryId
import app.nemla.core.domain.port.DeferredDebitRepository
import app.nemla.core.domain.port.ExpenseRepository
import app.nemla.core.domain.port.RecurringChargeMaterializer
import app.nemla.core.domain.usecase.GetDeferredDebit
import app.nemla.core.domain.usecase.MaterializeRecurringCharges
import app.nemla.core.domain.usecase.PendingReviews
import app.nemla.core.domain.usecase.SearchExpensesByDate
import app.nemla.core.domain.usecase.SearchExpensesByLabel
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.time.Instant

class DashboardUseCasesTest {
    private fun expense(
        id: String,
        voice: String?,
        created: Long,
        confirmed: Boolean = false,
    ) = Expense(
        id = ExpenseId(id),
        categoryId = CategoryId("c-1"),
        voiceEntryId = voice?.let(::VoiceEntryId),
        recurringChargeId = null,
        amountCents = 1_000,
        date = LocalDate(2026, 11, 16),
        debitDate = LocalDate(2026, 11, 16),
        source = ExpenseSource.VOICE,
        paymentMethod = PaymentMethod.CARD,
        confirmed = confirmed,
        createdAt = Instant.fromEpochSeconds(created),
    )

    @Test
    fun pendingExpensesAreGroupedByDictationAndImportsFormOneGroup() {
        val groups =
            PendingReviews.group(
                listOf(
                    expense("a2", "v-1", 20),
                    expense("i1", null, 5),
                    expense("a1", "v-1", 10),
                    expense("b1", "v-2", 30),
                    expense("i2", null, 1),
                    expense("ok", "v-1", 1, confirmed = true),
                ),
            )
        assertEquals(listOf(ReviewOrigin.VOICE, ReviewOrigin.IMPORT, ReviewOrigin.VOICE), groups.map { it.origin })
        assertEquals(listOf("a1", "a2"), groups[0].drafts.map { it.id?.value })
        assertEquals(VoiceEntryId("v-1"), groups[0].voiceEntryId)
        assertEquals(listOf("i2", "i1"), groups[1].drafts.map { it.id?.value })
        assertEquals(null, groups[1].voiceEntryId)
        assertEquals("", groups[2].drafts.single().label)
        assertFalse(groups.toString().contains("1000"))
    }

    @Test
    fun dateSearchAsksForConfirmedExpensesOfThatDayOnly() =
        runTest {
            val repository = RecordingExpenses()
            SearchExpensesByDate(repository)(LocalDate(2026, 11, 2))
            assertEquals(ExpenseQuery(dateEquals = LocalDate(2026, 11, 2), confirmed = true, limit = 50), repository.queries.single())
            assertEquals(AppError.NotSupportedYet, (SearchExpensesByLabel(repository)("  café ") as AppResult.Failure).error)
            assertEquals("café", repository.terms.single())
        }

    @Test
    fun materializationRunsOncePerYearAndIgnoresFailures() =
        runTest {
            val years = mutableListOf<Int>()
            val materialize =
                MaterializeRecurringCharges(
                    object : RecurringChargeMaterializer {
                        override suspend fun materialize(year: Int): AppResult<Int> {
                            years += year
                            return AppResult.Failure(AppError.NotSupportedYet)
                        }
                    },
                )
            materialize(2026)
            materialize(2026)
            materialize(2025)
            assertEquals(listOf(2026, 2025), years)
        }

    @Test
    fun deferredDebitFallsBackToImmediate() =
        runTest {
            assertEquals(DeferredDebit.Immediate, GetDeferredDebit(FixedDebits(AppResult.Failure(AppError.Network.Offline)))())
            val rule = DeferredDebit(true, 25, 1, null)
            assertEquals(rule, GetDeferredDebit(FixedDebits(AppResult.Success(rule)))())
        }
}

private class RecordingExpenses : ExpenseRepository {
    val queries = mutableListOf<ExpenseQuery>()
    val terms = mutableListOf<String>()

    override suspend fun list(query: ExpenseQuery): AppResult<List<Expense>> {
        queries += query
        return AppResult.Success(emptyList())
    }

    override suspend fun searchByLabel(
        term: String,
        limit: Int,
    ): AppResult<List<Expense>> {
        terms += term
        return AppResult.Failure(AppError.NotSupportedYet)
    }

    override suspend fun update(
        id: ExpenseId,
        edit: ExpenseEdit,
    ): AppResult<Unit> = AppResult.Success(Unit)

    override suspend fun delete(id: ExpenseId): AppResult<Unit> = AppResult.Success(Unit)
}

private class FixedDebits(
    private val result: AppResult<DeferredDebit>,
) : DeferredDebitRepository {
    override suspend fun current(): AppResult<DeferredDebit> = result

    override suspend fun settings(): AppResult<DebitSettings> = AppResult.Failure(AppError.NotSupportedYet)

    override suspend fun save(
        bankId: String?,
        rule: DeferredDebit,
    ): AppResult<Int> = AppResult.Failure(AppError.NotSupportedYet)
}
