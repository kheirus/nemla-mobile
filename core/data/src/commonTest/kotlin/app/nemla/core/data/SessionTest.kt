package app.nemla.core.data

import app.cash.turbine.test
import app.nemla.core.common.AppError
import app.nemla.core.common.AppResult
import app.nemla.core.common.ValidationCode
import app.nemla.core.common.getOrNull
import app.nemla.core.data.consent.SecureStoreAiConsentRepository
import app.nemla.core.data.session.SessionState
import app.nemla.core.data.session.SessionStorage
import app.nemla.core.data.session.StoredSession
import app.nemla.core.domain.model.AiConsentLevel
import app.nemla.core.domain.model.AuthState
import app.nemla.core.domain.model.EmailAddress
import app.nemla.core.domain.model.Gender
import app.nemla.core.domain.model.Household
import app.nemla.core.domain.model.Password
import app.nemla.core.domain.model.SignUpDetails
import app.nemla.core.domain.model.SignUpOutcome
import app.nemla.core.domain.model.SignUpRequest
import app.nemla.core.domain.model.UserId
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
import kotlinx.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

class SessionTest {
    private val email = EmailAddress.parse("jane@example.com").getOrNull()!!
    private val password = Password.parse("s3cret-pass").getOrNull()!!

    @Test
    fun signInStoresTheSessionAndSignsIn() =
        runTest {
            val harness = DataHarness { json(sessionResponse()) }
            harness.auth.observeAuthState().test {
                assertEquals(AuthState.SignedOut, awaitItem())
                val session = assertIs<AppResult.Success<*>>(harness.auth.signIn(email, password))
                val signedIn = assertIs<AuthState.SignedIn>(awaitItem())
                assertEquals(UserId(USER_ID), signedIn.session.userId)
                assertEquals(email, signedIn.session.email)
                assertEquals(Instant.fromEpochSeconds(now.epochSeconds + 3_600), signedIn.session.expiresAt)
                assertEquals(signedIn.session, session.value)
            }
            val request = harness.requests.single()
            assertEquals("/auth/v1/token", request.path)
            assertEquals("password", request.query("grant_type"))
            assertEquals("""{"email":"jane@example.com","password":"s3cret-pass"}""", request.body)
            assertNull(request.authorization)
            assertEquals(PUBLISHABLE_KEY, request.data.headers["apikey"])
            assertTrue(
                harness.store
                    .snapshot()[SessionStorage.KEY]
                    .orEmpty()
                    .contains("refresh-2"),
            )
        }

    @Test
    fun invalidCredentialsStoreNothing() =
        runTest {
            val harness =
                DataHarness {
                    json(
                        """{"error_code":"invalid_credentials","msg":"Invalid login credentials"}""",
                        HttpStatusCode.BadRequest,
                    )
                }
            assertEquals(AppError.Validation(ValidationCode.INVALID_CREDENTIALS), harness.auth.signIn(email, password).failure)
            assertTrue(harness.store.snapshot().isEmpty())
            assertEquals(SessionState.SignedOut, harness.sessions.restore())
        }

    @Test
    fun sessionIsRestoredAtStartup() =
        runTest {
            val harness = DataHarness(store = signedInStore()) { json("{}") }
            harness.auth.observeAuthState().test {
                val signedIn = assertIs<AuthState.SignedIn>(awaitItem())
                assertEquals(UserId(USER_ID), signedIn.session.userId)
            }
            assertTrue(harness.requests.isEmpty())
        }

    @Test
    fun corruptedStoredSessionIsDiscarded() =
        runTest {
            val harness = DataHarness(store = signedInStore("{not json")) { json("{}") }
            harness.auth.observeAuthState().test { assertEquals(AuthState.SignedOut, awaitItem()) }
            assertTrue(harness.store.snapshot().isEmpty())
        }

    @Test
    fun rejectedTokenIsRefreshedAndRetried() =
        runTest {
            var profileCalls = 0
            val harness =
                DataHarness(store = signedInStore()) { request ->
                    when (request.path) {
                        "/auth/v1/token" -> {
                            json(sessionResponse())
                        }

                        else -> {
                            profileCalls++
                            if (profileCalls == 1) {
                                json("""{"code":"PGRST301","message":"JWT expired"}""", HttpStatusCode.Unauthorized)
                            } else {
                                json("""[{"monthly_budget":0}]""")
                            }
                        }
                    }
                }
            assertIs<AppResult.Success<*>>(harness.profiles.getProfile())
            val refresh = harness.on("POST", "/auth/v1/token").single()
            assertEquals("refresh_token", refresh.query("grant_type"))
            assertEquals("""{"refresh_token":"refresh-1"}""", refresh.body)
            assertNull(refresh.authorization)
            assertEquals(listOf("Bearer access-1", "Bearer access-2"), harness.on("GET", "/rest/v1/profiles").map { it.authorization })
            assertTrue(
                harness.store
                    .snapshot()[SessionStorage.KEY]
                    .orEmpty()
                    .contains("refresh-2"),
            )
        }

    @Test
    fun expiringTokenIsRefreshedBeforeUse() =
        runTest {
            val store = signedInStore(storedSessionJson(expiresAt = now.epochSeconds + 5))
            val harness =
                DataHarness(store = store) { request ->
                    if (request.path == "/auth/v1/token") json(sessionResponse()) else json("""[{"monthly_budget":0}]""")
                }
            harness.profiles.getProfile()
            assertEquals("Bearer access-2", harness.on("GET", "/rest/v1/profiles").single().authorization)
        }

    @Test
    fun refusedRefreshSignsOutAndClearsLocalData() =
        runTest {
            val store = signedInStore()
            store.put(SecureStoreAiConsentRepository.KEY, """{"PERSONAL":1}""")
            val harness =
                DataHarness(store = store) { request ->
                    when (request.path) {
                        "/auth/v1/token" -> json("""{"error_code":"refresh_token_not_found"}""", HttpStatusCode.BadRequest)
                        else -> json("""{"code":"PGRST301"}""", HttpStatusCode.Unauthorized)
                    }
                }
            harness.auth.observeAuthState().test {
                assertIs<AuthState.SignedIn>(awaitItem())
                assertEquals(AppError.Unauthorized, harness.profiles.getProfile().failure)
                assertEquals(AuthState.SignedOut, awaitItem())
            }
            assertTrue(harness.store.snapshot().isEmpty())
            assertTrue(
                harness.consents
                    .current()
                    .getOrNull()!!
                    .grants
                    .isEmpty(),
            )
        }

    @Test
    fun offlineRefreshKeepsTheSession() =
        runTest {
            val harness =
                DataHarness(store = signedInStore()) { request ->
                    if (request.path == "/auth/v1/token") throw IOException("offline")
                    json("""{"code":"PGRST301"}""", HttpStatusCode.Unauthorized)
                }
            harness.profiles.getProfile()
            assertTrue(harness.store.snapshot().containsKey(SessionStorage.KEY))
        }

    @Test
    fun manualRefreshReturnsTheNewSession() =
        runTest {
            val harness = DataHarness(store = signedInStore()) { json(sessionResponse(access = "access-9")) }
            val session = harness.auth.refreshSession()
            assertEquals(UserId(USER_ID), session.getOrNull()?.userId)
            assertTrue(
                harness.store
                    .snapshot()[SessionStorage.KEY]
                    .orEmpty()
                    .contains("access-9"),
            )
        }

    @Test
    fun manualRefreshWhenSignedOutIsUnauthorized() =
        runTest {
            val harness = DataHarness { json("{}") }
            assertEquals(AppError.Unauthorized, harness.auth.refreshSession().failure)
            assertTrue(harness.requests.isEmpty())
        }

    @Test
    fun signOutRevokesRemotelyAndClearsEverything() =
        runTest {
            val store = signedInStore()
            val harness = DataHarness(store = store) { noContent() }
            harness.consents.grant(AiConsentLevel.PERSONAL)
            harness.auth.observeAuthState().test {
                assertIs<AuthState.SignedIn>(awaitItem())
                assertEquals(AppResult.Success(Unit), harness.auth.signOut())
                assertEquals(AuthState.SignedOut, awaitItem())
            }
            val logout = harness.on("POST", "/auth/v1/logout").single()
            assertEquals("Bearer access-1", logout.authorization)
            assertTrue(harness.store.snapshot().isEmpty())
            assertTrue(
                harness.consents
                    .current()
                    .getOrNull()!!
                    .grants
                    .isEmpty(),
            )
        }

    @Test
    fun signOutSucceedsLocallyWhenTheServerFails() =
        runTest {
            val harness = DataHarness(store = signedInStore()) { throw IOException("offline") }
            assertEquals(AppResult.Success(Unit), harness.auth.signOut())
            assertTrue(harness.store.snapshot().isEmpty())
        }

    @Test
    fun signUpSendsMetadataAndSignsInWhenASessionIsReturned() =
        runTest {
            val harness = DataHarness { json(sessionResponse()) }
            val details = SignUpDetails(pseudo = "jane", age = 34, gender = Gender.FEMALE, household = Household.FLATSHARE)
            val outcome = harness.auth.signUp(SignUpRequest(email, password, details))
            assertIs<SignUpOutcome.SignedIn>(outcome.getOrNull())
            val request = harness.requests.single()
            assertEquals("/auth/v1/signup", request.path)
            assertNull(request.authorization)
            assertEquals(
                """{"email":"jane@example.com","password":"s3cret-pass","data":{"pseudo":"jane","age":34,"sexe":"femme","situation_familiale":"colocation"}}""",
                request.body,
            )
            assertTrue(harness.store.snapshot().containsKey(SessionStorage.KEY))
        }

    @Test
    fun signUpRequiringConfirmationStoresNothing() =
        runTest {
            val harness =
                DataHarness { json("""{"id":"user-2","email":"jane@example.com","confirmation_sent_at":"2026-11-16T10:00:00Z"}""") }
            val outcome = harness.auth.signUp(SignUpRequest(email, password, SignUpDetails.Empty))
            assertEquals(SignUpOutcome.ConfirmationRequired, outcome.getOrNull())
            assertEquals("""{"email":"jane@example.com","password":"s3cret-pass","data":{}}""", harness.requests.single().body)
            assertTrue(harness.store.snapshot().isEmpty())
        }

    @Test
    fun passwordResetIsSessionless() =
        runTest {
            val harness = DataHarness(store = signedInStore()) { json("{}") }
            assertEquals(AppResult.Success(Unit), harness.auth.requestPasswordReset(email))
            val request = harness.requests.single()
            assertEquals("/auth/v1/recover", request.path)
            assertEquals("""{"email":"jane@example.com"}""", request.body)
            assertNull(request.authorization)
        }

    @Test
    fun storedSessionNeverPrintsTokens() {
        val session = StoredSession("access-secret", "refresh-secret", 1L, USER_ID, "jane@example.com")
        assertFalse(session.toString().contains("secret"))
        assertFalse(session.toString().contains("jane"))
    }
}
