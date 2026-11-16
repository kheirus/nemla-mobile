package app.nemla.core.data.remote

import app.nemla.core.common.AppResult
import app.nemla.core.common.flatMap
import app.nemla.core.domain.model.DeferredDebit
import app.nemla.core.domain.port.DeferredDebitRepository
import app.nemla.core.network.safeRequest
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import kotlinx.datetime.LocalDate
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
internal class DeferredDebitDto(
    @SerialName("debit_differe") val enabled: Boolean = false,
    @SerialName("debit_jour_arrete") val cutoffDay: Int? = null,
    @SerialName("debit_jour_prelevement") val debitDay: Int? = null,
    @SerialName("debit_depuis") val since: String? = null,
) {
    fun toDomain(): DeferredDebit =
        DeferredDebit(
            enabled = enabled,
            cutoffDay = cutoffDay ?: DeferredDebit.DEFAULT_CUTOFF_DAY,
            debitDay = debitDay ?: DeferredDebit.DEFAULT_DEBIT_DAY,
            since = since?.let(LocalDate::parseOrNullSafe),
        )
}

private const val DEBIT_COLUMNS = "debit_differe,debit_jour_arrete,debit_jour_prelevement,debit_depuis"

internal class KtorDeferredDebitRepository(
    private val postgrest: Postgrest,
) : DeferredDebitRepository {
    private val client get() = postgrest.client

    override suspend fun current(): AppResult<DeferredDebit> =
        postgrest.withUser { userId ->
            safeRequest<List<DeferredDebitDto>> {
                client.get(postgrest.table("profiles")) {
                    parameter("select", DEBIT_COLUMNS)
                    parameter("id", eq(userId))
                }
            }.flatMap { rows -> AppResult.Success(rows.firstOrNull()?.toDomain() ?: DeferredDebit.Immediate) }
        }
}
