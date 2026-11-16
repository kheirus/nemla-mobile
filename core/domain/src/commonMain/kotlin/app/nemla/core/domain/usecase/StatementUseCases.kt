package app.nemla.core.domain.usecase

import app.nemla.core.common.AppError
import app.nemla.core.common.AppResult
import app.nemla.core.domain.model.AppLanguage
import app.nemla.core.domain.model.ExpenseDraft
import app.nemla.core.domain.model.PendingReview
import app.nemla.core.domain.model.ReviewOrigin
import app.nemla.core.domain.model.StatementImport
import app.nemla.core.domain.model.StatementText
import app.nemla.core.domain.port.QuotaRepository
import app.nemla.core.domain.port.StatementImporter

class ImportStatement(
    private val importer: StatementImporter,
    private val quotas: QuotaRepository,
) {
    suspend operator fun invoke(
        statement: StatementText,
        language: AppLanguage,
        onAnonymized: (redactions: Int) -> Unit = {},
    ): AppResult<StatementImport> {
        val quota = quotas.currentMonth()
        if (quota is AppResult.Success && !quota.value.canImport) return AppResult.Failure(AppError.QuotaExceeded)
        return importer.import(statement, language, onAnonymized)
    }
}

object ImportedReviews {
    fun of(drafts: List<ExpenseDraft>): PendingReview =
        PendingReview(
            origin = ReviewOrigin.IMPORT,
            voiceEntryId = null,
            drafts = drafts.filter { it.id != null },
            announce = true,
        )
}
