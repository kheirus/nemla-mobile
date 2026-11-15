package app.nemla.core.security

import app.nemla.core.common.AppResult
import app.nemla.core.common.NoOpLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import java.io.File
import java.nio.file.Files
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals

class EncryptedPreferencesSecureStoreTest {
    private val directory: File = Files.createTempDirectory("secure-store").toFile()
    private val file = File(directory, SECURE_STORE_FILE_NAME)
    private val key = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
    private var currentKey: SecretKey = key
    private val cipher = AesGcmValueCipher { currentKey }
    private val store =
        EncryptedPreferencesSecureStore(
            dataStore = createEncryptedPreferencesDataStore({ file }, Dispatchers.IO),
            cipher = cipher,
            dispatcher = Dispatchers.IO,
            logger = NoOpLogger,
        )

    @AfterTest
    fun cleanUp() {
        directory.deleteRecursively()
    }

    @Test
    fun roundTripsValuesWithoutWritingPlaintext() =
        runTest {
            assertEquals(AppResult.Success(Unit), store.put("session", "refresh-token-value"))
            assertEquals(AppResult.Success("refresh-token-value"), store.get("session"))
            val raw = file.readBytes().decodeToString(throwOnInvalidSequence = false)
            assertFalse(raw.contains("refresh-token-value"))
        }

    @Test
    fun removeAndClearDeleteEntries() =
        runTest {
            store.put("a", "1")
            store.put("b", "2")
            store.remove("a")
            assertEquals(AppResult.Success(null), store.get("a"))
            assertEquals(AppResult.Success("2"), store.get("b"))
            store.clear()
            assertEquals(AppResult.Success(null), store.get("b"))
        }

    @Test
    fun ciphertextIsBoundToItsKey() {
        val payload = cipher.encrypt("value".encodeToByteArray(), "a".encodeToByteArray())
        val other = runCatching { cipher.decrypt(payload, "b".encodeToByteArray()) }
        assertFalse(other.isSuccess)
        assertEquals("value", cipher.decrypt(payload, "a".encodeToByteArray()).decodeToString())
    }

    @Test
    fun encryptionIsRandomised() {
        val first = cipher.encrypt("value".encodeToByteArray(), "a".encodeToByteArray())
        val second = cipher.encrypt("value".encodeToByteArray(), "a".encodeToByteArray())
        assertNotEquals(first.toList(), second.toList())
    }

    @Test
    fun unreadableEntriesAreDiscarded() =
        runTest {
            store.put("session", "value")
            currentKey = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
            assertEquals(AppResult.Success(null), store.get("session"))
            currentKey = key
            assertEquals(AppResult.Success(null), store.get("session"))
        }
}
