package app.nemla.feature.reports

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import app.nemla.core.designsystem.icon.NemlaIcons
import app.nemla.core.designsystem.theme.NemlaTheme
import app.nemla.core.domain.model.AppLanguage
import app.nemla.core.domain.model.ReviewPeriod
import app.nemla.feature.reports.resources.Res
import app.nemla.feature.reports.resources.reports_notice_month
import app.nemla.feature.reports.resources.reports_notice_title
import app.nemla.feature.reports.resources.reports_notice_view
import app.nemla.feature.reports.resources.reports_notice_year
import kotlinx.coroutines.awaitCancellation
import kotlinx.datetime.YearMonth
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun ReportArrivalNotice() {
    ReportArrivalNoticeRoute()
}

@Composable
internal fun ReportArrivalNoticeRoute(viewModel: ReportNoticeViewModel = koinViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(viewModel, lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            viewModel.onAction(ReportNoticeAction.Started)
            awaitCancellation()
        }
    }
    ReportArrivalNoticeContent(state = state, onAction = viewModel::onAction)
    state.opened?.let { period ->
        ReportDialog(period = period, onDismiss = { viewModel.onAction(ReportNoticeAction.DialogClosed) })
    }
}

@Composable
internal fun ReportArrivalNoticeContent(
    state: ReportNoticeUiState,
    onAction: (ReportNoticeAction) -> Unit,
) {
    val visibility = remember { MutableTransitionState(false) }
    visibility.targetState = state.periods.isNotEmpty()
    var shown by remember { mutableStateOf(state.periods) }
    if (state.periods.isNotEmpty()) shown = state.periods
    AnimatedVisibility(
        visibleState = visibility,
        enter = expandVertically(expandFrom = Alignment.Top) + fadeIn(),
        exit = shrinkVertically(shrinkTowards = Alignment.Top) + fadeOut(),
    ) {
        Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(NemlaTheme.spacing.sm)) {
            shown.forEach { period ->
                NoticeCard(period, state.language) { onAction(ReportNoticeAction.NoticeClicked(period)) }
            }
        }
    }
}

@Composable
private fun NoticeCard(
    period: ReviewPeriod,
    language: AppLanguage,
    onClick: () -> Unit,
) {
    val colors = NemlaTheme.colors
    val shape = RoundedCornerShape(NemlaTheme.radius.xl)
    Surface(
        shape = shape,
        color = colors.card,
        border = BorderStroke(1.dp, colors.accent.copy(alpha = 0.4f)),
        modifier = Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onClick),
    ) {
        Row(
            modifier = Modifier.padding(NemlaTheme.spacing.sm),
            horizontalArrangement = Arrangement.spacedBy(NemlaTheme.spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier.size(NoticeBadgeSize).background(colors.accent.copy(alpha = 0.2f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(NemlaIcons.BellRing, contentDescription = null, modifier = Modifier.size(NemlaTheme.sizes.icon))
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    stringResource(Res.string.reports_notice_title, periodLabel(period, language)),
                    style = NemlaTheme.typography.body.copy(fontWeight = FontWeight.SemiBold),
                )
                Text(
                    stringResource(if (period is ReviewPeriod.Year) Res.string.reports_notice_year else Res.string.reports_notice_month),
                    style = NemlaTheme.typography.caption,
                    color = colors.mutedForeground,
                )
            }
            Text(
                stringResource(Res.string.reports_notice_view),
                style = NemlaTheme.typography.caption.copy(fontWeight = FontWeight.Medium),
                color = colors.primary,
            )
        }
    }
}

private val NoticeBadgeSize = 36.dp

@Preview
@Composable
private fun ReportArrivalNoticePreview() {
    NemlaTheme {
        ReportArrivalNoticeContent(
            state =
                ReportNoticeUiState(
                    language = AppLanguage.FR,
                    periods = listOf(ReviewPeriod.Month(YearMonth(2026, 12)), ReviewPeriod.Year(2026)),
                ),
            onAction = {},
        )
    }
}
