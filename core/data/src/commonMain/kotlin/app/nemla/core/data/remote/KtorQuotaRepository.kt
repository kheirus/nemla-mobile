package app.nemla.core.data.remote

import app.nemla.core.common.AppError
import app.nemla.core.common.AppResult
import app.nemla.core.common.flatMap
import app.nemla.core.domain.model.MonthlyQuota
import app.nemla.core.domain.port.QuotaRepository
import app.nemla.core.network.safeRequest
import io.ktor.client.request.post
import kotlinx.datetime.YearMonth
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

@Serializable
internal class QuotaDto(
    val month: String,
    @SerialName("voice_count") val voiceCount: Int = 0,
    @SerialName("voice_limit") val voiceLimit: Int = 0,
    @SerialName("import_count") val importCount: Int = 0,
    @SerialName("import_limit") val importLimit: Int = 0,
) {
    fun toDomain(): MonthlyQuota? {
        val parsed =
            try {
                YearMonth.parse(month.trim())
            } catch (e: IllegalArgumentException) {
                return null
            }
        return MonthlyQuota(parsed, voiceCount, voiceLimit, importCount, importLimit)
    }
}

internal class KtorQuotaRepository(
    private val postgrest: Postgrest,
) : QuotaRepository {
    override suspend fun currentMonth(): AppResult<MonthlyQuota> =
        safeRequest<JsonElement> {
            postgrest.client.post(postgrest.rpc("ensure_month_quota")) { jsonBody(JsonObject(emptyMap())) }
        }.flatMap { element ->
            val row = if (element is JsonArray) element.firstOrNull() else element
            val quota =
                (row as? JsonObject)?.let {
                    try {
                        quotaJson.decodeFromJsonElement(QuotaDto.serializer(), it).toDomain()
                    } catch (e: SerializationException) {
                        null
                    } catch (e: IllegalArgumentException) {
                        null
                    }
                }
            quota?.let { AppResult.Success(it) } ?: AppResult.Failure(AppError.Server(null))
        }

    private companion object {
        val quotaJson = Json { ignoreUnknownKeys = true }
    }
}
