package app.nemla.core.data.remote

import app.nemla.core.common.AppResult
import app.nemla.core.common.flatMap
import app.nemla.core.domain.model.DebitSettings
import app.nemla.core.domain.model.DeferredDebit
import app.nemla.core.domain.port.DeferredDebitRepository
import app.nemla.core.network.safeRequest
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.patch
import io.ktor.client.request.post
import kotlinx.datetime.LocalDate
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.put

@Serializable
internal class DeferredDebitDto(
    @SerialName("debit_differe") val enabled: Boolean = false,
    @SerialName("debit_jour_arrete") val cutoffDay: Int? = null,
    @SerialName("debit_jour_prelevement") val debitDay: Int? = null,
    @SerialName("debit_depuis") val since: String? = null,
    val banque: String? = null,
    @SerialName("debit_choix_fait") val choiceMade: Boolean = false,
) {
    fun toDomain(): DeferredDebit =
        DeferredDebit(
            enabled = enabled,
            cutoffDay = cutoffDay ?: DeferredDebit.DEFAULT_CUTOFF_DAY,
            debitDay = debitDay ?: DeferredDebit.DEFAULT_DEBIT_DAY,
            since = since?.let(LocalDate::parseOrNullSafe),
        )

    fun toSettings(): DebitSettings = DebitSettings(bankId = banque, rule = toDomain(), choiceMade = choiceMade)
}

private const val DEBIT_COLUMNS = "debit_differe,debit_jour_arrete,debit_jour_prelevement,debit_depuis"
private const val SETTINGS_COLUMNS = "$DEBIT_COLUMNS,banque,debit_choix_fait"

internal class KtorDeferredDebitRepository(
    private val postgrest: Postgrest,
) : DeferredDebitRepository {
    private val client get() = postgrest.client

    override suspend fun current(): AppResult<DeferredDebit> = read(DEBIT_COLUMNS) { it?.toDomain() ?: DeferredDebit.Immediate }

    override suspend fun settings(): AppResult<DebitSettings> =
        read(SETTINGS_COLUMNS) { it?.toSettings() ?: DebitSettings(null, DeferredDebit.Immediate, choiceMade = false) }

    override suspend fun save(
        bankId: String?,
        rule: DeferredDebit,
    ): AppResult<Int> =
        postgrest.withUser { userId ->
            safeRequest<Unit> {
                client.patch(postgrest.table("profiles")) {
                    parameter("id", eq(userId))
                    jsonBody(
                        buildJsonObject {
                            put("banque", bankId)
                            put("debit_differe", rule.enabled)
                            put("debit_jour_arrete", rule.cutoffDay)
                            put("debit_jour_prelevement", rule.debitDay)
                            put("debit_depuis", rule.since?.toString())
                            put("debit_choix_fait", true)
                        },
                    )
                }
            }.flatMap {
                safeRequest<JsonElement> {
                    client.post(postgrest.rpc("apply_debit_rule")) { jsonBody(JsonObject(emptyMap())) }
                }.flatMap { moved -> AppResult.Success((moved as? JsonPrimitive)?.intOrNull ?: 0) }
            }
        }

    private suspend fun <T> read(
        columns: String,
        transform: (DeferredDebitDto?) -> T,
    ): AppResult<T> =
        postgrest.withUser { userId ->
            safeRequest<List<DeferredDebitDto>> {
                client.get(postgrest.table("profiles")) {
                    parameter("select", columns)
                    parameter("id", eq(userId))
                }
            }.flatMap { rows -> AppResult.Success(transform(rows.firstOrNull())) }
        }
}
