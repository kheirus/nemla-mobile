package app.nemla.ai.api

sealed class AiCapability<I : Any, O : Any>(
    val key: String,
    val inputSensitivity: DataSensitivity,
) {
    data object SpeechToText :
        AiCapability<SpeechRequest, Transcript>("speech_to_text", DataSensitivity.PERSONAL)

    data object ExpenseExtraction :
        AiCapability<ExpenseExtractionRequest, ExtractedExpenses>("expense_extraction", DataSensitivity.PERSONAL)

    data object VoiceExpenseCapture :
        AiCapability<SpeechRequest, VoiceCapture>("voice_expense_capture", DataSensitivity.PERSONAL)

    data object StatementAnonymization :
        AiCapability<RawStatement, AnonymizedText>("statement_anonymization", DataSensitivity.RAW_FINANCIAL)

    data object StatementParsing :
        AiCapability<StatementParsingRequest, ParsedStatement>("statement_parsing", DataSensitivity.ANONYMIZED)

    data object RecurringParsing :
        AiCapability<RecurringParsingRequest, ParsedRecurringCharges>("recurring_parsing", DataSensitivity.PERSONAL)

    data object BudgetSuggestion :
        AiCapability<BudgetSuggestionRequest, BudgetEstimate>("budget_suggestion", DataSensitivity.NONE)

    data object PeriodReport :
        AiCapability<PeriodReportRequest, PeriodReview>("period_report", DataSensitivity.NONE)

    override fun toString(): String = key
}
