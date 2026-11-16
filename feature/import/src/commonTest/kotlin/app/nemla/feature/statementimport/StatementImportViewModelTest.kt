package app.nemla.feature.statementimport

import app.cash.turbine.test
import app.nemla.core.common.AppError
import app.nemla.core.common.AppResult
import app.nemla.core.common.ValidationCode
import app.nemla.core.documents.DocumentError
import app.nemla.core.documents.DocumentKind
import app.nemla.core.documents.DocumentReadResult
import app.nemla.core.documents.DocumentText
import app.nemla.core.documents.DocumentTextReader
import app.nemla.core.domain.model.AiConsentLevel
import app.nemla.core.domain.model.AiConsents
import app.nemla.core.domain.model.AppLanguage
import app.nemla.core.domain.model.ReviewOrigin
import app.nemla.core.domain.model.StatementFormat
import app.nemla.core.domain.model.StatementImport
import app.nemla.core.domain.port.AiConsentRepository
import app.nemla.core.domain.port.DeviceLanguage
import app.nemla.core.domain.port.PreferencesRepository
import app.nemla.core.domain.port.QuotaRepository
import app.nemla.core.domain.port.StatementImporter
import app.nemla.core.domain.usecase.CurrentLanguages
import app.nemla.core.domain.usecase.GetMonthlyQuota
import app.nemla.core.domain.usecase.GrantAiConsent
import app.nemla.core.domain.usecase.ImportStatement
import app.nemla.core.domain.usecase.ObserveAiConsents
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.runTest
import org.koin.dsl.koinApplication
import org.koin.dsl.module
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class StatementImportViewModelTest : MainDispatcherTest() {
    private val reader = FakeReader()
    private val importer = FakeImporter()
    private val quotas = FakeQuotas()
    private val consents = FakeConsents(setOf(AiConsentLevel.ANONYMIZED))

    private fun viewModel(): StatementImportViewModel =
        StatementImportViewModel(
            reader = reader,
            importStatement = ImportStatement(importer, quotas),
            getQuota = GetMonthlyQuota(quotas),
            observeConsents = ObserveAiConsents(consents),
            grantConsent = GrantAiConsent(consents),
            languages = CurrentLanguages(FakePreferences(AppLanguage.EN)) { AppLanguage.FR },
        )

    private fun StatementImportViewModel.pickAndRead(document: FakeDocument = FakeDocument()) {
        onAction(StatementImportAction.FilePicked(document))
        onAction(StatementImportAction.ReadClicked)
    }

    @Test
    fun remainingImportsAreShownAndReadingNeedsAFile() =
        runTest {
            val vm = viewModel()
            assertEquals(ImportQuota.Available(1), vm.state.value.quota)
            assertFalse(vm.state.value.canRead)
            vm.onAction(StatementImportAction.ReadClicked)
            assertTrue(reader.reads.isEmpty())
            vm.onAction(StatementImportAction.FilePicked(FakeDocument("releve-mars.csv")))
            assertEquals("releve-mars.csv", vm.state.value.fileName)
            assertTrue(vm.state.value.canRead)
            vm.onAction(StatementImportAction.FilePicked(null))
            assertEquals("releve-mars.csv", vm.state.value.fileName)
        }

    @Test
    fun usedImportDisablesReading() =
        runTest {
            quotas.importUsed = 1
            val vm = viewModel()
            assertEquals(ImportQuota.Exhausted, vm.state.value.quota)
            vm.pickAndRead()
            assertFalse(vm.state.value.canRead)
            assertTrue(reader.reads.isEmpty())
            assertTrue(importer.calls.isEmpty())
        }

    @Test
    fun successfulImportOpensTheReviewWithTheImportedExpenses() =
        runTest {
            val vm = viewModel()
            vm.effects.test {
                vm.pickAndRead()
                val effect = assertIs<StatementImportEffect.Imported>(awaitItem())
                assertEquals(ReviewOrigin.IMPORT, effect.review.origin)
                assertTrue(effect.review.announce)
                assertEquals(listOf("e-1", "e-2"), effect.review.drafts.map { it.id?.value })
            }
            val (statement, language) = importer.calls.single()
            assertEquals(StatementFormat.CSV, statement.format)
            assertEquals(SYNTHETIC_STATEMENT, statement.text)
            assertEquals(AppLanguage.EN, language)
            assertNull(vm.state.value.fileName)
            assertEquals(ImportPhase.Idle, vm.state.value.phase)
        }

    @Test
    fun processingShowsTheRedactionCountAndBlocksClosing() =
        runTest {
            val gate = CompletableDeferred<Unit>()
            importer.gate = gate
            importer.redactions = 7
            val vm = viewModel()
            vm.pickAndRead()
            assertEquals(ImportPhase.Parsing, vm.state.value.phase)
            assertEquals(7, vm.state.value.redactions)
            assertFalse(vm.state.value.isDismissible)
            assertFalse(vm.state.value.canPick)
            vm.effects.test {
                vm.onAction(StatementImportAction.Dismissed)
                vm.onAction(StatementImportAction.ReadClicked)
                expectNoEvents()
                gate.complete(Unit)
                assertIs<StatementImportEffect.Imported>(awaitItem())
            }
            assertEquals(1, importer.calls.size)
        }

    @Test
    fun missingEndpointExplainsThatNothingWasSent() =
        runTest {
            importer.results += AppResult.Failure(AppError.NotSupportedYet)
            val vm = viewModel()
            vm.pickAndRead()
            assertEquals(ImportMessage.NOT_SUPPORTED_YET, vm.state.value.message)
            assertTrue(ImportMessage.NOT_SUPPORTED_YET.isInformation)
            assertEquals(5, vm.state.value.redactions)
            assertEquals(ImportPhase.Idle, vm.state.value.phase)
            assertEquals(ImportQuota.Available(1), vm.state.value.quota)
        }

    @Test
    fun serverAndDocumentErrorsAreTranslated() =
        runTest {
            val serverCases =
                mapOf(
                    AppError.QuotaExceeded to ImportMessage.QUOTA_REACHED,
                    AppError.Validation(ValidationCode.PAYLOAD_TOO_LARGE) to ImportMessage.TOO_LARGE,
                    AppError.Validation(ValidationCode.NO_EXPENSE_DETECTED) to ImportMessage.NO_EXPENSE,
                    AppError.Validation(ValidationCode.PERIOD_TOO_LONG) to ImportMessage.PERIOD_TOO_LONG,
                    AppError.Validation(ValidationCode.UNREADABLE_FILE) to ImportMessage.UNREADABLE,
                    AppError.Validation(ValidationCode.NO_CATEGORY) to ImportMessage.NO_CATEGORY,
                    AppError.Network.Offline to ImportMessage.OFFLINE,
                    AppError.Server(500) to ImportMessage.FAILED,
                )
            for ((error, message) in serverCases) {
                importer.results += AppResult.Failure(error)
                val vm = viewModel()
                vm.pickAndRead()
                assertEquals(message, vm.state.value.message, error.toString())
                assertNull(vm.state.value.redactions)
            }
            val documentCases =
                mapOf(
                    DocumentError.TOO_LARGE to ImportMessage.TOO_LARGE,
                    DocumentError.SPREADSHEET_UNSUPPORTED to ImportMessage.SPREADSHEET,
                    DocumentError.PDF_UNSUPPORTED_ON_DEVICE to ImportMessage.PDF_UNSUPPORTED,
                    DocumentError.EMPTY_OR_SCANNED to ImportMessage.UNREADABLE,
                    DocumentError.UNREADABLE to ImportMessage.OPEN_FAILED,
                )
            val callsBefore = importer.calls.size
            for ((error, message) in documentCases) {
                reader.result = DocumentReadResult.Failure(error)
                val vm = viewModel()
                vm.pickAndRead()
                assertEquals(message, vm.state.value.message, error.name)
            }
            assertEquals(callsBefore, importer.calls.size)
        }

    @Test
    fun emptyResultIsReportedAsNoExpense() =
        runTest {
            importer.results += AppResult.Success(StatementImport(listOf(draft(null)), 2))
            val vm = viewModel()
            vm.effects.test {
                vm.pickAndRead()
                expectNoEvents()
            }
            assertEquals(ImportMessage.NO_EXPENSE, vm.state.value.message)
        }

    @Test
    fun consentIsAskedBeforeReadingAndGrantedAtTheAnonymizedLevel() =
        runTest {
            consents.state.value = AiConsents.None
            val vm = viewModel()
            vm.pickAndRead()
            assertTrue(vm.state.value.isConsentPromptVisible)
            assertTrue(reader.reads.isEmpty())
            vm.effects.test {
                vm.onAction(StatementImportAction.ConsentAccepted)
                assertIs<StatementImportEffect.Imported>(awaitItem())
            }
            assertEquals(listOf(AiConsentLevel.ANONYMIZED), consents.grants)
            assertFalse(vm.state.value.isConsentPromptVisible)
        }

    @Test
    fun personalConsentAlsoCoversStatements() =
        runTest {
            consents.state.value = AiConsents.None
            consents.grant(AiConsentLevel.PERSONAL)
            consents.grants.clear()
            val vm = viewModel()
            vm.pickAndRead()
            assertFalse(vm.state.value.isConsentPromptVisible)
            assertEquals(1, importer.calls.size)
            assertTrue(consents.grants.isEmpty())
        }

    @Test
    fun decliningConsentSendsNothing() =
        runTest {
            consents.state.value = AiConsents.None
            val vm = viewModel()
            vm.pickAndRead()
            vm.onAction(StatementImportAction.ConsentDeclined)
            assertEquals(ImportMessage.CONSENT_DECLINED, vm.state.value.message)
            assertTrue(importer.calls.isEmpty())
            assertTrue(reader.reads.isEmpty())
        }

    @Test
    fun consentRequiredByTheRouterAsksAgainAndReusesTheExtractedText() =
        runTest {
            importer.results += AppResult.Failure(AppError.ConsentRequired)
            val vm = viewModel()
            vm.pickAndRead()
            assertTrue(vm.state.value.isConsentPromptVisible)
            assertEquals(ImportPhase.Idle, vm.state.value.phase)
            vm.effects.test {
                vm.onAction(StatementImportAction.ConsentAccepted)
                assertIs<StatementImportEffect.Imported>(awaitItem())
            }
            assertEquals(1, reader.reads.size)
            assertEquals(2, importer.calls.size)
        }

    @Test
    fun pdfTextIsSentAsPdf() =
        runTest {
            reader.result = DocumentReadResult.Success(DocumentText(SYNTHETIC_STATEMENT, DocumentKind.PDF))
            val vm = viewModel()
            vm.pickAndRead(FakeDocument("releve.pdf"))
            assertEquals(
                StatementFormat.PDF,
                importer.calls
                    .single()
                    .first.format,
            )
        }

    @Test
    fun dismissingResetsTheSelection() =
        runTest {
            val vm = viewModel()
            vm.onAction(StatementImportAction.FilePicked(FakeDocument()))
            vm.effects.test {
                vm.onAction(StatementImportAction.Dismissed)
                assertEquals(StatementImportEffect.Close, awaitItem())
            }
            assertNull(vm.state.value.fileName)
            assertNull(vm.state.value.message)
        }

    @Test
    fun unknownQuotaStillAllowsReadingAndTheServerDecides() =
        runTest {
            quotas.failing = true
            val vm = viewModel()
            assertEquals(ImportQuota.Unknown, vm.state.value.quota)
            vm.pickAndRead()
            assertEquals(1, importer.calls.size)
        }

    @Test
    fun stateNeverPrintsTheFileName() {
        val state = StatementImportUiState(fileName = "releve-camille.csv")
        assertFalse(state.toString().contains("camille"))
    }

    @Test
    fun koinModuleBuildsTheViewModel() {
        val koin =
            koinApplication {
                modules(
                    module {
                        single<DocumentTextReader> { reader }
                        single<StatementImporter> { importer }
                        single<QuotaRepository> { quotas }
                        single<AiConsentRepository> { consents }
                        single<PreferencesRepository> { FakePreferences() }
                        single<DeviceLanguage> { DeviceLanguage { AppLanguage.FR } }
                    },
                    featureImportModule,
                )
            }.koin
        assertIs<StatementImportViewModel>(koin.get<StatementImportViewModel>())
    }
}
