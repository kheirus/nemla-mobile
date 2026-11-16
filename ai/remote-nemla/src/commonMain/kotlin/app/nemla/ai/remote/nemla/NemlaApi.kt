package app.nemla.ai.remote.nemla

import app.nemla.core.common.AppError
import app.nemla.core.common.AppResult
import app.nemla.core.network.BackendConfig
import app.nemla.core.network.safeRequest
import app.nemla.core.network.toAppError
import io.ktor.client.HttpClient
import io.ktor.client.request.forms.formData
import io.ktor.client.request.forms.submitFormWithBinaryData
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import kotlinx.serialization.json.JsonObject
import kotlin.coroutines.cancellation.CancellationException

internal class NemlaApi(
    private val client: HttpClient,
    private val config: BackendConfig,
) {
    suspend fun processVoice(
        wav: ByteArray,
        lang: String,
    ): AppResult<VoiceProcessDto> = safeRequest { client.submitFormWithBinaryData(endpoint("voice/process"), audioForm(wav, lang)) }

    suspend fun transcribe(
        wav: ByteArray,
        lang: String,
    ): AppResult<TranscribeDto> = safeRequest { client.submitFormWithBinaryData(endpoint("transcribe"), audioForm(wav, lang)) }

    suspend fun parseRecurring(
        text: String,
        lang: String,
    ): AppResult<RecurringResponseDto> =
        safeRequest {
            client.post(endpoint("parse/recurring")) {
                contentType(ContentType.Application.Json)
                setBody(RecurringRequestDto(text, lang))
            }
        }

    suspend fun suggestBudget(): AppResult<BudgetSuggestionDto> = safeRequest { client.get(endpoint("budget/suggest")) }

    suspend fun periodReport(
        parameter: String,
        value: String,
    ): AppResult<PeriodReportDto> = safeRequest { client.get(endpoint("report/monthly")) { parameter(parameter, value) } }

    suspend fun importText(body: ImportTextRequestDto): AppResult<ImportResponseDto> {
        val response =
            sendSafely {
                client.post(endpoint(IMPORT_TEXT_PATH)) {
                    contentType(ContentType.Application.Json)
                    setBody(body)
                }
            }
        return when (response) {
            is AppResult.Failure -> {
                response
            }

            is AppResult.Success -> {
                if (response.value.isMissingEndpoint()) {
                    AppResult.Failure(AppError.NotSupportedYet)
                } else {
                    safeRequest {
                        response.value
                    }
                }
            }
        }
    }

    suspend fun isImportTextAvailable(): Boolean {
        val response =
            sendSafely {
                client.post(endpoint(IMPORT_TEXT_PATH)) {
                    contentType(ContentType.Application.Json)
                    setBody(JsonObject(emptyMap()))
                }
            }
        return response !is AppResult.Success || !response.value.isMissingEndpoint()
    }

    private suspend fun sendSafely(request: suspend () -> HttpResponse): AppResult<HttpResponse> =
        try {
            AppResult.Success(request())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            AppResult.Failure(e.toAppError())
        }

    private fun HttpResponse.isMissingEndpoint(): Boolean = status.value in MISSING_ENDPOINT_STATUSES

    private fun endpoint(path: String): String = config.apiBaseUrl.trim().trimEnd('/') + "/api/public/" + path

    private fun audioForm(
        wav: ByteArray,
        lang: String,
    ) = formData {
        append(
            AUDIO_FIELD,
            wav,
            Headers.build {
                append(HttpHeaders.ContentType, WAV_MIME_TYPE)
                append(HttpHeaders.ContentDisposition, "filename=\"$AUDIO_FILE_NAME\"")
            },
        )
        append(LANG_FIELD, lang)
    }

    companion object {
        const val AUDIO_FIELD = "audio"
        const val LANG_FIELD = "lang"
        const val AUDIO_FILE_NAME = "recording.wav"
        const val WAV_MIME_TYPE = "audio/wav"
        const val IMPORT_TEXT_PATH = "import/text"
        val MISSING_ENDPOINT_STATUSES = setOf(404, 405)
    }
}

internal fun backendLanguage(languageTag: String): String = if (languageTag.trim().lowercase().startsWith("en")) "en" else "fr"
