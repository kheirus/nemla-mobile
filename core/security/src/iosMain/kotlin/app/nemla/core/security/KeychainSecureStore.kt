@file:OptIn(ExperimentalForeignApi::class)

package app.nemla.core.security

import app.nemla.core.common.AppError
import app.nemla.core.common.AppLogger
import app.nemla.core.common.AppResult
import app.nemla.core.common.LogAttribute
import app.nemla.core.common.LogField
import app.nemla.core.common.warn
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.convert
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.readBytes
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.usePinned
import kotlinx.cinterop.value
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import platform.CoreFoundation.CFDataCreate
import platform.CoreFoundation.CFDataGetBytePtr
import platform.CoreFoundation.CFDataGetLength
import platform.CoreFoundation.CFDataRef
import platform.CoreFoundation.CFDictionaryAddValue
import platform.CoreFoundation.CFDictionaryCreateMutable
import platform.CoreFoundation.CFDictionaryRef
import platform.CoreFoundation.CFRelease
import platform.CoreFoundation.CFStringCreateWithCString
import platform.CoreFoundation.CFStringRef
import platform.CoreFoundation.CFTypeRef
import platform.CoreFoundation.CFTypeRefVar
import platform.CoreFoundation.kCFAllocatorDefault
import platform.CoreFoundation.kCFBooleanFalse
import platform.CoreFoundation.kCFBooleanTrue
import platform.CoreFoundation.kCFStringEncodingUTF8
import platform.CoreFoundation.kCFTypeDictionaryKeyCallBacks
import platform.CoreFoundation.kCFTypeDictionaryValueCallBacks
import platform.Security.SecItemAdd
import platform.Security.SecItemCopyMatching
import platform.Security.SecItemDelete
import platform.Security.SecItemUpdate
import platform.Security.errSecItemNotFound
import platform.Security.errSecSuccess
import platform.Security.kSecAttrAccessible
import platform.Security.kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly
import platform.Security.kSecAttrAccount
import platform.Security.kSecAttrService
import platform.Security.kSecAttrSynchronizable
import platform.Security.kSecClass
import platform.Security.kSecClassGenericPassword
import platform.Security.kSecMatchLimit
import platform.Security.kSecMatchLimitOne
import platform.Security.kSecReturnData
import platform.Security.kSecValueData

internal class KeychainSecureStore(
    private val service: String,
    private val dispatcher: CoroutineDispatcher,
    private val logger: AppLogger,
) : SecureStore {
    override suspend fun get(key: String): AppResult<String?> =
        withContext(dispatcher) {
            cfScope { scope ->
                val query =
                    scope.dictionary(
                        kSecClass to kSecClassGenericPassword,
                        kSecAttrService to scope.string(service),
                        kSecAttrAccount to scope.string(key),
                        kSecReturnData to kCFBooleanTrue,
                        kSecMatchLimit to kSecMatchLimitOne,
                    )
                memScoped {
                    val result = alloc<CFTypeRefVar>()
                    val status = SecItemCopyMatching(query, result.ptr)
                    val found = result.value
                    try {
                        when (status) {
                            errSecSuccess -> {
                                val dataRef: CFDataRef? = found?.reinterpret()
                                AppResult.Success(dataRef?.readString())
                            }

                            errSecItemNotFound -> {
                                AppResult.Success(null)
                            }

                            else -> {
                                failure(SecureStoreLog.READ_FAILED, status)
                            }
                        }
                    } finally {
                        if (found != null) CFRelease(found)
                    }
                }
            }
        }

    override suspend fun put(
        key: String,
        value: String,
    ): AppResult<Unit> =
        withContext(dispatcher) {
            cfScope { scope ->
                val data = scope.data(value.encodeToByteArray())
                val query = scope.itemQuery(key)
                val attributes =
                    scope.dictionary(
                        kSecValueData to data,
                        kSecAttrAccessible to kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly,
                    )
                var status = SecItemUpdate(query, attributes)
                if (status == errSecItemNotFound) {
                    val item =
                        scope.dictionary(
                            kSecClass to kSecClassGenericPassword,
                            kSecAttrService to scope.string(service),
                            kSecAttrAccount to scope.string(key),
                            kSecValueData to data,
                            kSecAttrAccessible to kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly,
                            kSecAttrSynchronizable to kCFBooleanFalse,
                        )
                    status = SecItemAdd(item, null)
                }
                if (status == errSecSuccess) AppResult.Success(Unit) else failure(SecureStoreLog.WRITE_FAILED, status)
            }
        }

    override suspend fun remove(key: String): AppResult<Unit> =
        withContext(dispatcher) {
            cfScope { scope -> delete(scope.itemQuery(key), SecureStoreLog.WRITE_FAILED) }
        }

    override suspend fun clear(): AppResult<Unit> =
        withContext(dispatcher) {
            cfScope { scope ->
                delete(
                    scope.dictionary(
                        kSecClass to kSecClassGenericPassword,
                        kSecAttrService to scope.string(service),
                    ),
                    SecureStoreLog.CLEAR_FAILED,
                )
            }
        }

    private fun delete(
        query: CFDictionaryRef?,
        event: SecureStoreLog,
    ): AppResult<Unit> {
        val status = SecItemDelete(query)
        return if (status == errSecSuccess || status == errSecItemNotFound) AppResult.Success(Unit) else failure(event, status)
    }

    private fun CfScope.itemQuery(key: String): CFDictionaryRef? =
        dictionary(
            kSecClass to kSecClassGenericPassword,
            kSecAttrService to string(service),
            kSecAttrAccount to string(key),
        )

    private fun failure(
        event: SecureStoreLog,
        status: Int,
    ): AppResult.Failure {
        logger.warn(event, LogAttribute.number(LogField.STATUS, status))
        return AppResult.Failure(AppError.Storage)
    }

    override fun toString(): String = "KeychainSecureStore(***)"
}

internal const val KEYCHAIN_SERVICE = "app.nemla.securestore"

private class CfScope {
    private val owned = mutableListOf<CFTypeRef>()

    fun string(value: String): CFStringRef? =
        CFStringCreateWithCString(kCFAllocatorDefault, value, kCFStringEncodingUTF8)?.also {
            owned +=
                it
        }

    fun data(bytes: ByteArray): CFDataRef? {
        val created =
            if (bytes.isEmpty()) {
                CFDataCreate(kCFAllocatorDefault, null, 0)
            } else {
                bytes.asUByteArray().usePinned { CFDataCreate(kCFAllocatorDefault, it.addressOf(0), bytes.size.convert()) }
            }
        return created?.also { owned += it }
    }

    fun dictionary(vararg entries: Pair<CFStringRef?, CFTypeRef?>): CFDictionaryRef? {
        val dictionary =
            CFDictionaryCreateMutable(
                kCFAllocatorDefault,
                entries.size.convert(),
                kCFTypeDictionaryKeyCallBacks.ptr,
                kCFTypeDictionaryValueCallBacks.ptr,
            ) ?: return null
        owned += dictionary
        entries.forEach { (key, value) -> CFDictionaryAddValue(dictionary, key, value) }
        return dictionary
    }

    fun release() {
        owned.asReversed().forEach { CFRelease(it) }
        owned.clear()
    }
}

private inline fun <R> cfScope(block: (CfScope) -> R): R {
    val scope = CfScope()
    try {
        return block(scope)
    } finally {
        scope.release()
    }
}

private fun CFDataRef.readString(): String {
    val length = CFDataGetLength(this).toInt()
    if (length == 0) return ""
    val bytes = CFDataGetBytePtr(this) ?: return ""
    return bytes.readBytes(length).decodeToString()
}
