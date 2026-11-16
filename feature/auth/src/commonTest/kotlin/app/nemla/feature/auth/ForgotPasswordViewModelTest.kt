package app.nemla.feature.auth

import app.cash.turbine.test
import app.nemla.core.common.AppError
import app.nemla.core.common.AppResult
import app.nemla.core.common.ValidationCode
import app.nemla.core.domain.usecase.RequestPasswordReset
import app.nemla.feature.auth.common.AuthMessage
import app.nemla.feature.auth.forgot.ForgotPasswordAction
import app.nemla.feature.auth.forgot.ForgotPasswordEffect
import app.nemla.feature.auth.forgot.ForgotPasswordViewModel
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ForgotPasswordViewModelTest : MainDispatcherTest() {
    private val auth = FakeAuthRepository()

    private fun viewModel() = ForgotPasswordViewModel(RequestPasswordReset(auth))

    @Test
    fun invalidEmailIsRejectedLocally() {
        val vm = viewModel()
        vm.onAction(ForgotPasswordAction.Submit)
        assertEquals(ValidationCode.EMAIL_REQUIRED, vm.state.value.emailError)
        vm.onAction(ForgotPasswordAction.EmailChanged("lea"))
        vm.onAction(ForgotPasswordAction.Submit)
        assertEquals(ValidationCode.EMAIL_INVALID, vm.state.value.emailError)
        assertTrue(auth.resetCalls.isEmpty())
    }

    @Test
    fun successEmitsLinkSent() =
        runTest {
            val vm = viewModel()
            vm.effects.test {
                vm.onAction(ForgotPasswordAction.EmailChanged("  $VALID_EMAIL "))
                vm.onAction(ForgotPasswordAction.Submit)
                assertEquals(ForgotPasswordEffect.LinkSent, awaitItem())
            }
            assertEquals(VALID_EMAIL, auth.resetCalls.single().value)
        }

    @Test
    fun failureShowsATranslatedBanner() {
        auth.resetResult = AppResult.Failure(AppError.RateLimited)
        val vm = viewModel()
        vm.onAction(ForgotPasswordAction.EmailChanged(VALID_EMAIL))
        vm.onAction(ForgotPasswordAction.Submit)
        assertEquals(
            AuthMessage.RATE_LIMITED,
            vm.state.value.banner
                ?.message,
        )
        assertFalse(vm.state.value.isSubmitting)
    }

    @Test
    fun doubleSubmissionIsIgnoredWhileLoading() {
        auth.gate = CompletableDeferred()
        val vm = viewModel()
        vm.onAction(ForgotPasswordAction.EmailChanged(VALID_EMAIL))
        vm.onAction(ForgotPasswordAction.Submit)
        vm.onAction(ForgotPasswordAction.Submit)
        assertEquals(1, auth.resetCalls.size)
        assertTrue(vm.state.value.isSubmitting)
    }

    @Test
    fun signUpLinkOpensSignUp() =
        runTest {
            val vm = viewModel()
            vm.effects.test {
                vm.onAction(ForgotPasswordAction.SignUpClicked)
                assertEquals(ForgotPasswordEffect.OpenSignUp, awaitItem())
            }
        }
}
