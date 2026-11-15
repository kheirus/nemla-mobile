package app.nemla.core.network

import app.nemla.core.common.AppError
import app.nemla.core.common.AppResult
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

class AuthPluginTest {
    @Test
    fun supabaseRequestsCarryApiKeyAndUserToken() =
        runTest {
            val harness = Harness { json("[]") }
            safeRequest<Unit> { harness.client.get("$SUPABASE/rest/v1/categories") }
            val headers = harness.requests.single().headers
            assertEquals(PUBLISHABLE_KEY, headers["apikey"])
            assertEquals("Bearer token-1", headers[HttpHeaders.Authorization])
        }

    @Test
    fun apiRequestsCarryTokenWithoutApiKey() =
        runTest {
            val harness = Harness { json("{}") }
            safeRequest<Unit> { harness.client.get("$API/api/public/budget/suggest") }
            val headers = harness.requests.single().headers
            assertNull(headers["apikey"])
            assertEquals("Bearer token-1", headers[HttpHeaders.Authorization])
        }

    @Test
    fun sessionlessRequestsOnlyCarryApiKey() =
        runTest {
            val harness = Harness { json("{}") }
            safeRequest<Unit> { harness.client.post("$SUPABASE/auth/v1/token?grant_type=password") { withoutUserSession() } }
            val headers = harness.requests.single().headers
            assertEquals(PUBLISHABLE_KEY, headers["apikey"])
            assertNull(headers[HttpHeaders.Authorization])
        }

    @Test
    fun foreignHostsNeverReceiveCredentials() =
        runTest {
            val harness = Harness { json("{}") }
            safeRequest<Unit> { harness.client.get("https://cdn.example.com/file") }
            val headers = harness.requests.single().headers
            assertNull(headers["apikey"])
            assertNull(headers[HttpHeaders.Authorization])
        }

    @Test
    fun supabase401IsRetriedOnceWithRefreshedToken() =
        runTest {
            var calls = 0
            val harness =
                Harness {
                    calls++
                    if (calls == 1) json("""{"code":"PGRST301","message":"JWT expired"}""", HttpStatusCode.Unauthorized) else json("[]")
                }
            val result = safeRequest<Unit> { harness.client.get("$SUPABASE/rest/v1/categories") }
            assertEquals(AppResult.Success(Unit), result)
            assertEquals(1, harness.store.refreshCount)
            assertEquals(listOf("Bearer token-1", "Bearer token-2"), harness.requests.map { it.headers[HttpHeaders.Authorization] })
        }

    @Test
    fun apiSessionRejectionIsRetried() =
        runTest {
            var calls = 0
            val harness =
                Harness {
                    calls++
                    if (calls == 1) json("""{"error":"Session invalide"}""", HttpStatusCode.Unauthorized) else json("{}")
                }
            assertEquals(AppResult.Success(Unit), safeRequest<Unit> { harness.client.get("$API/api/public/report/monthly") })
            assertEquals(1, harness.store.refreshCount)
            assertEquals(2, harness.requests.size)
        }

    @Test
    fun aiGateway401IsNeitherRetriedNorTreatedAsSessionExpiry() =
        runTest {
            val harness =
                Harness {
                    json(
                        """{"error":"Transcription impossible (401). invalid key","voice_entry_id":"x"}""",
                        HttpStatusCode.Unauthorized,
                    )
                }
            val result = safeRequest<Unit> { harness.client.post("$API/api/public/voice/process") }
            assertEquals(AppError.Server(401), result.failure)
            assertEquals(0, harness.store.refreshCount)
            assertEquals(1, harness.requests.size)
            assertFalse(harness.store.invalidated)
        }

    @Test
    fun retryHappensAtMostOnce() =
        runTest {
            val harness = Harness { json("""{"code":"PGRST301"}""", HttpStatusCode.Unauthorized) }
            val result = safeRequest<Unit> { harness.client.get("$SUPABASE/rest/v1/profiles") }
            assertEquals(AppError.Unauthorized, result.failure)
            assertEquals(2, harness.requests.size)
            assertEquals(1, harness.store.refreshCount)
        }

    @Test
    fun rejectedRefreshInvalidatesTheSession() =
        runTest {
            val store = FakeSessionTokenStore(next = { AppResult.Failure(AppError.Unauthorized) })
            val harness = Harness(store) { json("""{"code":"PGRST301"}""", HttpStatusCode.Unauthorized) }
            val result = safeRequest<Unit> { harness.client.get("$SUPABASE/rest/v1/profiles") }
            assertEquals(AppError.Unauthorized, result.failure)
            assertEquals(1, harness.requests.size)
            assertTrue(store.invalidated)
        }

    @Test
    fun offlineRefreshKeepsTheSession() =
        runTest {
            val store = FakeSessionTokenStore(next = { AppResult.Failure(AppError.Network.Offline) })
            val harness = Harness(store) { json("""{"code":"PGRST301"}""", HttpStatusCode.Unauthorized) }
            safeRequest<Unit> { harness.client.get("$SUPABASE/rest/v1/profiles") }
            assertFalse(store.invalidated)
        }

    @Test
    fun expiringTokenIsRefreshedBeforeSending() =
        runTest {
            val clock = FixedClock(Instant.fromEpochSeconds(1_000))
            val store = FakeSessionTokenStore(current = AccessToken("old", Instant.fromEpochSeconds(1_010)))
            val harness = Harness(store, clock) { json("{}") }
            safeRequest<Unit> { harness.client.get("$API/api/public/budget/suggest") }
            assertEquals("Bearer token-2", harness.requests.single().headers[HttpHeaders.Authorization])
            assertEquals(1, store.refreshCount)
        }

    @Test
    fun signedOutRequestsAreSentWithoutToken() =
        runTest {
            val store = FakeSessionTokenStore(current = null)
            val harness = Harness(store) { json("""{"code":"PGRST301"}""", HttpStatusCode.Unauthorized) }
            val result = safeRequest<Unit> { harness.client.get("$SUPABASE/rest/v1/profiles") }
            assertEquals(AppError.Unauthorized, result.failure)
            assertNull(harness.requests.single().headers[HttpHeaders.Authorization])
            assertEquals(0, store.refreshCount)
        }

    @Test
    fun concurrentRejectionsShareOneRefresh() =
        runTest {
            val store =
                FakeSessionTokenStore(
                    next = { AppResult.Success(AccessToken("token-2", farFuture)) },
                )
            val provider = createTokenProvider(lazyOf(store), FixedClock(Instant.fromEpochSeconds(1_000)))
            val stale = store.current
            val results =
                List(5) {
                    async {
                        yield()
                        provider.refresh(stale)
                    }
                }.awaitAll()
            assertEquals(1, store.refreshCount)
            assertTrue(results.all { it == AppResult.Success(AccessToken("token-2", farFuture)) })
        }

    @Test
    fun accessTokenIsRedacted() {
        assertFalse(AccessToken("secret-jwt", null).toString().contains("secret"))
    }
}
