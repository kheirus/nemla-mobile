package app.nemla.feature.settings.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.nemla.core.common.AppError
import app.nemla.core.common.AppResult
import app.nemla.core.domain.model.Currency
import app.nemla.core.domain.model.Gender
import app.nemla.core.domain.model.Household
import app.nemla.core.domain.model.ProfileIdentity
import app.nemla.core.domain.usecase.GetUserProfile
import app.nemla.core.domain.usecase.ProfileSaveOutcome
import app.nemla.core.domain.usecase.SaveProfileIdentity
import app.nemla.core.domain.validation.CredentialRules
import app.nemla.feature.settings.common.Notice
import app.nemla.feature.settings.common.error
import app.nemla.feature.settings.common.success
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

internal enum class ProfileMessage { SAVED, PSEUDO_TAKEN, AGE_INVALID, SAVE_FAILED }

internal class ProfileFields(
    val pseudo: String = "",
    val ageText: String = "",
    val gender: Gender? = null,
    val household: Household? = null,
) {
    fun copy(
        pseudo: String = this.pseudo,
        ageText: String = this.ageText,
        gender: Gender? = this.gender,
        household: Household? = this.household,
    ) = ProfileFields(pseudo, ageText, gender, household)

    override fun equals(other: Any?): Boolean =
        other is ProfileFields && other.pseudo == pseudo && other.ageText == ageText && other.gender == gender &&
            other.household == household

    override fun hashCode(): Int = listOf(pseudo, ageText, gender, household).hashCode()

    override fun toString(): String = "ProfileFields(***)"
}

internal data class ProfileUiState(
    val saved: ProfileFields = ProfileFields(),
    val form: ProfileFields = ProfileFields(),
    val currency: Currency = Currency.Default,
    val isEditing: Boolean = false,
    val isSaving: Boolean = false,
    val suggestions: List<String> = emptyList(),
    val notice: Notice<ProfileMessage>? = null,
) {
    override fun toString(): String = "ProfileUiState(isEditing=$isEditing, isSaving=$isSaving)"
}

internal sealed interface ProfileAction {
    data object Opened : ProfileAction

    data object EditClicked : ProfileAction

    data object DoneClicked : ProfileAction

    data class PseudoChanged(
        val text: String,
    ) : ProfileAction {
        override fun toString(): String = "PseudoChanged(***)"
    }

    data class AgeChanged(
        val text: String,
    ) : ProfileAction {
        override fun toString(): String = "AgeChanged(***)"
    }

    data class GenderSelected(
        val gender: Gender,
    ) : ProfileAction {
        override fun toString(): String = "GenderSelected(***)"
    }

    data class HouseholdSelected(
        val household: Household,
    ) : ProfileAction {
        override fun toString(): String = "HouseholdSelected(***)"
    }

    data class SuggestionPicked(
        val pseudo: String,
    ) : ProfileAction {
        override fun toString(): String = "SuggestionPicked(***)"
    }

    data object SaveClicked : ProfileAction

    data object NoticeDismissed : ProfileAction
}

internal sealed interface ProfileEffect {
    data object Saved : ProfileEffect
}

internal class ProfileViewModel(
    private val getProfile: GetUserProfile,
    private val saveIdentity: SaveProfileIdentity,
) : ViewModel() {
    private val mutableState = MutableStateFlow(ProfileUiState())
    val state: StateFlow<ProfileUiState> = mutableState.asStateFlow()

    private val effectChannel = Channel<ProfileEffect>(Channel.BUFFERED)
    val effects: Flow<ProfileEffect> = effectChannel.receiveAsFlow()

    fun onAction(action: ProfileAction) {
        when (action) {
            ProfileAction.Opened -> {
                load()
            }

            ProfileAction.EditClicked -> {
                mutableState.update { it.copy(isEditing = true, form = it.saved, suggestions = emptyList()) }
            }

            ProfileAction.DoneClicked -> {
                mutableState.update { it.copy(isEditing = false, suggestions = emptyList()) }
            }

            is ProfileAction.PseudoChanged -> {
                editForm { it.copy(pseudo = action.text.take(PSEUDO_MAX)) }
            }

            is ProfileAction.AgeChanged -> {
                if (action.text.length <= 3 &&
                    action.text.all(Char::isDigit)
                ) {
                    editForm { it.copy(ageText = action.text) }
                }
            }

            is ProfileAction.GenderSelected -> {
                editForm { it.copy(gender = action.gender) }
            }

            is ProfileAction.HouseholdSelected -> {
                editForm { it.copy(household = action.household) }
            }

            is ProfileAction.SuggestionPicked -> {
                mutableState.update { it.copy(form = it.form.copy(pseudo = action.pseudo), suggestions = emptyList()) }
            }

            ProfileAction.SaveClicked -> {
                save()
            }

            ProfileAction.NoticeDismissed -> {
                mutableState.update { it.copy(notice = null) }
            }
        }
    }

    private fun editForm(transform: (ProfileFields) -> ProfileFields) {
        mutableState.update { it.copy(form = transform(it.form)) }
    }

    private fun load() {
        viewModelScope.launch {
            val result = getProfile()
            if (result is AppResult.Success) {
                val profile = result.value
                val fields = ProfileFields(profile.pseudo.orEmpty(), profile.age?.toString().orEmpty(), profile.gender, profile.household)
                mutableState.update { it.copy(saved = fields, form = if (it.isEditing) it.form else fields, currency = profile.currency) }
            }
        }
    }

    private fun save() {
        val current = mutableState.value
        if (current.isSaving) return
        val age = current.form.ageText.toIntOrNull()
        if (CredentialRules.ageError(age) != null) {
            mutableState.update { it.copy(notice = error(ProfileMessage.AGE_INVALID)) }
            return
        }
        val identity = ProfileIdentity(current.form.pseudo, age, current.form.gender, current.form.household, current.currency)
        mutableState.update { it.copy(isSaving = true, notice = null, suggestions = emptyList()) }
        viewModelScope.launch {
            when (val result = saveIdentity(identity)) {
                is AppResult.Success -> {
                    when (val outcome = result.value) {
                        ProfileSaveOutcome.Saved -> {
                            val saved = current.form.copy(pseudo = current.form.pseudo.trim())
                            mutableState.update {
                                it.copy(
                                    isSaving = false,
                                    saved = saved,
                                    form = saved,
                                    notice = success(ProfileMessage.SAVED),
                                )
                            }
                            effectChannel.send(ProfileEffect.Saved)
                        }

                        is ProfileSaveOutcome.PseudoTaken -> {
                            mutableState.update {
                                it.copy(isSaving = false, suggestions = outcome.suggestions, notice = error(ProfileMessage.PSEUDO_TAKEN))
                            }
                        }
                    }
                }

                is AppResult.Failure -> {
                    val message = if (result.error is AppError.Validation) ProfileMessage.AGE_INVALID else ProfileMessage.SAVE_FAILED
                    mutableState.update { it.copy(isSaving = false, notice = error(message)) }
                }
            }
        }
    }

    private companion object {
        const val PSEUDO_MAX = 40
    }
}
