package app.nemla.core.data.remote

import app.nemla.ai.api.CategoryReview
import app.nemla.ai.api.ReviewPeriodKind
import app.nemla.core.common.AppResult
import app.nemla.core.common.getOrNull
import app.nemla.core.common.map
import app.nemla.core.data.ai.toDomain
import app.nemla.core.domain.model.Currency
import app.nemla.core.domain.model.InboxChannel
import app.nemla.core.domain.model.InboxItem
import app.nemla.core.domain.model.InboxItemId
import app.nemla.core.domain.model.InboxKind
import app.nemla.core.domain.model.ReportCalendar
import app.nemla.core.domain.model.ReportSheet
import app.nemla.core.domain.port.InboxRepository
import app.nemla.core.domain.port.ProfileRepository
import app.nemla.core.network.safeRequest
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.patch
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.time.Clock
import app.nemla.ai.api.PeriodReview as AiPeriodReview

@Serializable
internal class InboxRowDto(
    val id: String? = null,
    val channel: String? = null,
    val kind: String? = null,
    val title: String? = null,
    val body: String? = null,
    val periode: String? = null,
    val payload: JsonElement? = null,
    @SerialName("delivered_at") val deliveredAt: String? = null,
) {
    override fun toString(): String = "InboxRowDto(id=$id, channel=$channel, kind=$kind)"
}

@Serializable
internal class ReportPayloadDto(
    val month: String? = null,
    val kind: String? = null,
    @SerialName("previous_month") val previousMonth: String? = null,
    val total: JsonElement? = null,
    @SerialName("previous_total") val previousTotal: JsonElement? = null,
    val budget: JsonElement? = null,
    @SerialName("expense_count") val expenseCount: Int = 0,
    val categories: List<ReportPayloadCategoryDto> = emptyList(),
    val highlights: List<String> = emptyList(),
    val advice: List<String> = emptyList(),
)

@Serializable
internal class ReportPayloadCategoryDto(
    val nom: String? = null,
    val montant: JsonElement? = null,
    val part: JsonElement? = null,
    val variation: JsonElement? = null,
)

internal class KtorInboxRepository(
    private val postgrest: Postgrest,
    private val profiles: ProfileRepository,
    private val clock: Clock,
) : InboxRepository {
    private val client get() = postgrest.client

    override suspend fun unseen(): AppResult<List<InboxItem>> =
        postgrest
            .withUser { userId ->
                safeRequest<List<InboxRowDto>> {
                    client.get(postgrest.table(TABLE)) {
                        parameter("select", "id,channel,kind,title,body,periode,payload,delivered_at")
                        parameter("user_id", eq(userId))
                        parameter("seen_at", "is.null")
                        parameter("order", "created_at.desc")
                    }
                }
            }.let { result ->
                when (result) {
                    is AppResult.Failure -> result
                    is AppResult.Success -> AppResult.Success(toItems(result.value))
                }
            }

    override suspend fun markSeen(id: InboxItemId): AppResult<Unit> = patch(id, buildJsonObject { put("seen_at", clock.now().toString()) })

    override suspend fun markDelivered(id: InboxItemId): AppResult<Unit> {
        val stamp = clock.now().toString()
        return patch(
            id,
            buildJsonObject {
                put("delivered_at", stamp)
                put("seen_at", stamp)
            },
        )
    }

    private suspend fun patch(
        id: InboxItemId,
        body: JsonObject,
    ): AppResult<Unit> =
        postgrest.withUser { userId ->
            safeRequest<Unit> {
                client.patch(postgrest.table(TABLE)) {
                    parameter("id", eq(id.value))
                    parameter("user_id", eq(userId))
                    jsonBody(body)
                }
            }
        }

    private suspend fun toItems(rows: List<InboxRowDto>): List<InboxItem> {
        val needsCurrency = rows.any { it.payload is JsonObject }
        val currency = if (needsCurrency) profiles.getProfile().getOrNull()?.currency ?: Currency.Default else Currency.Default
        return rows.mapNotNull { it.toItem(currency) }
    }

    private fun InboxRowDto.toItem(currency: Currency): InboxItem? {
        val id = id?.takeIf { it.isNotBlank() } ?: return null
        val channel = channelOf(channel) ?: return null
        val kind = kindOf(kind) ?: return null
        val period = periode?.let(ReportCalendar::parseKey)
        val report = if (kind == InboxKind.DAILY_REMINDER) null else reportOf(payload, period?.let(ReportCalendar::key), kind, currency)
        return InboxItem(
            id = InboxItemId(id),
            channel = channel,
            kind = kind,
            title = title.orEmpty(),
            body = body.orEmpty(),
            period = period,
            report = report,
            delivered = deliveredAt != null,
        )
    }

    private fun reportOf(
        payload: JsonElement?,
        periodKey: String?,
        kind: InboxKind,
        currency: Currency,
    ): ReportSheet? {
        val content = payload as? JsonObject ?: return null
        return try {
            val dto = json.decodeFromJsonElement(ReportPayloadDto.serializer(), content)
            dto.toAiReview(periodKey, kind)?.toDomain()?.let { ReportSheet(it, currency) }
        } catch (e: SerializationException) {
            null
        } catch (e: IllegalArgumentException) {
            null
        }
    }

    private fun ReportPayloadDto.toAiReview(
        periodKey: String?,
        kind: InboxKind,
    ): AiPeriodReview? {
        val reviewKind =
            when (this.kind) {
                "annee" -> ReviewPeriodKind.YEAR
                "mois" -> ReviewPeriodKind.MONTH
                else -> if (kind == InboxKind.YEAR_REPORT) ReviewPeriodKind.YEAR else ReviewPeriodKind.MONTH
            }
        val period = month?.trim() ?: periodKey ?: return null
        return AiPeriodReview(
            period = period,
            kind = reviewKind,
            previousPeriod = previousMonth?.trim() ?: return null,
            totalCents = total.toCents() ?: return null,
            previousTotalCents = previousTotal.toCents() ?: 0,
            budgetCents = budget.toCents() ?: 0,
            expenseCount = expenseCount,
            categories =
                categories.map { category ->
                    CategoryReview(
                        name = category.nom ?: return null,
                        amountCents = category.montant.toCents() ?: return null,
                        share = category.part.toDecimal() ?: 0.0,
                        variation = category.variation.toDecimal() ?: 0.0,
                    )
                },
            highlights = highlights.map(String::trim).filter(String::isNotEmpty),
            advice = advice.map(String::trim).filter(String::isNotEmpty),
        )
    }

    private companion object {
        const val TABLE = "user_notifications"
        val json = Json { ignoreUnknownKeys = true }

        fun channelOf(value: String?): InboxChannel? =
            when (value) {
                "in_app" -> InboxChannel.IN_APP
                "push" -> InboxChannel.PUSH
                else -> null
            }

        fun kindOf(value: String?): InboxKind? =
            when (value) {
                "bilan_mois" -> InboxKind.MONTH_REPORT
                "bilan_annee" -> InboxKind.YEAR_REPORT
                "rappel_jour" -> InboxKind.DAILY_REMINDER
                else -> null
            }
    }
}

private fun JsonElement?.toDecimal(): Double? =
    (this as? JsonPrimitive)
        ?.takeUnless { it is JsonNull }
        ?.content
        ?.trim()
        ?.toDoubleOrNull()
