package app.nemla.ai.api

/**
 * A typed description of one thing the app asks an AI to do.
 *
 * The capability fixes the input type, the output type and the sensitivity of what the input
 * carries. Engines are registered per capability; the router never needs to know which provider
 * (on-device model, nemla backend, another vendor) ends up doing the work.
 *
 * To add a capability: add an object here, give it models in `AiModels.kt`, register engines.
 */
sealed class AiCapability<I : Any, O : Any>(
    val key: String,
    val inputSensitivity: DataSensitivity,
) {
    /** Audio to text. */
    data object SpeechToText :
        AiCapability<SpeechRequest, Transcript>("speech_to_text", DataSensitivity.PERSONAL)

    /** Dictated sentence to structured expenses. */
    data object ExpenseExtraction :
        AiCapability<ExpenseExtractionRequest, ExtractedExpenses>("expense_extraction", DataSensitivity.PERSONAL)

    /**
     * Audio straight to structured expenses, in one call.
     * Exists because the current nemla backend only offers this combined operation;
     * the split path (SpeechToText then ExpenseExtraction) is preferred when engines exist for it.
     */
    data object VoiceExpenseCapture :
        AiCapability<SpeechRequest, VoiceCapture>("voice_expense_capture", DataSensitivity.PERSONAL)

    /** Raw bank statement text to anonymised text. On-device engines only (see [AiRouter]). */
    data object StatementAnonymization :
        AiCapability<RawStatement, AnonymizedText>("statement_anonymization", DataSensitivity.RAW_FINANCIAL)

    /** Anonymised statement text to structured expenses. */
    data object StatementParsing :
        AiCapability<StatementParsingRequest, ParsedStatement>("statement_parsing", DataSensitivity.ANONYMIZED)

    /** Free text ("loyer 850 le 5, Netflix 13,49") to recurring charges. */
    data object RecurringParsing :
        AiCapability<RecurringParsingRequest, ParsedRecurringCharges>("recurring_parsing", DataSensitivity.PERSONAL)

    /** Budget estimate learnt from the user's history. */
    data object BudgetSuggestion :
        AiCapability<BudgetSuggestionRequest, BudgetEstimate>("budget_suggestion", DataSensitivity.NONE)

    /** Monthly or yearly review with highlights and advice. */
    data object PeriodReport :
        AiCapability<PeriodReportRequest, PeriodReview>("period_report", DataSensitivity.NONE)

    override fun toString(): String = key
}
