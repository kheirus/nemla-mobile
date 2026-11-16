package app.nemla.feature.statementimport

import androidx.compose.runtime.Composable
import app.nemla.feature.statementimport.resources.Res
import app.nemla.feature.statementimport.resources.import_error_consent_declined
import app.nemla.feature.statementimport.resources.import_error_failed
import app.nemla.feature.statementimport.resources.import_error_no_category
import app.nemla.feature.statementimport.resources.import_error_no_expense
import app.nemla.feature.statementimport.resources.import_error_not_supported_yet
import app.nemla.feature.statementimport.resources.import_error_offline
import app.nemla.feature.statementimport.resources.import_error_open_failed
import app.nemla.feature.statementimport.resources.import_error_pdf_unsupported
import app.nemla.feature.statementimport.resources.import_error_period_too_long
import app.nemla.feature.statementimport.resources.import_error_quota
import app.nemla.feature.statementimport.resources.import_error_rate_limited
import app.nemla.feature.statementimport.resources.import_error_spreadsheet
import app.nemla.feature.statementimport.resources.import_error_timeout
import app.nemla.feature.statementimport.resources.import_error_too_large
import app.nemla.feature.statementimport.resources.import_error_unreadable
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

internal val ImportMessage.isInformation: Boolean
    get() = this == ImportMessage.NOT_SUPPORTED_YET || this == ImportMessage.CONSENT_DECLINED

@Composable
internal fun ImportMessage.text(): String = stringResource(resource())

private fun ImportMessage.resource(): StringResource =
    when (this) {
        ImportMessage.QUOTA_REACHED -> Res.string.import_error_quota
        ImportMessage.TOO_LARGE -> Res.string.import_error_too_large
        ImportMessage.UNREADABLE -> Res.string.import_error_unreadable
        ImportMessage.SPREADSHEET -> Res.string.import_error_spreadsheet
        ImportMessage.PDF_UNSUPPORTED -> Res.string.import_error_pdf_unsupported
        ImportMessage.OPEN_FAILED -> Res.string.import_error_open_failed
        ImportMessage.NO_EXPENSE -> Res.string.import_error_no_expense
        ImportMessage.PERIOD_TOO_LONG -> Res.string.import_error_period_too_long
        ImportMessage.NO_CATEGORY -> Res.string.import_error_no_category
        ImportMessage.NOT_SUPPORTED_YET -> Res.string.import_error_not_supported_yet
        ImportMessage.OFFLINE -> Res.string.import_error_offline
        ImportMessage.TIMEOUT -> Res.string.import_error_timeout
        ImportMessage.RATE_LIMITED -> Res.string.import_error_rate_limited
        ImportMessage.CONSENT_DECLINED -> Res.string.import_error_consent_declined
        ImportMessage.FAILED -> Res.string.import_error_failed
    }
