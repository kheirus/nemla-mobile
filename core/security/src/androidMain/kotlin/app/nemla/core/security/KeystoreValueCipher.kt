package app.nemla.core.security

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

internal interface ValueCipher {
    fun encrypt(
        plaintext: ByteArray,
        associatedData: ByteArray,
    ): ByteArray

    fun decrypt(
        payload: ByteArray,
        associatedData: ByteArray,
    ): ByteArray
}

internal class AesGcmValueCipher(
    private val keyProvider: () -> SecretKey,
) : ValueCipher {
    override fun encrypt(
        plaintext: ByteArray,
        associatedData: ByteArray,
    ): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, keyProvider())
        cipher.updateAAD(associatedData)
        val ciphertext = cipher.doFinal(plaintext)
        val iv = cipher.iv
        check(iv.size == IV_BYTES)
        return byteArrayOf(FORMAT_VERSION) + iv + ciphertext
    }

    override fun decrypt(
        payload: ByteArray,
        associatedData: ByteArray,
    ): ByteArray {
        require(payload.size > 1 + IV_BYTES && payload[0] == FORMAT_VERSION)
        val iv = payload.copyOfRange(1, 1 + IV_BYTES)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, keyProvider(), GCMParameterSpec(TAG_BITS, iv))
        cipher.updateAAD(associatedData)
        return cipher.doFinal(payload, 1 + IV_BYTES, payload.size - 1 - IV_BYTES)
    }

    private companion object {
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val IV_BYTES = 12
        const val TAG_BITS = 128
        const val FORMAT_VERSION: Byte = 1
    }
}

internal class AndroidKeystoreKeyProvider(
    private val alias: String,
) : () -> SecretKey {
    @Volatile
    private var cached: SecretKey? = null

    override fun invoke(): SecretKey = cached ?: synchronized(this) { cached ?: loadOrCreate().also { cached = it } }

    private fun loadOrCreate(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (keyStore.getKey(alias, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec
                .Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(KEY_BITS)
                .setRandomizedEncryptionRequired(true)
                .build(),
        )
        return generator.generateKey()
    }

    private companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val KEY_BITS = 256
    }
}
