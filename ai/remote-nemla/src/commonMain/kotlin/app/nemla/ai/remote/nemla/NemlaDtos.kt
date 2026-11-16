package app.nemla.ai.remote.nemla

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
internal class VoiceProcessDto(
    @SerialName("voice_entry_id") val voiceEntryId: String? = null,
    val transcript: JsonElement? = null,
    val expenses: List<ExpenseDto> = emptyList(),
    @SerialName("category_sources") val categorySources: List<CategorySourceDto> = emptyList(),
)

@Serializable
internal class ExpenseDto(
    val id: String? = null,
    val montant: JsonElement? = null,
    val description: String? = null,
    val date: String? = null,
    @SerialName("category_id") val categoryId: String? = null,
)

@Serializable
internal class CategorySourceDto(
    @SerialName("merchant_key") val merchantKey: String? = null,
    val source: String? = null,
)

@Serializable
internal class TranscribeDto(
    val transcript: JsonElement? = null,
)

@Serializable
internal class RecurringRequestDto(
    val text: String,
    val lang: String,
)

@Serializable
internal class RecurringResponseDto(
    val charges: List<RecurringChargeDto> = emptyList(),
)

@Serializable
internal class RecurringChargeDto(
    val nom: String? = null,
    val montant: JsonElement? = null,
    val frequence: String? = null,
    @SerialName("jour_prelevement") val jourPrelevement: JsonElement? = null,
    @SerialName("category_id") val categoryId: String? = null,
)

@Serializable
internal class BudgetSuggestionDto(
    val monthly: JsonElement? = null,
    val annual: JsonElement? = null,
    @SerialName("fixed_monthly") val fixedMonthly: JsonElement? = null,
    val months: List<MonthTotalDto> = emptyList(),
    @SerialName("per_category") val perCategory: List<CategoryEstimateDto> = emptyList(),
    val basis: String? = null,
)

@Serializable
internal class MonthTotalDto(
    val month: String,
    val total: JsonElement? = null,
)

@Serializable
internal class CategoryEstimateDto(
    @SerialName("category_id") val categoryId: String? = null,
    val monthly: JsonElement? = null,
    val months: Int = 0,
)

@Serializable
internal class PeriodReportDto(
    val month: String? = null,
    val kind: String? = null,
    @SerialName("previous_month") val previousMonth: String? = null,
    val total: JsonElement? = null,
    @SerialName("previous_total") val previousTotal: JsonElement? = null,
    val budget: JsonElement? = null,
    @SerialName("expense_count") val expenseCount: Int = 0,
    val categories: List<ReportCategoryDto> = emptyList(),
    val highlights: List<String> = emptyList(),
    val advice: List<String> = emptyList(),
)

@Serializable
internal class ReportCategoryDto(
    val nom: String? = null,
    val montant: JsonElement? = null,
    val part: JsonElement? = null,
    val variation: JsonElement? = null,
)
