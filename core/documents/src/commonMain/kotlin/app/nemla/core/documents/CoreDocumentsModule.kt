package app.nemla.core.documents

import app.nemla.core.common.AppDispatchers
import org.koin.core.module.Module
import org.koin.dsl.module

val coreDocumentsModule: Module =
    module {
        single<DocumentTextReader> { DefaultDocumentTextReader(get<AppDispatchers>().io, get()) }
    }
