package app.nemla.feature.auth

import app.cash.turbine.test
import app.nemla.core.common.AppError
import app.nemla.core.common.AppResult
import app.nemla.core.common.ValidationCode
import app.nemla.core.domain.model.Gender
import app.nemla.core.domain.model.Household
import app.nemla.core.domain.model.SignUpOutcome
import app.nemla.core.domain.usecase.SignUp
import app.nemla.core.domain.usecase.ValidateCredentials
import app.nemla.feature.auth.common.AuthBanner
import app.nemla.feature.auth.common.AuthBannerKind
import app.nemla.feature.auth.common.AuthMessage
import app.nemla.feature.auth.signup.SignUpAction
import app.nemla.feature.auth.signup.SignUpEffect
import app.nemla.feature.auth.signup.SignUpViewModel
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SignUpViewModelTest : MainDispatcherTest() {
    private val auth = FakeAuthRepository()

    private fun viewModel() = SignUpViewModel(SignUp(auth), ValidateCredentials())

    private fun SignUpViewModel.fillCredentials() {
        onAction(SignUpAction.EmailChanged(VALID_EMAIL))
        onAction(SignUpAction.PasswordChanged(VALID_PASSWORD))
    }

    @Test
    fun optionalDetailsAreSentWithTheRequest() {
        val vm = viewModel()
        vm.fillCredentials()
        vm.onAction(SignUpAction.PseudoChanged("  Léa  "))
        vm.onAction(SignUpAction.AgeChanged("29"))
        vm.onAction(SignUpAction.GenderSelected(Gender.FEMALE))
        vm.onAction(SignUpAction.HouseholdSelected(Household.FLATSHARE))
        vm.onAction(SignUpAction.Submit)
        val details = auth.signUpCalls.single().details
        assertEquals("Léa", details.pseudo)
        assertEquals(29, details.age)
        assertEquals(Gender.FEMALE, details.gender)
        assertEquals(Household.FLATSHARE, details.household)
    }

    @Test
    fun blankOptionalDetailsAreOmitted() {
        val vm = viewModel()
        vm.fillCredentials()
        vm.onAction(SignUpAction.PseudoChanged("   "))
        vm.onAction(SignUpAction.Submit)
        val details = auth.signUpCalls.single().details
        assertNull(details.pseudo)
        assertNull(details.age)
        assertNull(details.gender)
        assertNull(details.household)
    }

    @Test
    fun ageOutsideTheAllowedRangeIsRejected() {
        val vm = viewModel()
        vm.fillCredentials()
        vm.onAction(SignUpAction.AgeChanged("13"))
        vm.onAction(SignUpAction.Submit)
        assertEquals(ValidationCode.AGE_OUT_OF_RANGE, vm.state.value.ageError)
        assertTrue(auth.signUpCalls.isEmpty())
        vm.onAction(SignUpAction.AgeChanged("121"))
        vm.onAction(SignUpAction.Submit)
        assertEquals(ValidationCode.AGE_OUT_OF_RANGE, vm.state.value.ageError)
        vm.onAction(SignUpAction.AgeChanged("14"))
        assertNull(vm.state.value.ageError)
        vm.onAction(SignUpAction.Submit)
        assertEquals(1, auth.signUpCalls.size)
    }

    @Test
    fun ageKeepsOnlyThreeDigits() {
        val vm = viewModel()
        vm.onAction(SignUpAction.AgeChanged("4a2-17"))
        assertEquals("421", vm.state.value.age)
    }

    @Test
    fun invalidCredentialsAreReportedPerField() {
        val vm = viewModel()
        vm.onAction(SignUpAction.EmailChanged("lea@"))
        vm.onAction(SignUpAction.PasswordChanged("12345"))
        vm.onAction(SignUpAction.Submit)
        assertEquals(ValidationCode.EMAIL_INVALID, vm.state.value.emailError)
        assertEquals(ValidationCode.PASSWORD_TOO_SHORT, vm.state.value.passwordError)
        assertTrue(auth.signUpCalls.isEmpty())
    }

    @Test
    fun confirmationRequiredKeepsTheUserOnScreenWithANotice() =
        runTest {
            auth.signUpResult = AppResult.Success(SignUpOutcome.ConfirmationRequired)
            val vm = viewModel()
            vm.effects.test {
                vm.fillCredentials()
                vm.onAction(SignUpAction.Submit)
                expectNoEvents()
            }
            assertEquals(AuthBanner(AuthBannerKind.Info, AuthMessage.CONFIRMATION_REQUIRED), vm.state.value.banner)
            assertEquals("", vm.state.value.password)
            assertEquals(VALID_EMAIL, vm.state.value.email)
            assertFalse(vm.state.value.isSubmitting)
        }

    @Test
    fun immediateSessionEmitsSignedUp() =
        runTest {
            auth.signUpResult = AppResult.Success(SignUpOutcome.SignedIn(testSession))
            val vm = viewModel()
            vm.effects.test {
                vm.fillCredentials()
                vm.onAction(SignUpAction.Submit)
                assertEquals(SignUpEffect.SignedUp, awaitItem())
            }
            assertEquals("", vm.state.value.password)
        }

    @Test
    fun alreadyRegisteredEmailShowsABanner() {
        auth.signUpResult = AppResult.Failure(AppError.Validation(ValidationCode.EMAIL_ALREADY_REGISTERED))
        val vm = viewModel()
        vm.fillCredentials()
        vm.onAction(SignUpAction.Submit)
        assertEquals(
            AuthMessage.EMAIL_TAKEN,
            vm.state.value.banner
                ?.message,
        )
    }

    @Test
    fun weakPasswordFromTheServerIsAFieldError() {
        auth.signUpResult = AppResult.Failure(AppError.Validation(ValidationCode.PASSWORD_TOO_WEAK))
        val vm = viewModel()
        vm.fillCredentials()
        vm.onAction(SignUpAction.Submit)
        assertEquals(ValidationCode.PASSWORD_TOO_WEAK, vm.state.value.passwordError)
        assertNull(vm.state.value.banner)
    }

    @Test
    fun doubleSubmissionIsIgnoredWhileLoading() {
        auth.gate = CompletableDeferred()
        val vm = viewModel()
        vm.fillCredentials()
        vm.onAction(SignUpAction.Submit)
        vm.onAction(SignUpAction.Submit)
        assertTrue(vm.state.value.isSubmitting)
        assertEquals(1, auth.signUpCalls.size)
        auth.gate?.complete(Unit)
        assertFalse(vm.state.value.isSubmitting)
    }

    @Test
    fun signInLinkOpensSignIn() =
        runTest {
            val vm = viewModel()
            vm.effects.test {
                vm.onAction(SignUpAction.SignInClicked)
                assertEquals(SignUpEffect.OpenSignIn, awaitItem())
            }
        }
}
