package app.nemla.core.data.remote

import app.nemla.core.common.AppError
import app.nemla.core.common.AppResult
import app.nemla.core.common.flatMap
import app.nemla.core.common.map
import app.nemla.core.domain.model.CategoryOutcome
import app.nemla.core.domain.model.ClassificationCorrection
import app.nemla.core.domain.model.ExpenseId
import app.nemla.core.domain.model.MerchantKeys
import app.nemla.core.domain.model.ReviewedExpense
import app.nemla.core.domain.model.VoiceEntryId
import app.nemla.core.domain.port.ExpenseReviewRepository
import app.nemla.core.network.safeRequest
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.patch
import io.ktor.client.request.post
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

@Serializable
internal class MerchantRuleKeyDto(
    @SerialName("merchant_key") val merchantKey: String,
)

@Serializable
internal class RowIdDto(
    val id: String,
)

internal const val VOICE_STATUS_CONFIRMED = "confirmed"
private const val MAX_LEARNED_KEYS = 100

internal class KtorExpenseReviewRepository(
    private val postgrest: Postgrest,
) : ExpenseReviewRepository {
    private val client get() = postgrest.client

    override suspend fun learnedMerchantKeys(keys: Set<String>): AppResult<Set<String>> {
        val usable = keys.filter { MerchantKeys.isUsable(it) && it.all(::isMerchantKeyChar) }.take(MAX_LEARNED_KEYS)
        if (usable.isEmpty()) return AppResult.Success(emptySet())
        return postgrest.withUser { userId ->
            safeRequest<List<MerchantRuleKeyDto>> {
                client.get(postgrest.table("merchant_rules")) {
                    parameter("select", "merchant_key")
                    parameter("user_id", eq(userId))
                    parameter("merchant_key", inList(usable))
                }
            }.map { rows -> rows.map { it.merchantKey }.toSet() }
        }
    }

    override suspend fun confirm(expense: ReviewedExpense): AppResult<Unit> =
        postgrest.withUser { userId ->
            safeRequest<List<RowIdDto>> {
                client.patch(postgrest.table("expenses")) {
                    parameter("id", eq(expense.id.value))
                    parameter("user_id", eq(userId))
                    parameter("select", "id")
                    returnRepresentation()
                    jsonBody(
                        buildJsonObject {
                            put("montant", centsToJson(expense.amountCents))
                            put("date", expense.date.toString())
                            put("category_id", expense.categoryId?.value)
                            put("confirmed", true)
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

    override suspend fun markVoiceEntryConfirmed(id: VoiceEntryId): AppResult<Unit> =
        postgrest.withUser { userId ->
            safeRequest<Unit> {
                client.patch(postgrest.table("voice_entries")) {
                    parameter("id", eq(id.value))
                    parameter("user_id", eq(userId))
                    jsonBody(buildJsonObject { put("statut", VOICE_STATUS_CONFIRMED) })
                }
            }
        }

    override suspend fun recordCategoryOutcome(outcome: CategoryOutcome): AppResult<Unit> {
        if (!MerchantKeys.isUsable(outcome.merchantKey)) return AppResult.Success(Unit)
        return safeRequest<Unit> {
            client.post(postgrest.rpc("record_category_correction")) {
                jsonBody(
                    buildJsonObject {
                        put("p_merchant_key", outcome.merchantKey)
                        put("p_label", "")
                        put("p_category_id", outcome.categoryId?.value)
                        put("p_suggested_category", outcome.suggestedCategoryName)
                        put("p_chosen_category", outcome.chosenCategoryName)
                        put("p_was_correction", outcome.wasCorrection)
                        put("p_expense_id", outcome.expenseId.value)
                    },
                )
            }
        }
    }

    override suspend fun logCorrection(correction: ClassificationCorrection): AppResult<Unit> = AppResult.Failure(AppError.NotSupportedYet)
}

private fun isMerchantKeyChar(char: Char): Boolean = char in 'a'..'z' || char in '0'..'9' || char == ' '

internal fun inList(values: Collection<String>): String = values.joinToString(separator = ",", prefix = "in.(", postfix = ")") { "\"$it\"" }
