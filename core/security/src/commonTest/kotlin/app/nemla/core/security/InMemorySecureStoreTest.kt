package app.nemla.core.security

import app.nemla.core.common.AppResult
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class InMemorySecureStoreTest {
    @Test
    fun storesAndRemovesValues() =
        runTest {
            val store = InMemorySecureStore(mapOf("seed" to "1"))
            assertEquals(AppResult.Success("1"), store.get("seed"))
            store.put("session", "token")
            assertEquals(AppResult.Success("token"), store.get("session"))
            store.remove("session")
            assertEquals(AppResult.Success(null), store.get("session"))
            store.clear()
            assertEquals(emptyMap(), store.snapshot())
        }

    @Test
    fun toStringHidesValues() =
        runTest {
            val store = InMemorySecureStore(mapOf("session" to "secret-token"))
            assertFalse(store.toString().contains("secret"))
        }
}
