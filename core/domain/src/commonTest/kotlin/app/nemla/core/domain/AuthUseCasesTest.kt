package app.nemla.core.domain

import app.cash.turbine.test
import app.nemla.core.common.AppError
import app.nemla.core.common.AppResult
import app.nemla.core.common.ValidationCode
import app.nemla.core.domain.model.AuthState
import app.nemla.core.domain.model.Gender
import app.nemla.core.domain.model.Household
import app.nemla.core.domain.model.SignUpOutcome
import app.nemla.core.domain.usecase.ObserveAuthState
import app.nemla.core.domain.usecase.RequestPasswordReset
import app.nemla.core.domain.usecase.SignIn
import app.nemla.core.domain.usecase.SignOut
import app.nemla.core.domain.usecase.SignUp
import app.nemla.core.domain.usecase.SignUpForm
import app.nemla.core.domain.usecase.ValidateCredentials
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AuthUseCasesTest {
    private val auth = FakeAuthRepository()

    @Test
    fun signInDelegatesValidCredentials() =
        runTest {
            val result = SignIn(auth)(" jane@example.fr ", "secret1")
            assertEquals(AppResult.Success(sampleSession), result)
            assertEquals(
                "jane@example.fr",
                auth.signInCalls
                    .single()
                    .first.value,
            )
            assertEquals(
                "secret1",
                auth.signInCalls
                    .single()
                    .second.value,
            )
        }

    @Test
    fun signInRejectsInvalidEmailLocally() =
        runTest {
            val result = SignIn(auth)("jane", "secret1")
            assertEquals(AppResult.Failure(AppError.Validation(ValidationCode.EMAIL_INVALID)), result)
            assertTrue(auth.signInCalls.isEmpty())
        }

    @Test
    fun signInRejectsShortPasswordLocally() =
        runTest {
            val result = SignIn(auth)("jane@example.fr", "123")
            assertEquals(AppResult.Failure(AppError.Validation(ValidationCode.PASSWORD_TOO_SHORT)), result)
            assertTrue(auth.signInCalls.isEmpty())
        }

    @Test
    fun signInPropagatesRepositoryErrors() =
        runTest {
            auth.signInResult = AppResult.Failure(AppError.Validation(ValidationCode.INVALID_CREDENTIALS))
            val result = SignIn(auth)("jane@example.fr", "secret1")
            assertEquals(AppResult.Failure(AppError.Validation(ValidationCode.INVALID_CREDENTIALS)), result)
        }

    @Test
    fun signUpNormalisesDetails() =
        runTest {
            val form =
                SignUpForm(
                    email = "jane@example.fr",
                    password = "secret1",
                    pseudo = "  ",
                    age = 30,
                    gender = Gender.FEMALE,
                    household = Household.COUPLE,
                )
            val result = SignUp(auth)(form)
            assertEquals(AppResult.Success(SignUpOutcome.ConfirmationRequired), result)
            val request = auth.signUpCalls.single()
            assertEquals(null, request.details.pseudo)
            assertEquals(30, request.details.age)
            assertEquals(Gender.FEMALE, request.details.gender)
            assertEquals(Household.COUPLE, request.details.household)
        }

    @Test
    fun signUpTrimsPseudo() =
        runTest {
            SignUp(auth)(SignUpForm("jane@example.fr", "secret1", pseudo = " Jane "))
            assertEquals(
                "Jane",
                auth.signUpCalls
                    .single()
                    .details.pseudo,
            )
        }

    @Test
    fun signUpRejectsAgeOutOfRange() =
        runTest {
            val result = SignUp(auth)(SignUpForm("jane@example.fr", "secret1", age = 12))
            assertEquals(AppResult.Failure(AppError.Validation(ValidationCode.AGE_OUT_OF_RANGE)), result)
            assertTrue(auth.signUpCalls.isEmpty())
        }

    @Test
    fun signUpRejectsWeakPassword() =
        runTest {
            val result = SignUp(auth)(SignUpForm("jane@example.fr", "12345"))
            assertEquals(AppResult.Failure(AppError.Validation(ValidationCode.PASSWORD_TOO_SHORT)), result)
            assertTrue(auth.signUpCalls.isEmpty())
        }

    @Test
    fun signUpFormIsRedacted() {
        assertFalse(SignUpForm("jane@example.fr", "secret1").toString().contains("jane"))
    }

    @Test
    fun requestPasswordResetValidatesEmail() =
        runTest {
            assertEquals(
                AppResult.Failure(AppError.Validation(ValidationCode.EMAIL_REQUIRED)),
                RequestPasswordReset(auth)(" "),
            )
            assertTrue(auth.resetCalls.isEmpty())
            assertEquals(AppResult.Success(Unit), RequestPasswordReset(auth)("jane@example.fr"))
            assertEquals("jane@example.fr", auth.resetCalls.single().value)
        }

    @Test
    fun signOutDelegates() =
        runTest {
            auth.signOutResult = AppResult.Failure(AppError.Network.Offline)
            assertEquals(AppResult.Failure(AppError.Network.Offline), SignOut(auth)())
            assertEquals(1, auth.signOutCalls)
        }

    @Test
    fun observeAuthStateRelaysRepositoryStates() =
        runTest {
            ObserveAuthState(auth)().test {
                assertEquals(AuthState.Unknown, awaitItem())
                auth.state.value = AuthState.SignedIn(sampleSession)
                assertEquals(AuthState.SignedIn(sampleSession), awaitItem())
                auth.state.value = AuthState.SignedOut
                assertEquals(AuthState.SignedOut, awaitItem())
            }
        }

    @Test
    fun validateCredentialsReportsBothFields() {
        val errors = ValidateCredentials()("", "1")
        assertEquals(ValidationCode.EMAIL_REQUIRED, errors.email)
        assertEquals(ValidationCode.PASSWORD_TOO_SHORT, errors.password)
        assertFalse(errors.isValid)
        assertTrue(ValidateCredentials()("a@b.fr", "123456").isValid)
    }
}
