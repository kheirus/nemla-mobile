package app.nemla.feature.auth

import app.nemla.core.domain.usecase.RequestPasswordReset
import app.nemla.core.domain.usecase.SignIn
import app.nemla.core.domain.usecase.SignUp
import app.nemla.core.domain.usecase.ValidateCredentials
import app.nemla.feature.auth.forgot.ForgotPasswordViewModel
import app.nemla.feature.auth.signin.SignInViewModel
import app.nemla.feature.auth.signup.SignUpViewModel
import org.koin.core.module.Module
import org.koin.core.module.dsl.viewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

val featureAuthModule: Module =
    module {
        factory { ValidateCredentials() }
        factory { SignIn(get()) }
        factory { SignUp(get()) }
        factory { RequestPasswordReset(get()) }
        viewModel { params ->
            SignInViewModel(
                signIn = get(),
                validateCredentials = get(),
                resetLinkSent = params.getOrNull<Boolean>() ?: false,
            )
        }
        viewModelOf(::SignUpViewModel)
        viewModelOf(::ForgotPasswordViewModel)
    }
