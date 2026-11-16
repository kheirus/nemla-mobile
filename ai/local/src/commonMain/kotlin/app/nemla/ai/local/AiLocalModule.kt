package app.nemla.ai.local

import app.nemla.ai.api.AiEngine
import org.koin.core.module.Module
import org.koin.dsl.bind
import org.koin.dsl.module

val aiLocalModule: Module =
    module {
        single { RuleBasedStatementAnonymizer() } bind AiEngine::class
    }
