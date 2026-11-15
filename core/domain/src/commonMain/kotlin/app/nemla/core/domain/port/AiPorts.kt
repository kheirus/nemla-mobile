package app.nemla.core.domain.port

import app.nemla.core.common.AppResult
import app.nemla.core.domain.model.AppLanguage
import app.nemla.core.domain.model.BudgetEstimate
import app.nemla.core.domain.model.PeriodReview
import app.nemla.core.domain.model.RecurringChargeDraft
import app.nemla.core.domain.model.ReviewPeriod
import app.nemla.core.domain.model.StatementFile
import app.nemla.core.domain.model.StatementImport
import app.nemla.core.domain.model.VoiceCapture
import app.nemla.core.domain.model.VoiceRecording

interface VoiceExpenseCapturer {
    suspend fun capture(
        recording: VoiceRecording,
        language: AppLanguage,
    ): AppResult<VoiceCapture>
}

interface StatementImporter {
    suspend fun import(
        file: StatementFile,
        language: AppLanguage,
    ): AppResult<StatementImport>
}

interface RecurringChargeParser {
    suspend fun parse(
        text: String,
        language: AppLanguage,
    ): AppResult<List<RecurringChargeDraft>>
}

interface BudgetAdvisor {
    suspend fun suggest(): AppResult<BudgetEstimate>
}

interface PeriodReviewer {
    suspend fun review(period: ReviewPeriod): AppResult<PeriodReview>
}
