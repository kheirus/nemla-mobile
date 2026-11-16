package app.nemla.core.domain.usecase

import app.nemla.core.common.AppResult
import app.nemla.core.domain.model.DeferredDebit
import app.nemla.core.domain.model.Expense
import app.nemla.core.domain.model.ExpenseDraft
import app.nemla.core.domain.model.ExpenseEdit
import app.nemla.core.domain.model.ExpenseId
import app.nemla.core.domain.model.ExpenseQuery
import app.nemla.core.domain.model.PendingReview
import app.nemla.core.domain.model.RecurringCharge
import app.nemla.core.domain.model.ReviewOrigin
import app.nemla.core.domain.model.UserProfile
import app.nemla.core.domain.port.DeferredDebitRepository
import app.nemla.core.domain.port.ExpenseRepository
import app.nemla.core.domain.port.ProfileRepository
import app.nemla.core.domain.port.RecurringChargeMaterializer
import app.nemla.core.domain.port.RecurringChargeRepository
import kotlinx.datetime.LocalDate

class BootstrapAndLoadProfile(
    private val profiles: ProfileRepository,
) {
    private var bootstrapped = false

    suspend operator fun invoke(): AppResult<UserProfile> {
        if (!bootstrapped) {
            if (profiles.bootstrap() is AppResult.Success) bootstrapped = true
        }
        return profiles.getProfile()
    }
}

class ListExpenses(
    private val expenses: ExpenseRepository,
) {
    suspend operator fun invoke(query: ExpenseQuery): AppResult<List<Expense>> = expenses.list(query)
}

class SearchExpensesByDate(
    private val expenses: ExpenseRepository,
) {
    suspend operator fun invoke(date: LocalDate): AppResult<List<Expense>> =
        expenses.list(ExpenseQuery(dateEquals = date, confirmed = true, limit = SEARCH_LIMIT))

    companion object {
        const val SEARCH_LIMIT = 50
    }
}

class SearchExpensesByLabel(
    private val expenses: ExpenseRepository,
) {
    suspend operator fun invoke(term: String): AppResult<List<Expense>> =
        expenses.searchByLabel(term.trim(), SearchExpensesByDate.SEARCH_LIMIT)
}

class UpdateExpense(
    private val expenses: ExpenseRepository,
) {
    suspend operator fun invoke(
        id: ExpenseId,
        edit: ExpenseEdit,
    ): AppResult<Unit> = expenses.update(id, edit)
}

class DeleteExpense(
    private val expenses: ExpenseRepository,
) {
    suspend operator fun invoke(id: ExpenseId): AppResult<Unit> = expenses.delete(id)
}

class ListRecurringCharges(
    private val charges: RecurringChargeRepository,
) {
    suspend operator fun invoke(): AppResult<List<RecurringCharge>> = charges.list()
}

class MaterializeRecurringCharges(
    private val materializer: RecurringChargeMaterializer,
) {
    private val done = mutableSetOf<Int>()

    suspend operator fun invoke(year: Int) {
        if (year in done) return
        done += year
        materializer.materialize(year)
    }
}

class GetDeferredDebit(
    private val debits: DeferredDebitRepository,
) {
    suspend operator fun invoke(): DeferredDebit =
        when (val result = debits.current()) {
            is AppResult.Success -> result.value
            is AppResult.Failure -> DeferredDebit.Immediate
        }
}

object PendingReviews {
    fun group(pending: List<Expense>): List<PendingReview> {
        val unconfirmed = pending.filterNot { it.confirmed }
        val groups = LinkedHashMap<String?, MutableList<Expense>>()
        unconfirmed.forEach { expense -> groups.getOrPut(expense.voiceEntryId?.value) { mutableListOf() } += expense }
        return groups.map { (key, expenses) ->
            val ordered = expenses.sortedWith(compareBy(nullsLast()) { it.createdAt })
            PendingReview(
                origin = if (key == null) ReviewOrigin.IMPORT else ReviewOrigin.VOICE,
                voiceEntryId = ordered.first().voiceEntryId,
                drafts = ordered.map { it.toDraft() },
            )
        }
    }

    private fun Expense.toDraft(): ExpenseDraft =
        ExpenseDraft(
            id = id,
            amountCents = amountCents,
            label = label.orEmpty(),
            date = date,
            categoryId = categoryId,
            categorySource = null,
        )
}
