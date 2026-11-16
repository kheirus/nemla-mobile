package app.nemla.feature.statementimport

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.nemla.core.common.AppError
import app.nemla.core.common.AppResult
import app.nemla.core.common.ValidationCode
import app.nemla.core.documents.DocumentError
import app.nemla.core.documents.DocumentKind
import app.nemla.core.documents.DocumentReadResult
import app.nemla.core.documents.DocumentText
import app.nemla.core.documents.DocumentTextReader
import app.nemla.core.documents.PickedDocument
import app.nemla.core.domain.model.AiConsentLevel
import app.nemla.core.domain.model.AiConsents
import app.nemla.core.domain.model.StatementFormat
import app.nemla.core.domain.model.StatementText
import app.nemla.core.domain.usecase.CurrentLanguages
import app.nemla.core.domain.usecase.GetMonthlyQuota
import app.nemla.core.domain.usecase.GrantAiConsent
import app.nemla.core.domain.usecase.ImportStatement
import app.nemla.core.domain.usecase.ImportedReviews
import app.nemla.core.domain.usecase.ObserveAiConsents
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

internal class StatementImportViewModel(
    private val reader: DocumentTextReader,
    private val importStatement: ImportStatement,
    private val getQuota: GetMonthlyQuota,
    private val observeConsents: ObserveAiConsents,
    private val grantConsent: GrantAiConsent,
    private val languages: CurrentLanguages,
) : ViewModel() {
    private val mutableState = MutableStateFlow(StatementImportUiState())
    val state: StateFlow<StatementImportUiState> = mutableState.asStateFlow()

    private val effectChannel = Channel<StatementImportEffect>(Channel.BUFFERED)
    val effects: Flow<StatementImportEffect> = effectChannel.receiveAsFlow()

    private var document: PickedDocument? = null
    private var extracted: DocumentText? = null
    private var job: Job? = null

    init {
        refreshQuota()
    }

    fun onAction(action: StatementImportAction) {
        when (action) {
            StatementImportAction.Opened -> refreshQuota()
            is StatementImportAction.FilePicked -> pick(action.document)
            StatementImportAction.ReadClicked -> requestRead()
            StatementImportAction.ConsentAccepted -> acceptConsent()
            StatementImportAction.ConsentDeclined -> declineConsent()
            StatementImportAction.Dismissed -> dismiss()
        }
    }

    private fun pick(picked: PickedDocument?) {
        if (picked == null || !mutableState.value.canPick) return
        document = picked
        extracted = null
        mutableState.update { it.copy(fileName = picked.displayName, message = null, redactions = null) }
    }

    private fun requestRead() {
        if (!mutableState.value.canRead || job?.isActive == true) return
        mutableState.update { it.copy(message = null, redactions = null) }
        job =
            viewModelScope.launch {
                if (observeConsents().first().allowsStatements()) {
                    run()
                } else {
                    mutableState.update { it.copy(isConsentPromptVisible = true) }
                }
            }
    }

    private fun acceptConsent() {
        mutableState.update { it.copy(isConsentPromptVisible = false) }
        job =
            viewModelScope.launch {
                if (grantConsent(AiConsentLevel.ANONYMIZED) is AppResult.Failure) {
                    forgetText()
                    mutableState.update { it.copy(message = ImportMessage.FAILED) }
                    return@launch
                }
                run()
            }
    }

    private fun declineConsent() {
        forgetText()
        mutableState.update {
            it.copy(isConsentPromptVisible = false, phase = ImportPhase.Idle, redactions = null, message = ImportMessage.CONSENT_DECLINED)
        }
    }

    private suspend fun run() {
        val picked = document ?: return
        mutableState.update { it.copy(phase = ImportPhase.Reading, message = null, redactions = null) }
        val text =
            extracted ?: when (val read = reader.read(picked)) {
                is DocumentReadResult.Success -> read.document.also { extracted = it }
                is DocumentReadResult.Failure -> return finish(read.error.toMessage())
            }
        val statement = StatementText(text.text, text.kind.toFormat() ?: return finish(ImportMessage.SPREADSHEET))
        val result =
            importStatement(statement, languages.ui()) { redactions ->
                mutableState.update { it.copy(phase = ImportPhase.Parsing, redactions = redactions) }
            }
        when (result) {
            is AppResult.Success -> {
                val drafts = result.value.drafts.filter { it.id != null }
                if (drafts.isEmpty()) {
                    finish(ImportMessage.NO_EXPENSE)
                } else {
                    forgetText()
                    resetSelection()
                    effectChannel.send(StatementImportEffect.Imported(ImportedReviews.of(drafts)))
                }
            }

            is AppResult.Failure -> {
                if (result.error == AppError.ConsentRequired) {
                    mutableState.update { it.copy(phase = ImportPhase.Idle, redactions = null, isConsentPromptVisible = true) }
                } else {
                    finish(result.error.toMessage())
                }
            }
        }
        refreshQuota()
    }

    private fun finish(message: ImportMessage) {
        forgetText()
        mutableState.update {
            it.copy(
                phase = ImportPhase.Idle,
                message = message,
                redactions = if (message == ImportMessage.NOT_SUPPORTED_YET) it.redactions else null,
            )
        }
    }

    private fun dismiss() {
        if (!mutableState.value.isDismissible) return
        job?.cancel()
        forgetText()
        resetSelection()
        viewModelScope.launch { effectChannel.send(StatementImportEffect.Close) }
    }

    private fun resetSelection() {
        document = null
        mutableState.update { StatementImportUiState(quota = it.quota) }
    }

    private fun forgetText() {
        extracted = null
    }

    private fun refreshQuota() {
        viewModelScope.launch {
            val remaining = (getQuota() as? AppResult.Success)?.value?.importRemaining
            mutableState.update { current ->
                val next =
                    when {
                        remaining == null -> if (current.quota == ImportQuota.Loading) ImportQuota.Unknown else current.quota
                        remaining > 0 -> ImportQuota.Available(remaining)
                        else -> ImportQuota.Exhausted
                    }
                current.copy(quota = next)
            }
        }
    }

    override fun onCleared() {
        forgetText()
        document = null
    }
}

private fun AiConsents.allowsStatements(): Boolean = isGranted(AiConsentLevel.ANONYMIZED) || isGranted(AiConsentLevel.PERSONAL)

private fun DocumentKind.toFormat(): StatementFormat? =
    when (this) {
        DocumentKind.PDF -> StatementFormat.PDF
        DocumentKind.CSV -> StatementFormat.CSV
        DocumentKind.TEXT -> StatementFormat.TEXT
        DocumentKind.SPREADSHEET -> null
    }

internal fun DocumentError.toMessage(): ImportMessage =
    when (this) {
        DocumentError.TOO_LARGE -> ImportMessage.TOO_LARGE
        DocumentError.SPREADSHEET_UNSUPPORTED -> ImportMessage.SPREADSHEET
        DocumentError.PDF_UNSUPPORTED_ON_DEVICE -> ImportMessage.PDF_UNSUPPORTED
        DocumentError.EMPTY_OR_SCANNED -> ImportMessage.UNREADABLE
        DocumentError.UNREADABLE -> ImportMessage.OPEN_FAILED
    }

internal fun AppError.toMessage(): ImportMessage =
    when (this) {
        AppError.QuotaExceeded -> {
            ImportMessage.QUOTA_REACHED
        }

        AppError.NotSupportedYet -> {
            ImportMessage.NOT_SUPPORTED_YET
        }

        AppError.Network.Offline -> {
            ImportMessage.OFFLINE
        }

        AppError.Network.Timeout -> {
            ImportMessage.TIMEOUT
        }

        AppError.RateLimited -> {
            ImportMessage.RATE_LIMITED
        }

        is AppError.Validation -> {
            when (code) {
                ValidationCode.PAYLOAD_TOO_LARGE -> ImportMessage.TOO_LARGE
                ValidationCode.UNREADABLE_FILE -> ImportMessage.UNREADABLE
                ValidationCode.NO_EXPENSE_DETECTED -> ImportMessage.NO_EXPENSE
                ValidationCode.PERIOD_TOO_LONG -> ImportMessage.PERIOD_TOO_LONG
                ValidationCode.NO_CATEGORY -> ImportMessage.NO_CATEGORY
                else -> ImportMessage.FAILED
            }
        }

        else -> {
            ImportMessage.FAILED
        }
    }
