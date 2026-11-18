package app.nemla.feature.reports

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.nemla.core.designsystem.component.NemlaButton
import app.nemla.core.designsystem.component.NemlaButtonVariant
import app.nemla.core.designsystem.component.NemlaDialog
import app.nemla.core.designsystem.component.NemlaLoadingIndicator
import app.nemla.core.designsystem.icon.NemlaIcons
import app.nemla.core.designsystem.theme.NemlaTheme
import app.nemla.core.domain.format.DateLabels
import app.nemla.core.domain.format.MoneyFormat
import app.nemla.core.domain.model.AppLanguage
import app.nemla.core.domain.model.CategoryShare
import app.nemla.core.domain.model.Currency
import app.nemla.core.domain.model.PeriodReview
import app.nemla.core.domain.model.ReportCalendar
import app.nemla.core.domain.model.ReportSheet
import app.nemla.core.domain.model.ReviewPeriod
import app.nemla.feature.reports.resources.Res
import app.nemla.feature.reports.resources.reports_advice
import app.nemla.feature.reports.resources.reports_delta
import app.nemla.feature.reports.resources.reports_highlights
import app.nemla.feature.reports.resources.reports_loading_report
import app.nemla.feature.reports.resources.reports_percent
import app.nemla.feature.reports.resources.reports_report_failed
import app.nemla.feature.reports.resources.reports_retry
import app.nemla.feature.reports.resources.reports_server_language
import app.nemla.feature.reports.resources.reports_share
import app.nemla.feature.reports.resources.reports_title
import app.nemla.feature.reports.resources.reports_title_generic
import app.nemla.feature.reports.resources.reports_total
import app.nemla.feature.reports.resources.reports_trend_down
import app.nemla.feature.reports.resources.reports_trend_up
import app.nemla.feature.reports.resources.reports_understood
import app.nemla.feature.reports.resources.reports_where
import app.nemla.feature.reports.resources.reports_year_label
import kotlinx.datetime.YearMonth
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
fun ReportDialog(
    period: ReviewPeriod,
    onDismiss: () -> Unit,
) {
    ReportDialogRoute(key = "report-" + ReportCalendar.key(period), request = ReportRequest.Compute(period), onDismiss = onDismiss)
}

@Composable
internal fun DeliveredReportDialog(
    opened: OpenedInboxReport,
    onDismiss: () -> Unit,
) {
    ReportDialogRoute(key = "report-inbox-" + opened.id.value, request = opened.request, onDismiss = onDismiss)
}

@Composable
private fun ReportDialogRoute(
    key: String,
    request: ReportRequest,
    onDismiss: () -> Unit,
) {
    val viewModel: ReportDialogViewModel = koinViewModel(key = key) { parametersOf(request) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    ReportDialogContent(state = state, onAction = viewModel::onAction, onDismiss = onDismiss)
}

@Composable
internal fun ReportDialogContent(
    state: ReportDialogUiState,
    onAction: (ReportDialogAction) -> Unit,
    onDismiss: () -> Unit,
) {
    NemlaDialog(
        title =
            state.period?.let { stringResource(Res.string.reports_title, periodLabel(it, state.language)) }
                ?: stringResource(Res.string.reports_title_generic),
        onDismissRequest = onDismiss,
        maxWidth = ReportDialogMaxWidth,
    ) {
        when (val content = state.content) {
            ReportContent.Loading -> {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = NemlaTheme.spacing.md),
                    horizontalArrangement = Arrangement.spacedBy(NemlaTheme.spacing.xs, Alignment.CenterHorizontally),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    NemlaLoadingIndicator(modifier = Modifier.size(NemlaTheme.sizes.iconLarge))
                    Text(
                        stringResource(Res.string.reports_loading_report),
                        style = NemlaTheme.typography.body,
                        color = NemlaTheme.colors.mutedForeground,
                    )
                }
            }

            ReportContent.Failed -> {
                Text(
                    stringResource(Res.string.reports_report_failed),
                    style = NemlaTheme.typography.body,
                    color = NemlaTheme.colors.destructive,
                )
                if (state.canRetry) {
                    NemlaButton(
                        text = stringResource(Res.string.reports_retry),
                        onClick = { onAction(ReportDialogAction.RetryClicked) },
                        variant = NemlaButtonVariant.Outline,
                        leadingIcon = NemlaIcons.RefreshCw,
                        modifier = Modifier.fillMaxWidth(),
                    )
                } else {
                    NemlaButton(
                        text = stringResource(Res.string.reports_understood),
                        onClick = onDismiss,
                        variant = NemlaButtonVariant.Outline,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            is ReportContent.Loaded -> {
                Column(
                    modifier = Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(NemlaTheme.spacing.md),
                ) {
                    ReportBody(content.sheet, state.language)
                }
                NemlaButton(
                    text = stringResource(Res.string.reports_understood),
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun ReportBody(
    sheet: ReportSheet,
    language: AppLanguage,
) {
    val review = sheet.review
    TotalBox(sheet)
    if (review.categories.isNotEmpty()) {
        Section(stringResource(Res.string.reports_where)) {
            review.categories.forEach { CategoryLine(it, sheet.currency) }
        }
    }
    if (review.highlights.isNotEmpty()) {
        Section(stringResource(Res.string.reports_highlights)) { Bullets(review.highlights) }
    }
    if (review.advice.isNotEmpty()) {
        Section(stringResource(Res.string.reports_advice)) { Bullets(review.advice) }
    }
    if (language == AppLanguage.EN && (review.highlights.isNotEmpty() || review.advice.isNotEmpty())) {
        Text(
            stringResource(Res.string.reports_server_language),
            style = NemlaTheme.typography.caption,
            color = NemlaTheme.colors.mutedForeground,
        )
    }
}

@Composable
private fun TotalBox(sheet: ReportSheet) {
    val colors = NemlaTheme.colors
    Surface(
        shape = RoundedCornerShape(NemlaTheme.radius.xl),
        color = colors.muted.copy(alpha = 0.5f),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(NemlaTheme.spacing.md), verticalArrangement = Arrangement.spacedBy(NemlaTheme.spacing.xxs)) {
            Text(stringResource(Res.string.reports_total), style = NemlaTheme.typography.caption, color = colors.mutedForeground)
            Text(MoneyFormat.format(sheet.review.totalCents, sheet.currency), style = NemlaTheme.typography.amount)
            sheet.deltaCents?.let { delta ->
                val rising = delta > 0
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(NemlaTheme.spacing.xxs)) {
                    Icon(
                        imageVector = if (rising) NemlaIcons.TrendingUp else NemlaIcons.TrendingDown,
                        contentDescription = stringResource(if (rising) Res.string.reports_trend_up else Res.string.reports_trend_down),
                        tint = if (rising) colors.destructive else colors.primary,
                        modifier = Modifier.size(14.dp),
                    )
                    val amount = MoneyFormat.format(delta, sheet.currency)
                    Text(
                        stringResource(Res.string.reports_delta, if (rising) "+$amount" else amount),
                        style = NemlaTheme.typography.caption,
                        color = colors.mutedForeground,
                    )
                }
            }
        }
    }
}

@Composable
private fun Section(
    title: String,
    content: @Composable () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(NemlaTheme.spacing.xs)) {
        Text(
            title,
            style = NemlaTheme.typography.body.copy(fontWeight = FontWeight.SemiBold),
            modifier = Modifier.semantics { heading() },
        )
        content()
    }
}

@Composable
private fun CategoryLine(
    category: CategoryShare,
    currency: Currency,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(NemlaTheme.spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            category.name,
            style = NemlaTheme.typography.body,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Text(
            stringResource(
                Res.string.reports_share,
                MoneyFormat.format(category.amountCents, currency),
                stringResource(Res.string.reports_percent, category.sharePercent.toString()),
            ),
            style = NemlaTheme.typography.caption,
            color = NemlaTheme.colors.mutedForeground,
        )
    }
}

@Composable
private fun Bullets(lines: List<String>) {
    Column(verticalArrangement = Arrangement.spacedBy(NemlaTheme.spacing.xxs)) {
        lines.forEach { line ->
            Row(horizontalArrangement = Arrangement.spacedBy(NemlaTheme.spacing.xs)) {
                Box(
                    modifier =
                        Modifier
                            .padding(top = BulletTopOffset)
                            .size(BulletSize)
                            .background(NemlaTheme.colors.mutedForeground, CircleShape),
                )
                Text(line, style = NemlaTheme.typography.body, color = NemlaTheme.colors.mutedForeground)
            }
        }
    }
}

@Composable
internal fun periodLabel(
    period: ReviewPeriod,
    language: AppLanguage,
): String =
    when (period) {
        is ReviewPeriod.Month -> DateLabels.capitalizeFirst(DateLabels.monthYear(period.month, language))
        is ReviewPeriod.Year -> stringResource(Res.string.reports_year_label, period.year.toString())
    }

private val ReportDialogMaxWidth = 448.dp
private val BulletSize = 4.dp
private val BulletTopOffset = 8.dp

@Preview
@Composable
private fun ReportDialogPreview() {
    val period = ReviewPeriod.Month(YearMonth(2026, 10))
    NemlaTheme {
        ReportDialogContent(
            state =
                ReportDialogUiState(
                    period = period,
                    language = AppLanguage.FR,
                    content =
                        ReportContent.Loaded(
                            ReportSheet(
                                PeriodReview(
                                    period,
                                    ReviewPeriod.Month(YearMonth(2026, 9)),
                                    123_450,
                                    100_000,
                                    150_000,
                                    42,
                                    listOf(CategoryShare("Courses", 40_025, 32, -1_250)),
                                    listOf("Courses concentre 32 % de vos dépenses (400 €)."),
                                    listOf("Rien à corriger ce mois-ci : continuez à dicter régulièrement."),
                                ),
                                Currency.EUR,
                            ),
                        ),
                ),
            onAction = {},
            onDismiss = {},
        )
    }
}
