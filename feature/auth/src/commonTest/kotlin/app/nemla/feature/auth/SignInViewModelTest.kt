package app.nemla.feature.auth

import app.cash.turbine.test
import app.nemla.core.common.AppError
import app.nemla.core.common.AppResult
import app.nemla.core.common.ValidationCode
import app.nemla.core.domain.usecase.SignIn
import app.nemla.core.domain.usecase.ValidateCredentials
import app.nemla.feature.auth.common.AuthBanner
import app.nemla.feature.auth.common.AuthBannerKind
import app.nemla.feature.auth.common.AuthMessage
import app.nemla.feature.auth.signin.SignInAction
import app.nemla.feature.auth.signin.SignInEffect
import app.nemla.feature.auth.signin.SignInViewModel
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SignInViewModelTest : MainDispatcherTest() {
    private val auth = FakeAuthRepository()

    private fun viewModel(resetLinkSent: Boolean = false) = SignInViewModel(SignIn(auth), ValidateCredentials(), resetLinkSent)

    private fun SignInViewModel.fill(
        email: String = VALID_EMAIL,
        password: String = VALID_PASSWORD,
    ) {
        onAction(SignInAction.EmailChanged(email))
        onAction(SignInAction.PasswordChanged(password))
    }

    @Test
    fun invalidFieldsAreReportedWithoutCallingTheBackend() {
        val vm = viewModel()
        vm.fill(email = "not-an-email", password = "123")
        vm.onAction(SignInAction.Submit)
        assertEquals(ValidationCode.EMAIL_INVALID, vm.state.value.emailError)
        assertEquals(ValidationCode.PASSWORD_TOO_SHORT, vm.state.value.passwordError)
        assertTrue(auth.signInCalls.isEmpty())
    }

    @Test
    fun emptyFieldsAreRequired() {
        val vm = viewModel()
        vm.onAction(SignInAction.Submit)
        assertEquals(ValidationCode.EMAIL_REQUIRED, vm.state.value.emailError)
        assertEquals(ValidationCode.PASSWORD_REQUIRED, vm.state.value.passwordError)
    }

    @Test
    fun editingAFieldClearsItsError() {
        val vm = viewModel()
        vm.onAction(SignInAction.Submit)
        vm.onAction(SignInAction.EmailChanged("l"))
        assertNull(vm.state.value.emailError)
        assertEquals(ValidationCode.PASSWORD_REQUIRED, vm.state.value.passwordError)
    }

    @Test
    fun successEmitsSignedInAndForgetsThePassword() =
        runTest {
            val vm = viewModel()
            vm.effects.test {
                vm.fill()
                vm.onAction(SignInAction.Submit)
                assertEquals(SignInEffect.SignedIn, awaitItem())
            }
            assertEquals(1, auth.signInCalls.size)
            assertEquals(
                VALID_EMAIL,
                auth.signInCalls
                    .single()
                    .first.value,
            )
            assertEquals("", vm.state.value.password)
            assertFalse(vm.state.value.isSubmitting)
        }

    @Test
    fun invalidCredentialsShowAnErrorBanner() {
        auth.signInResult = AppResult.Failure(AppError.Validation(ValidationCode.INVALID_CREDENTIALS))
        val vm = viewModel()
        vm.fill()
        vm.onAction(SignInAction.Submit)
        assertEquals(AuthBanner(AuthBannerKind.Error, AuthMessage.INVALID_CREDENTIALS), vm.state.value.banner)
        assertFalse(vm.state.value.isSubmitting)
        assertEquals(VALID_PASSWORD, vm.state.value.password)
    }

    @Test
    fun unconfirmedEmailIsExplained() {
        auth.signInResult = AppResult.Failure(AppError.Validation(ValidationCode.EMAIL_NOT_CONFIRMED))
        val vm = viewModel()
        vm.fill()
        vm.onAction(SignInAction.Submit)
        assertEquals(
            AuthMessage.EMAIL_NOT_CONFIRMED,
            vm.state.value.banner
                ?.message,
        )
    }

    @Test
    fun networkErrorsAreTranslated() {
        val vm = viewModel()
        vm.fill()
        auth.signInResult = AppResult.Failure(AppError.Network.Offline)
        vm.onAction(SignInAction.Submit)
        assertEquals(
            AuthMessage.OFFLINE,
            vm.state.value.banner
                ?.message,
        )
        auth.signInResult = AppResult.Failure(AppError.Server(500))
        vm.onAction(SignInAction.Submit)
        assertEquals(
            AuthMessage.GENERIC,
            vm.state.value.banner
                ?.message,
        )
    }

    @Test
    fun doubleSubmissionIsIgnoredWhileLoading() =
        runTest {
            val gate = CompletableDeferred<Unit>()
            auth.gate = gate
            val vm = viewModel()
            vm.fill()
            vm.onAction(SignInAction.Submit)
            assertTrue(vm.state.value.isSubmitting)
            vm.onAction(SignInAction.Submit)
            vm.onAction(SignInAction.SignUpClicked)
            assertEquals(1, auth.signInCalls.size)
            vm.effects.test {
                gate.complete(Unit)
                assertEquals(SignInEffect.SignedIn, awaitItem())
                expectNoEvents()
            }
        }

    @Test
    fun resetLinkNoticeIsShownOnArrival() {
        val vm = viewModel(resetLinkSent = true)
        assertEquals(AuthBanner(AuthBannerKind.Success, AuthMessage.RESET_LINK_SENT), vm.state.value.banner)
    }

    @Test
    fun navigationActionsBecomeEffects() =
        runTest {
            val vm = viewModel()
            vm.effects.test {
                vm.onAction(SignInAction.ForgotPasswordClicked)
                assertEquals(SignInEffect.OpenForgotPassword, awaitItem())
                vm.onAction(SignInAction.SignUpClicked)
                assertEquals(SignInEffect.OpenSignUp, awaitItem())
            }
        }

    @Test
    fun stateNeverPrintsCredentials() {
        val vm = viewModel()
        vm.fill()
        val printed = vm.state.value.toString()
        assertFalse(printed.contains(VALID_EMAIL))
        assertFalse(printed.contains(VALID_PASSWORD))
        assertFalse(SignInAction.PasswordChanged(VALID_PASSWORD).toString().contains(VALID_PASSWORD))
    }
}
