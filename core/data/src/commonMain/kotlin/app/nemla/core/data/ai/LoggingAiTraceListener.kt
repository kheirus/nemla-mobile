package app.nemla.core.data.ai

import app.nemla.ai.api.AiError
import app.nemla.ai.api.AiTrace
import app.nemla.ai.api.AiTraceListener
import app.nemla.ai.api.AttemptOutcome
import app.nemla.core.common.AppLogger
import app.nemla.core.common.LogAttribute
import app.nemla.core.common.LogField
import app.nemla.core.common.info
import app.nemla.core.common.warn
import app.nemla.core.data.DataLog

internal enum class AiCapabilityTag(
    val key: String,
) {
    SPEECH_TO_TEXT("speech_to_text"),
    EXPENSE_EXTRACTION("expense_extraction"),
    VOICE_EXPENSE_CAPTURE("voice_expense_capture"),
    STATEMENT_ANONYMIZATION("statement_anonymization"),
    STATEMENT_PARSING("statement_parsing"),
    RECURRING_PARSING("recurring_parsing"),
    BUDGET_SUGGESTION("budget_suggestion"),
    PERIOD_REPORT("period_report"),
    OTHER(""),
    ;

    companion object {
        fun of(key: String): AiCapabilityTag = entries.firstOrNull { it.key == key } ?: OTHER
    }
}

internal enum class AiErrorKind {
    NO_ENGINE,
    CONSENT_REQUIRED,
    QUOTA_EXCEEDED,
    OFFLINE,
    UNAUTHORIZED,
    REJECTED,
    ENGINE_FAILURE,
    PRIVACY_VIOLATION,
    ;

    companion object {
        fun of(error: AiError): AiErrorKind =
            when (error) {
                AiError.NoEngine -> NO_ENGINE
                AiError.ConsentRequired -> CONSENT_REQUIRED
                AiError.QuotaExceeded -> QUOTA_EXCEEDED
                AiError.Offline -> OFFLINE
                AiError.Unauthorized -> UNAUTHORIZED
                is AiError.Rejected -> REJECTED
                is AiError.EngineFailure -> ENGINE_FAILURE
                AiError.PrivacyViolation -> PRIVACY_VIOLATION
            }
    }
}

internal class LoggingAiTraceListener(
    private val logger: AppLogger,
) : AiTraceListener {
    override fun onTrace(trace: AiTrace) {
        val capability = LogAttribute.choice(LogField.KIND, AiCapabilityTag.of(trace.capability))
        val attempts = LogAttribute.number(LogField.ATTEMPT, trace.attempts.size)
        val served = trace.servedBy
        if (served != null) {
            logger.info(DataLog.AI_REQUEST_SERVED, capability, attempts, LogAttribute.choice(LogField.SERVICE, served.location))
            return
        }
        val lastError =
            trace.attempts.lastOrNull()?.outcome?.let { outcome ->
                when (outcome) {
                    is AttemptOutcome.Failed -> outcome.error
                    is AttemptOutcome.Skipped -> outcome.error
                    AttemptOutcome.Succeeded -> null
                }
            } ?: AiError.NoEngine
        logger.warn(DataLog.AI_REQUEST_FAILED, capability, attempts, LogAttribute.choice(LogField.REASON, AiErrorKind.of(lastError)))
    }
}
