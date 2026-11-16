package app.nemla.feature.home

internal data class HomeUiState(
    val pseudo: String? = null,
    val isSigningOut: Boolean = false,
    val signOutFailed: Boolean = false,
) {
    override fun toString(): String = "HomeUiState(hasPseudo=${pseudo != null}, isSigningOut=$isSigningOut, signOutFailed=$signOutFailed)"
}

internal sealed interface HomeAction {
    data object SignOutClicked : HomeAction
}

internal sealed interface HomeEffect {
    data object SignedOut : HomeEffect
}
