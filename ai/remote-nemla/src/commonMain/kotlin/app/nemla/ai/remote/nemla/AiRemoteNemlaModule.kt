package app.nemla.ai.remote.nemla

import app.nemla.ai.api.AiEngine
import org.koin.core.module.Module
import org.koin.dsl.bind
import org.koin.dsl.module

val aiRemoteNemlaModule: Module =
    module {
        single { NemlaApi(client = get(), config = get()) }
        single { NemlaVoiceCaptureEngine(get()) } bind AiEngine::class
        single { NemlaTranscriptionEngine(get()) } bind AiEngine::class
        single { NemlaRecurringParsingEngine(get()) } bind AiEngine::class
        single { NemlaBudgetSuggestionEngine(get()) } bind AiEngine::class
        single { NemlaPeriodReportEngine(get()) } bind AiEngine::class
    }
