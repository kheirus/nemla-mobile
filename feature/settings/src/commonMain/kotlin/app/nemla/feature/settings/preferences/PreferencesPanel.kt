package app.nemla.feature.settings.preferences

import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.nemla.core.designsystem.component.NemlaButton
import app.nemla.core.designsystem.component.NemlaButtonVariant
import app.nemla.core.designsystem.component.NemlaChoiceCard
import app.nemla.core.designsystem.component.NemlaOption
import app.nemla.core.designsystem.component.NemlaSelectField
import app.nemla.core.designsystem.icon.NemlaIcons
import app.nemla.core.designsystem.theme.NemlaTheme
import app.nemla.core.domain.model.AppLanguage
import app.nemla.core.domain.model.Currency
import app.nemla.core.domain.model.ThemeMode
import app.nemla.feature.settings.common.Caption
import app.nemla.feature.settings.common.NoticeBanner
import app.nemla.feature.settings.common.PanelCard
import app.nemla.feature.settings.resources.Res
import app.nemla.feature.settings.resources.preferences_ai_consent
import app.nemla.feature.settings.resources.preferences_ai_consent_granted
import app.nemla.feature.settings.resources.preferences_ai_consent_none
import app.nemla.feature.settings.resources.preferences_ai_consent_revoke
import app.nemla.feature.settings.resources.preferences_app_language
import app.nemla.feature.settings.resources.preferences_appearance
import app.nemla.feature.settings.resources.preferences_consent_revoked
import app.nemla.feature.settings.resources.preferences_country_aed
import app.nemla.feature.settings.resources.preferences_country_cad
import app.nemla.feature.settings.resources.preferences_country_chf
import app.nemla.feature.settings.resources.preferences_country_dzd
import app.nemla.feature.settings.resources.preferences_country_eur
import app.nemla.feature.settings.resources.preferences_country_gbp
import app.nemla.feature.settings.resources.preferences_country_mad
import app.nemla.feature.settings.resources.preferences_country_tnd
import app.nemla.feature.settings.resources.preferences_country_usd
import app.nemla.feature.settings.resources.preferences_country_xof
import app.nemla.feature.settings.resources.preferences_currency
import app.nemla.feature.settings.resources.preferences_currency_option
import app.nemla.feature.settings.resources.preferences_currency_updated
import app.nemla.feature.settings.resources.preferences_dark
import app.nemla.feature.settings.resources.preferences_dictation_english
import app.nemla.feature.settings.resources.preferences_dictation_french
import app.nemla.feature.settings.resources.preferences_dictation_language
import app.nemla.feature.settings.resources.preferences_english
import app.nemla.feature.settings.resources.preferences_failed
import app.nemla.feature.settings.resources.preferences_french
import app.nemla.feature.settings.resources.preferences_light
import app.nemla.feature.settings.resources.preferences_title
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

@Composable
internal fun PreferencesPanelRoute(viewModel: PreferencesViewModel = koinViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(viewModel) { viewModel.onAction(PreferencesAction.Opened) }
    PreferencesPanel(state = state, onAction = viewModel::onAction)
}

@Composable
internal fun PreferencesPanel(
    state: PreferencesUiState,
    onAction: (PreferencesAction) -> Unit,
) {
    val languageOptions =
        listOf(
            NemlaOption(AppLanguage.FR, stringResource(Res.string.preferences_french)),
            NemlaOption(AppLanguage.EN, stringResource(Res.string.preferences_english)),
        )
    Column(verticalArrangement = Arrangement.spacedBy(NemlaTheme.spacing.md)) {
        state.notice?.let { notice ->
            NoticeBanner(text = notice.message.text(), kind = notice.kind, onDismiss = { onAction(PreferencesAction.NoticeDismissed) })
        }
        PanelCard(title = stringResource(Res.string.preferences_title)) {
            NemlaSelectField(
                label = stringResource(Res.string.preferences_currency),
                placeholder = stringResource(Res.string.preferences_currency),
                options =
                    Currency.entries.map {
                        NemlaOption(it, stringResource(Res.string.preferences_currency_option, stringResource(it.country()), it.symbol))
                    },
                selected = state.currency,
                onSelect = { onAction(PreferencesAction.CurrencySelected(it)) },
                enabled = !state.isSavingCurrency,
            )
            NemlaSelectField(
                label = stringResource(Res.string.preferences_app_language),
                placeholder = stringResource(Res.string.preferences_app_language),
                options = languageOptions,
                selected = state.appLanguage,
                onSelect = { onAction(PreferencesAction.AppLanguageSelected(it)) },
            )
            NemlaSelectField(
                label = stringResource(Res.string.preferences_dictation_language),
                placeholder = stringResource(Res.string.preferences_dictation_language),
                options = languageOptions,
                selected = state.dictationLanguage,
                onSelect = { onAction(PreferencesAction.DictationLanguageSelected(it)) },
            )
            Column(verticalArrangement = Arrangement.spacedBy(NemlaTheme.spacing.xs)) {
                Text(stringResource(Res.string.preferences_appearance), style = NemlaTheme.typography.bodyStrong)
                val dark =
                    when (state.theme) {
                        ThemeMode.DARK -> true
                        ThemeMode.LIGHT -> false
                        ThemeMode.SYSTEM -> isSystemInDarkTheme()
                    }
                Row(horizontalArrangement = Arrangement.spacedBy(NemlaTheme.spacing.xs)) {
                    NemlaChoiceCard(
                        title = stringResource(Res.string.preferences_light),
                        selected = !dark,
                        onClick = { onAction(PreferencesAction.ThemeSelected(ThemeMode.LIGHT)) },
                        icon = NemlaIcons.Sun,
                        modifier = Modifier.weight(1f),
                    )
                    NemlaChoiceCard(
                        title = stringResource(Res.string.preferences_dark),
                        selected = dark,
                        onClick = { onAction(PreferencesAction.ThemeSelected(ThemeMode.DARK)) },
                        icon = NemlaIcons.Moon,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            val shape = RoundedCornerShape(NemlaTheme.radius.xl)
            Column(
                modifier = Modifier.fillMaxWidth().border(1.dp, NemlaTheme.colors.border, shape).padding(NemlaTheme.spacing.sm),
                verticalArrangement = Arrangement.spacedBy(NemlaTheme.spacing.xs),
            ) {
                Text(stringResource(Res.string.preferences_ai_consent), style = NemlaTheme.typography.bodyStrong)
                Caption(
                    stringResource(
                        if (state.aiConsentGranted) Res.string.preferences_ai_consent_granted else Res.string.preferences_ai_consent_none,
                    ),
                )
                if (state.aiConsentGranted) {
                    NemlaButton(
                        text = stringResource(Res.string.preferences_ai_consent_revoke),
                        onClick = { onAction(PreferencesAction.RevokeConsentClicked) },
                        variant = NemlaButtonVariant.Outline,
                        leadingIcon = NemlaIcons.ShieldCheck,
                    )
                }
            }
        }
    }
}

private fun Currency.country(): StringResource =
    when (this) {
        Currency.EUR -> Res.string.preferences_country_eur
        Currency.CHF -> Res.string.preferences_country_chf
        Currency.GBP -> Res.string.preferences_country_gbp
        Currency.USD -> Res.string.preferences_country_usd
        Currency.CAD -> Res.string.preferences_country_cad
        Currency.MAD -> Res.string.preferences_country_mad
        Currency.TND -> Res.string.preferences_country_tnd
        Currency.DZD -> Res.string.preferences_country_dzd
        Currency.XOF -> Res.string.preferences_country_xof
        Currency.AED -> Res.string.preferences_country_aed
    }

@Composable
private fun PreferencesMessage.text(): String =
    when (this) {
        PreferencesMessage.CurrencyUpdated -> {
            stringResource(Res.string.preferences_currency_updated)
        }

        is PreferencesMessage.DictationLanguageSet -> {
            stringResource(
                if (language == AppLanguage.EN) Res.string.preferences_dictation_english else Res.string.preferences_dictation_french,
            )
        }

        PreferencesMessage.ConsentRevoked -> {
            stringResource(Res.string.preferences_consent_revoked)
        }

        PreferencesMessage.Failed -> {
            stringResource(Res.string.preferences_failed)
        }
    }
