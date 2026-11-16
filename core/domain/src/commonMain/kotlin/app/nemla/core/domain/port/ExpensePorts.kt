package app.nemla.core.domain.port

import app.nemla.core.common.AppResult
import app.nemla.core.domain.model.DebitSettings
import app.nemla.core.domain.model.DeferredDebit
import app.nemla.core.domain.model.Expense
import app.nemla.core.domain.model.ExpenseEdit
import app.nemla.core.domain.model.ExpenseId
import app.nemla.core.domain.model.ExpenseQuery
import app.nemla.core.domain.model.PendingReview
import app.nemla.core.domain.model.RecurringCharge
import app.nemla.core.domain.model.RecurringChargeDraft
import app.nemla.core.domain.model.RecurringChargeId

interface ExpenseRepository {
    suspend fun list(query: ExpenseQuery): AppResult<List<Expense>>

    suspend fun searchByLabel(
        term: String,
        limit: Int,
    ): AppResult<List<Expense>>

    suspend fun update(
        id: ExpenseId,
        edit: ExpenseEdit,
    ): AppResult<Unit>

    suspend fun delete(id: ExpenseId): AppResult<Unit>
}

interface RecurringChargeRepository {
    suspend fun list(): AppResult<List<RecurringCharge>>

    suspend fun addAll(drafts: List<RecurringChargeDraft>): AppResult<Unit>

    suspend fun delete(id: RecurringChargeId): AppResult<Unit>
}

interface RecurringChargeMaterializer {
    suspend fun materialize(year: Int): AppResult<Int>
}

interface DeferredDebitRepository {
    suspend fun current(): AppResult<DeferredDebit>

    suspend fun settings(): AppResult<DebitSettings>

    suspend fun save(
        bankId: String?,
        rule: DeferredDebit,
    ): AppResult<Int>
}

fun interface PendingReviewOpener {
    fun open(review: PendingReview)
}
