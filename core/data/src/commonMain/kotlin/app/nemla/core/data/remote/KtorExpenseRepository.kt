package app.nemla.core.data.remote

import app.nemla.core.common.AppError
import app.nemla.core.common.AppResult
import app.nemla.core.common.flatMap
import app.nemla.core.domain.model.CategoryId
import app.nemla.core.domain.model.Expense
import app.nemla.core.domain.model.ExpenseEdit
import app.nemla.core.domain.model.ExpenseId
import app.nemla.core.domain.model.ExpenseQuery
import app.nemla.core.domain.model.ExpenseSource
import app.nemla.core.domain.model.PaymentMethod
import app.nemla.core.domain.model.RecurringChargeId
import app.nemla.core.domain.model.VoiceEntryId
import app.nemla.core.domain.port.ExpenseRepository
import app.nemla.core.network.safeRequest
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.patch
import kotlinx.datetime.LocalDate
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.time.Instant

@Serializable
internal class ExpenseDto(
    val id: String,
    @SerialName("category_id") val categoryId: String? = null,
    @SerialName("voice_entry_id") val voiceEntryId: String? = null,
    @SerialName("recurring_expense_id") val recurringExpenseId: String? = null,
    val montant: JsonElement? = null,
    val date: String,
    @SerialName("date_prelevement") val debitDate: String? = null,
    val source: String? = null,
    @SerialName("moyen_paiement") val paymentMethod: String? = null,
    val confirmed: Boolean = true,
    @SerialName("created_at") val createdAt: String? = null,
) {
    override fun toString(): String = "ExpenseDto(id=$id)"

    fun toDomain(): Expense? {
        val purchase = LocalDate.parseOrNullSafe(date) ?: return null
        return Expense(
            id = ExpenseId(id),
            categoryId = categoryId?.let(::CategoryId),
            voiceEntryId = voiceEntryId?.let(::VoiceEntryId),
            recurringChargeId = recurringExpenseId?.let(::RecurringChargeId),
            amountCents = montant.toCents() ?: 0,
            date = purchase,
            debitDate = debitDate?.let(LocalDate::parseOrNullSafe) ?: purchase,
            source = if (source == SOURCE_VOICE) ExpenseSource.VOICE else ExpenseSource.MANUAL,
            paymentMethod = paymentMethodFromBackend(paymentMethod),
            confirmed = confirmed,
            createdAt = createdAt?.let(::parseInstantOrNull),
            label = null,
        )
    }
}

internal const val SOURCE_VOICE = "voice"
internal const val EXPENSE_COLUMNS =
    "id,category_id,voice_entry_id,recurring_expense_id,montant,date,date_prelevement,source,moyen_paiement,confirmed,created_at"
internal const val EXPENSE_PAGE_SIZE = 1_000
private const val MAX_EXPENSE_PAGES = 20

internal fun paymentMethodFromBackend(value: String?): PaymentMethod =
    when (value) {
        "virement" -> PaymentMethod.TRANSFER
        "especes" -> PaymentMethod.CASH
        "cheque" -> PaymentMethod.CHEQUE
        else -> PaymentMethod.CARD
    }

internal fun PaymentMethod.toBackend(): String =
    when (this) {
        PaymentMethod.CARD -> "carte"
        PaymentMethod.TRANSFER -> "virement"
        PaymentMethod.CASH -> "especes"
        PaymentMethod.CHEQUE -> "cheque"
    }

internal fun LocalDate.Companion.parseOrNullSafe(value: String): LocalDate? =
    try {
        parse(value.take(ISO_DATE_LENGTH))
    } catch (_: IllegalArgumentException) {
        null
    }

internal fun parseInstantOrNull(value: String): Instant? =
    try {
        Instant.parse(value)
    } catch (_: IllegalArgumentException) {
        null
    }

private const val ISO_DATE_LENGTH = 10

internal class KtorExpenseRepository(
    private val postgrest: Postgrest,
) : ExpenseRepository {
    private val client get() = postgrest.client

    override suspend fun list(query: ExpenseQuery): AppResult<List<Expense>> =
        postgrest.withUser { userId ->
            val requested = query.limit
            val collected = mutableListOf<ExpenseDto>()
            var page = 0
            while (page < MAX_EXPENSE_PAGES) {
                val remaining = requested?.minus(collected.size)
                if (remaining != null && remaining <= 0) break
                val size = minOf(remaining ?: EXPENSE_PAGE_SIZE, EXPENSE_PAGE_SIZE)
                val offset = collected.size
                val result =
                    safeRequest<List<ExpenseDto>> {
                        client.get(postgrest.table("expenses")) {
                            parameter("select", EXPENSE_COLUMNS)
                            parameter("user_id", eq(userId))
                            query.dateFrom?.let { parameter("date", "gte.$it") }
                            query.dateTo?.let { parameter("date", "lte.$it") }
                            query.dateEquals?.let { parameter("date", eq(it.toString())) }
                            query.confirmed?.let { parameter("confirmed", "is.$it") }
                            parameter("order", "date.desc,created_at.desc,id.desc")
                            parameter("limit", size)
                            if (offset > 0) parameter("offset", offset)
                        }
                    }
                when (result) {
                    is AppResult.Failure -> {
                        return@withUser result
                    }

                    is AppResult.Success -> {
                        collected += result.value
                        if (result.value.size < size) break
                    }
                }
                page++
            }
            AppResult.Success(collected.mapNotNull(ExpenseDto::toDomain))
        }

    override suspend fun searchByLabel(
        term: String,
        limit: Int,
    ): AppResult<List<Expense>> = AppResult.Failure(AppError.NotSupportedYet)

    override suspend fun update(
        id: ExpenseId,
        edit: ExpenseEdit,
    ): AppResult<Unit> =
        postgrest.withUser { userId ->
            safeRequest<List<RowIdDto>> {
                client.patch(postgrest.table("expenses")) {
                    parameter("id", eq(id.value))
                    parameter("user_id", eq(userId))
                    parameter("select", "id")
                    returnRepresentation()
                    jsonBody(
                        buildJsonObject {
                            put("montant", centsToJson(edit.amountCents))
                            put("date", edit.date.toString())
                            put("category_id", edit.categoryId?.value)
                            put("moyen_paiement", edit.paymentMethod.toBackend())
                        },
                    )
                }
            }.flatMap { rows -> if (rows.isEmpty()) AppResult.Failure(AppError.NotFound) else AppResult.Success(Unit) }
        }

    override suspend fun delete(id: ExpenseId): AppResult<Unit> =
        postgrest.withUser { userId ->
            safeRequest<Unit> {
                client.delete(postgrest.table("expenses")) {
                    parameter("id", eq(id.value))
                    parameter("user_id", eq(userId))
                }
            }
        }
}
