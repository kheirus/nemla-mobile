package app.nemla.feature.statementimport

import app.nemla.core.documents.PickedDocument
import app.nemla.core.domain.model.PendingReview

internal sealed interface ImportQuota {
    data object Loading : ImportQuota

    data object Unknown : ImportQuota

    data class Available(
        val remaining: Int,
    ) : ImportQuota

    data object Exhausted : ImportQuota
}

internal enum class ImportPhase { Idle, Reading, Parsing }

internal enum class ImportMessage {
    QUOTA_REACHED,
    TOO_LARGE,
    UNREADABLE,
    SPREADSHEET,
    PDF_UNSUPPORTED,
    OPEN_FAILED,
    NO_EXPENSE,
    PERIOD_TOO_LONG,
    NO_CATEGORY,
    NOT_SUPPORTED_YET,
    OFFLINE,
    TIMEOUT,
    RATE_LIMITED,
    CONSENT_DECLINED,
    FAILED,
}

internal data class StatementImportUiState(
    val quota: ImportQuota = ImportQuota.Loading,
    val fileName: String? = null,
    val phase: ImportPhase = ImportPhase.Idle,
    val redactions: Int? = null,
    val message: ImportMessage? = null,
    val isConsentPromptVisible: Boolean = false,
) {
    val isBusy: Boolean get() = phase != ImportPhase.Idle

    val canPick: Boolean get() = !isBusy && !isConsentPromptVisible

    val canRead: Boolean get() = fileName != null && canPick && quota != ImportQuota.Exhausted

    val isDismissible: Boolean get() = !isBusy

    override fun toString(): String =
        "StatementImportUiState(quota=$quota, hasFile=${fileName != null}, phase=$phase, redactions=$redactions, message=$message)"
}

internal sealed interface StatementImportAction {
    data object Opened : StatementImportAction

    data class FilePicked(
        val document: PickedDocument?,
    ) : StatementImportAction

    data object ReadClicked : StatementImportAction

    data object ConsentAccepted : StatementImportAction

    data object ConsentDeclined : StatementImportAction

    data object Dismissed : StatementImportAction
}

internal sealed interface StatementImportEffect {
    data class Imported(
        val review: PendingReview,
    ) : StatementImportEffect

    data object Close : StatementImportEffect
}
