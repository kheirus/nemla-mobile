package app.nemla.core.data

import app.nemla.ai.api.AiCapability
import app.nemla.ai.api.AiRoutingContext
import app.nemla.ai.api.Transcript
import app.nemla.core.common.AppError
import app.nemla.core.common.AppResult
import app.nemla.core.common.NoOpLogger
import app.nemla.core.common.getOrNull
import app.nemla.core.data.ai.RoutedSpeechTranscriber
import app.nemla.core.data.preferences.SecureStorePreferencesRepository
import app.nemla.core.data.remote.PREFER_HEADER
import app.nemla.core.domain.model.AppLanguage
import app.nemla.core.domain.model.AppPreferences
import app.nemla.core.domain.model.CategoryId
import app.nemla.core.domain.model.ChargeFrequency
import app.nemla.core.domain.model.Currency
import app.nemla.core.domain.model.DebitSettings
import app.nemla.core.domain.model.DeferredDebit
import app.nemla.core.domain.model.ExpenseQuery
import app.nemla.core.domain.model.RecurringChargeDraft
import app.nemla.core.domain.model.RecurringChargeId
import app.nemla.core.domain.model.ThemeMode
import app.nemla.core.domain.model.VoiceRecording
import app.nemla.core.security.InMemorySecureStore
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SettingsRepositoriesTest {
    @Test
    fun recurringChargesAreInsertedInOneBatchAndDeletedForTheUser() =
        runTest {
            val harness = DataHarness(store = signedInStore()) { noContent() }
            val drafts =
                listOf(
                    RecurringChargeDraft("Netflix", 1_549, ChargeFrequency.MONTHLY, 5, CategoryId("cat-1")),
                    RecurringChargeDraft("Assurance", 36_000, ChargeFrequency.YEARLY, 1, null),
                )
            assertEquals(AppResult.Success(Unit), harness.recurring.addAll(drafts))
            val insert = harness.requests.single()
            assertEquals("POST", insert.method)
            assertEquals("/rest/v1/recurring_expenses", insert.path)
            assertEquals(
                """[{"user_id":"$USER_ID","category_id":"cat-1","nom":"Netflix","montant":15.49,"frequence":"mensuelle",""" +
                    """"jour_prelevement":5},{"user_id":"$USER_ID","category_id":null,"nom":"Assurance","montant":360.00,""" +
                    """"frequence":"annuelle","jour_prelevement":1}]""",
                insert.body,
            )

            harness.recurring.delete(RecurringChargeId("rec-1"))
            val delete = harness.requests.last()
            assertEquals("DELETE", delete.method)
            assertEquals("eq.rec-1", delete.query("id"))
            assertEquals("eq.$USER_ID", delete.query("user_id"))

            harness.recurring.addAll(emptyList())
            assertEquals(2, harness.requests.size)
        }

    @Test
    fun blankAccountCheckReadsASingleExpenseWithoutLabels() =
        runTest {
            val harness = DataHarness(store = signedInStore()) { json("[]") }
            assertEquals(AppResult.Success(emptyList()), harness.expenses.list(ExpenseQuery(limit = 1)))
            val request = harness.requests.single()
            assertEquals("/rest/v1/expenses", request.path)
            assertEquals("1", request.query("limit"))
            assertTrue(request.query("select").orEmpty().contains("montant"))
            assertTrue(!request.query("select").orEmpty().contains("description"))
        }

    @Test
    fun onboardingUpsertsTheBudgetAndTheFlag() =
        runTest {
            val harness = DataHarness(store = signedInStore()) { noContent() }
            harness.profiles.completeOnboarding(200_000)
            val request = harness.requests.single()
            assertEquals("POST", request.method)
            assertEquals("/rest/v1/profiles", request.path)
            assertEquals("id", request.query("on_conflict"))
            assertTrue(
                request.data.headers[PREFER_HEADER]
                    .orEmpty()
                    .contains("resolution=merge-duplicates"),
            )
            assertEquals("""{"id":"$USER_ID","monthly_budget":2000.00,"onboarding_done":true}""", request.body)

            harness.profiles.updateCurrency(Currency.XOF)
            assertEquals("""{"devise":"XOF"}""", harness.requests.last().body)
            assertEquals("PATCH", harness.requests.last().method)
        }

    @Test
    fun debitSettingsAreReadThenSavedAndReplayed() =
        runTest {
            val harness =
                DataHarness(store = signedInStore()) { request ->
                    when (request.path) {
                        "/rest/v1/rpc/apply_debit_rule" -> {
                            json("4")
                        }

                        else -> {
                            if (request.method == "GET") {
                                json(
                                    """[{"banque":"sg","debit_differe":true,"debit_jour_arrete":31,"debit_jour_prelevement":3,
                                    "debit_depuis":"2026-01-15","debit_choix_fait":true}]""",
                                )
                            } else {
                                noContent()
                            }
                        }
                    }
                }
            val settings = harness.debits.settings().getOrNull()!!
            assertEquals(DebitSettings("sg", DeferredDebit(true, 31, 3, LocalDate(2026, 1, 15)), true), settings)
            assertTrue(
                harness.requests
                    .single()
                    .query("select")
                    .orEmpty()
                    .contains("debit_choix_fait"),
            )

            val moved = harness.debits.save("bnp", DeferredDebit(true, 31, 5, null))
            assertEquals(AppResult.Success(4), moved)
            val patch = harness.on("PATCH", "/rest/v1/profiles").single()
            assertEquals(
                """{"banque":"bnp","debit_differe":true,"debit_jour_arrete":31,"debit_jour_prelevement":5,""" +
                    """"debit_depuis":null,"debit_choix_fait":true}""",
                patch.body,
            )
            assertEquals(1, harness.on("POST", "/rest/v1/rpc/apply_debit_rule").size)
        }

    @Test
    fun debitSaveStopsWhenTheProfileUpdateFails() =
        runTest {
            val harness = DataHarness(store = signedInStore()) { json("""{"message":"boom"}""", HttpStatusCode.InternalServerError) }
            assertTrue(harness.debits.save(null, DeferredDebit.Immediate) is AppResult.Failure)
            assertTrue(harness.on("POST", "/rest/v1/rpc/apply_debit_rule").isEmpty())
        }

    @Test
    fun transcriptionUsesTheSpeechCapabilityWithTheDictationLanguage() =
        runTest {
            val engine = FakeEngine(AiCapability.SpeechToText, "stt") { Transcript("  loyer 750  ", "fr") }
            val transcriber = RoutedSpeechTranscriber(router(engine))
            val text = transcriber.transcribe(VoiceRecording(ByteArray(16), 16_000, 1_000), AppLanguage.EN)
            assertEquals(AppResult.Success("loyer 750"), text)
            assertEquals("en", engine.inputs.single().languageTag)

            val withoutConsent = FixedContextSource(AiRoutingContext(online = true, remainingCredits = 10, remoteConsent = emptySet()))
            val noConsent = RoutedSpeechTranscriber(router(engine, context = withoutConsent))
            assertEquals(
                AppError.ConsentRequired,
                noConsent.transcribe(VoiceRecording(ByteArray(16), 16_000, 1_000), AppLanguage.FR).failure,
            )
        }

    @Test
    fun preferencesArePersistedAndSurviveCorruption() =
        runTest {
            val store = InMemorySecureStore()
            val repository = SecureStorePreferencesRepository(store, NoOpLogger)
            assertNull(repository.preferences.value)
            assertEquals(AppPreferences.Default, repository.load())
            val chosen = AppPreferences(AppLanguage.EN, AppLanguage.FR, ThemeMode.DARK)
            assertEquals(AppResult.Success(Unit), repository.update(chosen))
            assertEquals(chosen, SecureStorePreferencesRepository(store, NoOpLogger).load())

            store.put(SecureStorePreferencesRepository.KEY, "{not json")
            assertEquals(AppPreferences.Default, SecureStorePreferencesRepository(store, NoOpLogger).load())
        }
}
