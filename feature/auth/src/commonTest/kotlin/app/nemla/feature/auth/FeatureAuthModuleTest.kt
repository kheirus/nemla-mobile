package app.nemla.feature.auth

import app.nemla.core.domain.port.AuthRepository
import app.nemla.feature.auth.common.AuthMessage
import app.nemla.feature.auth.forgot.ForgotPasswordViewModel
import app.nemla.feature.auth.signin.SignInViewModel
import app.nemla.feature.auth.signup.SignUpViewModel
import org.koin.core.parameter.parametersOf
import org.koin.dsl.koinApplication
import org.koin.dsl.module
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class FeatureAuthModuleTest : MainDispatcherTest() {
    private val koin =
        koinApplication {
            modules(module { single<AuthRepository> { FakeAuthRepository() } }, featureAuthModule)
        }.koin

    @Test
    fun viewModelsResolve() {
        koin.get<SignUpViewModel>()
        koin.get<ForgotPasswordViewModel>()
        assertNull(
            koin
                .get<SignInViewModel>()
                .state.value.banner,
        )
    }

    @Test
    fun signInReceivesTheResetNotice() {
        val vm = koin.get<SignInViewModel> { parametersOf(true) }
        assertEquals(
            AuthMessage.RESET_LINK_SENT,
            vm.state.value.banner
                ?.message,
        )
    }
}
