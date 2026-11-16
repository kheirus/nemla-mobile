package app.nemla.core.data.remote

import app.nemla.core.common.AppError
import app.nemla.core.common.AppResult
import app.nemla.core.common.map
import app.nemla.core.domain.model.CategoryId
import app.nemla.core.domain.model.ChargeFrequency
import app.nemla.core.domain.model.RecurringCharge
import app.nemla.core.domain.model.RecurringChargeDraft
import app.nemla.core.domain.model.RecurringChargeId
import app.nemla.core.domain.port.RecurringChargeMaterializer
import app.nemla.core.domain.port.RecurringChargeRepository
import app.nemla.core.network.safeRequest
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

@Serializable
internal class RecurringChargeDto(
    val id: String,
    val nom: String = "",
    val montant: JsonElement? = null,
    val frequence: String? = null,
    @SerialName("jour_prelevement") val dayOfMonth: Int = 1,
    @SerialName("category_id") val categoryId: String? = null,
) {
    override fun toString(): String = "RecurringChargeDto(id=$id)"

    fun toDomain(): RecurringCharge =
        RecurringCharge(
            id = RecurringChargeId(id),
            name = nom,
            amountCents = montant.toCents() ?: 0,
            frequency = if (frequence == FREQUENCY_YEARLY) ChargeFrequency.YEARLY else ChargeFrequency.MONTHLY,
            dayOfMonth = dayOfMonth,
            categoryId = categoryId?.let(::CategoryId),
        )
}

internal const val FREQUENCY_YEARLY = "annuelle"
internal const val FREQUENCY_MONTHLY = "mensuelle"
private const val RECURRING_COLUMNS = "id,nom,montant,frequence,jour_prelevement,category_id"

internal class KtorRecurringChargeRepository(
    private val postgrest: Postgrest,
) : RecurringChargeRepository {
    private val client get() = postgrest.client

    override suspend fun list(): AppResult<List<RecurringCharge>> =
        postgrest.withUser { userId ->
            safeRequest<List<RecurringChargeDto>> {
                client.get(postgrest.table("recurring_expenses")) {
                    parameter("select", RECURRING_COLUMNS)
                    parameter("user_id", eq(userId))
                    parameter("order", "created_at.asc")
                }
            }.map { rows -> rows.map(RecurringChargeDto::toDomain) }
        }

    override suspend fun addAll(drafts: List<RecurringChargeDraft>): AppResult<Unit> {
        if (drafts.isEmpty()) return AppResult.Success(Unit)
        return postgrest.withUser { userId ->
            safeRequest<Unit> {
                client.post(postgrest.table("recurring_expenses")) {
                    jsonBody(
                        JsonArray(
                            drafts.map { draft ->
                                buildJsonObject {
                                    put("user_id", userId)
                                    put("category_id", draft.categoryId?.value)
                                    put("nom", draft.name)
                                    put("montant", centsToJson(draft.amountCents))
                                    put("frequence", if (draft.frequency == ChargeFrequency.YEARLY) FREQUENCY_YEARLY else FREQUENCY_MONTHLY)
                                    put("jour_prelevement", draft.dayOfMonth)
                                }
                            },
                        ),
                    )
                }
            }
        }
    }

    override suspend fun delete(id: RecurringChargeId): AppResult<Unit> =
        postgrest.withUser { userId ->
            safeRequest<Unit> {
                client.delete(postgrest.table("recurring_expenses")) {
                    parameter("id", eq(id.value))
                    parameter("user_id", eq(userId))
                }
            }
        }
}

internal object UnsupportedRecurringChargeMaterializer : RecurringChargeMaterializer {
    override suspend fun materialize(year: Int): AppResult<Int> = AppResult.Failure(AppError.NotSupportedYet)
}
