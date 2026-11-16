package app.nemla.feature.voice.capture

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import app.nemla.core.audio.rememberMicrophonePermissionRequest
import app.nemla.core.designsystem.component.NemlaBanner
import app.nemla.core.designsystem.component.NemlaBannerKind
import app.nemla.core.designsystem.component.NemlaDialog
import app.nemla.core.designsystem.component.NemlaProgressBar
import app.nemla.core.designsystem.icon.NemlaIcons
import app.nemla.core.designsystem.theme.NemlaTheme
import app.nemla.feature.voice.common.AiConsentDialog
import app.nemla.feature.voice.common.NoticeKind
import app.nemla.feature.voice.common.text
import app.nemla.feature.voice.resources.Res
import app.nemla.feature.voice.resources.voice_dialog_description
import app.nemla.feature.voice.resources.voice_dialog_title
import app.nemla.feature.voice.resources.voice_elapsed
import app.nemla.feature.voice.resources.voice_fab_description
import app.nemla.feature.voice.resources.voice_fab_label
import app.nemla.feature.voice.resources.voice_menu_coming_soon
import app.nemla.feature.voice.resources.voice_menu_credits_exhausted
import app.nemla.feature.voice.resources.voice_menu_dictate
import app.nemla.feature.voice.resources.voice_menu_dictate_max
import app.nemla.feature.voice.resources.voice_menu_dictations_left
import app.nemla.feature.voice.resources.voice_menu_import
import app.nemla.feature.voice.resources.voice_menu_import_exhausted
import app.nemla.feature.voice.resources.voice_menu_import_formats
import app.nemla.feature.voice.resources.voice_menu_import_formats_only
import app.nemla.feature.voice.resources.voice_menu_imports_left
import app.nemla.feature.voice.resources.voice_menu_manual
import app.nemla.feature.voice.resources.voice_menu_resume_review
import app.nemla.feature.voice.resources.voice_menu_resume_review_hint
import app.nemla.feature.voice.resources.voice_notice_dismiss
import app.nemla.feature.voice.resources.voice_start_recording
import app.nemla.feature.voice.resources.voice_status_idle
import app.nemla.feature.voice.resources.voice_status_processing
import app.nemla.feature.voice.resources.voice_status_recording
import app.nemla.feature.voice.resources.voice_stop_recording
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

private const val LOW_CREDIT_THRESHOLD = 3
private const val NOTICE_DURATION_MILLIS = 6_000L
private const val DISABLED_ALPHA = 0.5f
private const val SPINNER_PERIOD_MILLIS = 1_000

@Composable
fun VoiceCaptureButton(
    onOpenReview: () -> Unit,
    modifier: Modifier = Modifier,
    onImportStatement: (() -> Unit)? = null,
) {
    VoiceCaptureRoute(onOpenReview = onOpenReview, onImportStatement = onImportStatement, modifier = modifier)
}

@Composable
internal fun VoiceCaptureRoute(
    onOpenReview: () -> Unit,
    onImportStatement: (() -> Unit)?,
    modifier: Modifier = Modifier,
    viewModel: VoiceCaptureViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val currentOnOpenReview by rememberUpdatedState(onOpenReview)
    val currentOnImportStatement by rememberUpdatedState(onImportStatement)
    val requestMicrophone =
        rememberMicrophonePermissionRequest { granted ->
            viewModel.onAction(VoiceCaptureAction.MicrophonePermissionResult(granted))
        }
    val currentRequestMicrophone by rememberUpdatedState(requestMicrophone)
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(viewModel, lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            viewModel.effects.collect { effect ->
                when (effect) {
                    VoiceCaptureEffect.RequestMicrophonePermission -> currentRequestMicrophone()
                    VoiceCaptureEffect.OpenReview -> currentOnOpenReview()
                    VoiceCaptureEffect.OpenImport -> currentOnImportStatement?.invoke()
                }
            }
        }
    }
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { viewModel.onAction(VoiceCaptureAction.AppBackgrounded) }
    VoiceCaptureContent(
        state = state,
        onAction = viewModel::onAction,
        modifier = modifier,
        importAvailable = onImportStatement != null,
    )
}

@Composable
internal fun VoiceCaptureContent(
    state: VoiceCaptureUiState,
    onAction: (VoiceCaptureAction) -> Unit,
    modifier: Modifier = Modifier,
    importAvailable: Boolean = false,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.End,
        verticalArrangement = Arrangement.spacedBy(NemlaTheme.spacing.sm),
    ) {
        state.notice?.let { notice ->
            LaunchedEffect(notice) {
                delay(NOTICE_DURATION_MILLIS)
                onAction(VoiceCaptureAction.NoticeDismissed)
            }
            val dismissLabel = stringResource(Res.string.voice_notice_dismiss)
            NemlaBanner(
                message = notice.message.text(),
                kind =
                    when (notice.kind) {
                        NoticeKind.Error -> NemlaBannerKind.Error
                        NoticeKind.Info -> NemlaBannerKind.Info
                        NoticeKind.Success -> NemlaBannerKind.Success
                    },
                modifier =
                    Modifier
                        .widthIn(max = NemlaTheme.sizes.authCardMaxWidth)
                        .clickable(onClickLabel = dismissLabel) { onAction(VoiceCaptureAction.NoticeDismissed) },
            )
        }
        Box {
            AddFab(onClick = { onAction(VoiceCaptureAction.FabClicked) })
            AddMenu(state = state, onAction = onAction, importAvailable = importAvailable)
        }
    }
    if (state.isDialogOpen) DictationDialog(state = state, onAction = onAction)
    if (state.isConsentPromptVisible) ConsentDialog(onAction = onAction)
}

@Composable
private fun AddFab(onClick: () -> Unit) {
    val colors = NemlaTheme.colors
    val description = stringResource(Res.string.voice_fab_description)
    ExtendedFloatingActionButton(
        onClick = onClick,
        shape = CircleShape,
        containerColor = colors.primary,
        contentColor = colors.primaryForeground,
        modifier = Modifier.semantics { contentDescription = description },
        icon = { Icon(NemlaIcons.Plus, contentDescription = null, modifier = Modifier.size(NemlaTheme.sizes.iconLarge)) },
        text = { Text(stringResource(Res.string.voice_fab_label), style = NemlaTheme.typography.bodyEmphasis) },
    )
}

@Composable
private fun AddMenu(
    state: VoiceCaptureUiState,
    onAction: (VoiceCaptureAction) -> Unit,
    importAvailable: Boolean,
) {
    val colors = NemlaTheme.colors
    DropdownMenu(
        expanded = state.isMenuOpen,
        onDismissRequest = { onAction(VoiceCaptureAction.MenuDismissed) },
        shape = RoundedCornerShape(NemlaTheme.radius.md),
        containerColor = colors.popover,
        modifier = Modifier.width(MENU_WIDTH),
    ) {
        MenuEntry(
            icon = NemlaIcons.Mic,
            title = stringResource(Res.string.voice_menu_dictate),
            enabled = state.canDictate,
            onClick = { onAction(VoiceCaptureAction.DictateClicked) },
            subtitle = { DictationQuota(state.quota) },
        )
        if (state.hasPendingReview) {
            MenuEntry(
                icon = NemlaIcons.Check,
                title = stringResource(Res.string.voice_menu_resume_review),
                enabled = true,
                onClick = { onAction(VoiceCaptureAction.ResumeReviewClicked) },
                subtitle = { MenuHint(stringResource(Res.string.voice_menu_resume_review_hint)) },
            )
        }
        MenuEntry(
            icon = NemlaIcons.Pencil,
            title = stringResource(Res.string.voice_menu_manual),
            enabled = false,
            onClick = {},
            subtitle = { MenuHint(stringResource(Res.string.voice_menu_coming_soon)) },
        )
        MenuEntry(
            icon = NemlaIcons.FileUp,
            title = stringResource(Res.string.voice_menu_import),
            enabled = importAvailable && state.canImport,
            onClick = { onAction(VoiceCaptureAction.ImportClicked) },
            subtitle = {
                if (importAvailable) ImportQuota(state.importQuota) else MenuHint(stringResource(Res.string.voice_menu_coming_soon))
            },
        )
    }
}

private val MENU_WIDTH = 240.dp

@Composable
private fun MenuEntry(
    icon: ImageVector,
    title: String,
    enabled: Boolean,
    onClick: () -> Unit,
    subtitle: @Composable () -> Unit,
) {
    val colors = NemlaTheme.colors
    DropdownMenuItem(
        enabled = enabled,
        onClick = onClick,
        modifier = Modifier.alpha(if (enabled) 1f else DISABLED_ALPHA),
        leadingIcon = { Icon(icon, contentDescription = null, tint = colors.primary, modifier = Modifier.size(NemlaTheme.sizes.icon)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, style = NemlaTheme.typography.bodyStrong, color = colors.popoverForeground)
                subtitle()
            }
        },
        colors =
            MenuDefaults.itemColors(
                textColor = colors.popoverForeground,
                disabledTextColor = colors.popoverForeground,
                leadingIconColor = colors.primary,
                disabledLeadingIconColor = colors.primary,
            ),
    )
}

@Composable
private fun MenuHint(text: String) {
    Text(text, style = NemlaTheme.typography.caption, color = NemlaTheme.colors.mutedForeground)
}

@Composable
private fun DictationQuota(quota: QuotaStatus) {
    val colors = NemlaTheme.colors
    val caption = NemlaTheme.typography.caption
    when (quota) {
        QuotaStatus.Exhausted -> {
            Text(
                stringResource(Res.string.voice_menu_credits_exhausted),
                style = caption.copy(fontWeight = FontWeight.SemiBold),
                color = colors.destructive,
            )
        }

        is QuotaStatus.Available -> {
            val left = pluralStringResource(Res.plurals.voice_menu_dictations_left, quota.remaining, quota.remaining)
            val prefix = stringResource(Res.string.voice_menu_dictate_max)
            val tint = if (quota.remaining <= LOW_CREDIT_THRESHOLD) colors.creditLow else colors.creditOk
            Text(
                buildAnnotatedString {
                    withStyle(SpanStyle(color = colors.mutedForeground)) { append(prefix) }
                    withStyle(SpanStyle(color = tint, fontWeight = FontWeight.SemiBold)) { append(left) }
                },
                style = caption,
            )
        }

        QuotaStatus.Loading, QuotaStatus.Unknown -> {
            MenuHint(stringResource(Res.string.voice_menu_dictate_max).trimEnd(' ', '—').trimEnd())
        }
    }
}

@Composable
private fun ImportQuota(quota: QuotaStatus) {
    val colors = NemlaTheme.colors
    val caption = NemlaTheme.typography.caption
    when (quota) {
        QuotaStatus.Exhausted -> {
            Text(
                stringResource(Res.string.voice_menu_import_exhausted),
                style = caption.copy(fontWeight = FontWeight.SemiBold),
                color = colors.destructive,
            )
        }

        is QuotaStatus.Available -> {
            val left = pluralStringResource(Res.plurals.voice_menu_imports_left, quota.remaining, quota.remaining)
            val prefix = stringResource(Res.string.voice_menu_import_formats)
            Text(
                buildAnnotatedString {
                    withStyle(SpanStyle(color = colors.mutedForeground)) { append(prefix) }
                    withStyle(SpanStyle(color = colors.creditOk, fontWeight = FontWeight.SemiBold)) { append(left) }
                },
                style = caption,
            )
        }

        QuotaStatus.Loading, QuotaStatus.Unknown -> {
            MenuHint(stringResource(Res.string.voice_menu_import_formats_only))
        }
    }
}

@Composable
private fun DictationDialog(
    state: VoiceCaptureUiState,
    onAction: (VoiceCaptureAction) -> Unit,
) {
    NemlaDialog(
        title = stringResource(Res.string.voice_dialog_title),
        description = stringResource(Res.string.voice_dialog_description),
        dismissible = state.isDialogDismissible,
        onDismissRequest = { onAction(VoiceCaptureAction.DialogDismissed) },
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(vertical = NemlaTheme.spacing.xs),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(NemlaTheme.spacing.md),
        ) {
            RecordButton(phase = state.phase, onClick = { onAction(VoiceCaptureAction.RecordButtonClicked) })
            Text(
                text =
                    stringResource(
                        when (state.phase) {
                            CapturePhase.Idle -> Res.string.voice_status_idle
                            is CapturePhase.Recording -> Res.string.voice_status_recording
                            CapturePhase.Processing -> Res.string.voice_status_processing
                        },
                    ),
                style = NemlaTheme.typography.body,
                color = NemlaTheme.colors.mutedForeground,
                textAlign = TextAlign.Center,
            )
            val phase = state.phase
            if (phase is CapturePhase.Recording) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(NemlaTheme.spacing.xs),
                ) {
                    NemlaProgressBar(progress = phase.elapsedSeconds.toFloat() / phase.maxSeconds)
                    Text(
                        text = stringResource(Res.string.voice_elapsed, phase.elapsedSeconds, phase.maxSeconds),
                        style = NemlaTheme.typography.caption.copy(fontFeatureSettings = "tnum"),
                        color = NemlaTheme.colors.mutedForeground,
                    )
                }
            }
            state.dialogMessage?.let { message ->
                Text(
                    text = message.text(),
                    style = NemlaTheme.typography.body,
                    color = NemlaTheme.colors.destructive,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

@Composable
private fun RecordButton(
    phase: CapturePhase,
    onClick: () -> Unit,
) {
    val colors = NemlaTheme.colors
    val recording = phase is CapturePhase.Recording
    val processing = phase == CapturePhase.Processing
    val description = stringResource(if (recording) Res.string.voice_stop_recording else Res.string.voice_start_recording)
    Surface(
        onClick = onClick,
        enabled = !processing,
        shape = CircleShape,
        color = if (recording) colors.destructive else colors.primary,
        contentColor = if (recording) colors.destructiveForeground else colors.primaryForeground,
        modifier = Modifier.size(RECORD_BUTTON_SIZE).semantics { contentDescription = description },
    ) {
        Box(contentAlignment = Alignment.Center) {
            when {
                recording -> Icon(NemlaIcons.Square, contentDescription = null, modifier = Modifier.size(24.dp))
                processing -> Spinner()
                else -> Icon(NemlaIcons.Mic, contentDescription = null, modifier = Modifier.size(28.dp))
            }
        }
    }
}

private val RECORD_BUTTON_SIZE = 80.dp

@Composable
private fun Spinner() {
    val transition = rememberInfiniteTransition()
    val angle by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(SPINNER_PERIOD_MILLIS, easing = LinearEasing), RepeatMode.Restart),
    )
    Icon(NemlaIcons.Loader, contentDescription = null, modifier = Modifier.size(28.dp).rotate(angle))
}

@Composable
private fun ConsentDialog(onAction: (VoiceCaptureAction) -> Unit) {
    AiConsentDialog(
        onAccept = { onAction(VoiceCaptureAction.ConsentAccepted) },
        onDecline = { onAction(VoiceCaptureAction.ConsentDeclined) },
    )
}

@Preview
@Composable
private fun VoiceCaptureIdlePreview() {
    NemlaTheme {
        VoiceCaptureContent(state = VoiceCaptureUiState(quota = QuotaStatus.Available(2)), onAction = {})
    }
}

@Preview
@Composable
private fun VoiceCaptureRecordingPreview() {
    NemlaTheme {
        VoiceCaptureContent(
            state = VoiceCaptureUiState(isDialogOpen = true, phase = CapturePhase.Recording(12, 30)),
            onAction = {},
        )
    }
}
