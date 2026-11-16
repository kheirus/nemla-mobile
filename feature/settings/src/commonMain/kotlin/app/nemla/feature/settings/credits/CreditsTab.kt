package app.nemla.feature.settings.credits

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import app.nemla.core.common.AppResult
import app.nemla.core.designsystem.component.NemlaProgressBar
import app.nemla.core.designsystem.theme.NemlaTheme
import app.nemla.core.domain.model.MonthlyQuota
import app.nemla.core.domain.usecase.GetMonthlyQuota
import app.nemla.feature.settings.common.Caption
import app.nemla.feature.settings.common.PanelCard
import app.nemla.feature.settings.common.tabular
import app.nemla.feature.settings.resources.Res
import app.nemla.feature.settings.resources.credits_exhausted
import app.nemla.feature.settings.resources.credits_failed
import app.nemla.feature.settings.resources.credits_import
import app.nemla.feature.settings.resources.credits_imports_left
import app.nemla.feature.settings.resources.credits_intro
import app.nemla.feature.settings.resources.credits_loading
import app.nemla.feature.settings.resources.credits_manual
import app.nemla.feature.settings.resources.credits_title
import app.nemla.feature.settings.resources.credits_unlimited
import app.nemla.feature.settings.resources.credits_usage
import app.nemla.feature.settings.resources.credits_voice
import app.nemla.feature.settings.resources.credits_voice_left
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.PluralStringResource
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

internal data class CreditsUiState(
    val isLoading: Boolean = true,
    val quota: MonthlyQuota? = null,
    val failed: Boolean = false,
)

internal class CreditsViewModel(
    private val getQuota: GetMonthlyQuota,
) : ViewModel() {
    private val mutableState = MutableStateFlow(CreditsUiState())
    val state: StateFlow<CreditsUiState> = mutableState.asStateFlow()

    fun refresh() {
        viewModelScope.launch {
            when (val result = getQuota()) {
                is AppResult.Success -> mutableState.value = CreditsUiState(isLoading = false, quota = result.value)
                is AppResult.Failure -> mutableState.update { it.copy(isLoading = false, failed = it.quota == null) }
            }
        }
    }
}

private const val LOW_SHARE = 0.34

@Composable
internal fun CreditsTabRoute(viewModel: CreditsViewModel = koinViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(viewModel) { viewModel.refresh() }
    CreditsTab(state)
}

@Composable
internal fun CreditsTab(state: CreditsUiState) {
    PanelCard(title = stringResource(Res.string.credits_title), subtitle = stringResource(Res.string.credits_intro)) {
        val quota = state.quota
        when {
            quota != null -> {
                QuotaTile(stringResource(Res.string.credits_voice), quota.voiceUsed, quota.voiceLimit, Res.plurals.credits_voice_left)
                QuotaTile(stringResource(Res.string.credits_import), quota.importUsed, quota.importLimit, Res.plurals.credits_imports_left)
                Tile(
                    stringResource(Res.string.credits_manual),
                    "∞",
                    0f,
                    stringResource(Res.string.credits_unlimited),
                    NemlaTheme.colors.creditOk,
                )
            }

            state.isLoading -> {
                Caption(stringResource(Res.string.credits_loading), style = NemlaTheme.typography.body)
            }

            else -> {
                Caption(stringResource(Res.string.credits_failed), style = NemlaTheme.typography.body)
            }
        }
    }
}

@Composable
private fun QuotaTile(
    title: String,
    used: Int,
    limit: Int,
    unit: PluralStringResource,
) {
    val left = (limit - used).coerceAtLeast(0)
    val color =
        when {
            left == 0 -> NemlaTheme.colors.destructive
            limit > 0 && left <= limit * LOW_SHARE -> NemlaTheme.colors.creditLow
            else -> NemlaTheme.colors.creditOk
        }
    Tile(
        title = title,
        value = stringResource(Res.string.credits_usage, used, limit),
        progress = if (limit > 0) used.toFloat() / limit else 1f,
        caption = if (left == 0) stringResource(Res.string.credits_exhausted) else pluralStringResource(unit, left, left),
        color = color,
    )
}

@Composable
private fun Tile(
    title: String,
    value: String,
    progress: Float,
    caption: String,
    color: Color,
) {
    val colors = NemlaTheme.colors
    val shape = RoundedCornerShape(NemlaTheme.radius.xl)
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .border(1.dp, colors.border, shape)
                .background(colors.card.copy(alpha = 0.6f), shape)
                .padding(NemlaTheme.spacing.sm),
        verticalArrangement = Arrangement.spacedBy(NemlaTheme.spacing.xs),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(title, style = NemlaTheme.typography.bodyStrong)
            Text(value, style = NemlaTheme.typography.bodyStrong.tabular())
        }
        NemlaProgressBar(progress = progress.coerceIn(0f, 1f))
        Text(caption, style = NemlaTheme.typography.caption.copy(fontWeight = FontWeight.SemiBold), color = color)
    }
}
