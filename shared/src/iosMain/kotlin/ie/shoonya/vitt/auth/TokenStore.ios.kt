package ie.shoonya.vitt.auth

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.readBytes
import kotlinx.cinterop.refTo
import kotlinx.cinterop.value
import kotlinx.serialization.json.Json
import platform.CoreFoundation.CFDataCreate
import platform.CoreFoundation.CFDataGetBytePtr
import platform.CoreFoundation.CFDataGetLength
import platform.CoreFoundation.CFDataRef
import platform.CoreFoundation.CFDictionaryAddValue
import platform.CoreFoundation.CFDictionaryCreateMutable
import platform.CoreFoundation.CFDictionaryRef
import platform.CoreFoundation.CFMutableDictionaryRef
import platform.CoreFoundation.CFRelease
import platform.CoreFoundation.CFStringCreateWithCString
import platform.CoreFoundation.CFTypeRefVar
import platform.CoreFoundation.kCFBooleanTrue
import platform.CoreFoundation.kCFStringEncodingUTF8
import platform.CoreFoundation.kCFTypeDictionaryKeyCallBacks
import platform.CoreFoundation.kCFTypeDictionaryValueCallBacks
import platform.Security.SecItemAdd
import platform.Security.SecItemCopyMatching
import platform.Security.SecItemDelete
import platform.Security.kSecAttrAccessible
import platform.Security.kSecAttrAccessibleAfterFirstUnlock
import platform.Security.kSecAttrAccount
import platform.Security.kSecAttrService
import platform.Security.kSecClass
import platform.Security.kSecClassGenericPassword
import platform.Security.kSecMatchLimit
import platform.Security.kSecMatchLimitOne
import platform.Security.kSecReturnData
import platform.Security.kSecValueData

/**
 * Keychain-backed token storage.
 *
 * The Keychain is the only appropriate place for a credential on iOS: encrypted
 * at rest, scoped to this app, removed when the app is deleted.
 *
 * Written against CoreFoundation directly rather than through a wrapper library.
 * The obvious candidates are unmaintained — KVault has had no release since 2023
 * — and an abandoned dependency holding refresh tokens is a worse liability than
 * thirty lines of well-understood interop.
 */
@OptIn(ExperimentalForeignApi::class)
class KeychainTokenStore(private val service: String = "ie.shoonya.vitt.oauth") : TokenStore {

    override fun save(tokens: StoredTokens) {
        val payload = Json.encodeToString(StoredTokens.serializer(), tokens)
            .encodeToByteArray().toUByteArray()
        // Keychain add fails with errSecDuplicateItem rather than replacing.
        clear()
        val data = CFDataCreate(null, payload.refTo(0), payload.size.toLong())
        val query = baseQuery {
            CFDictionaryAddValue(it, kSecValueData, data)
            // AfterFirstUnlock, not WhenUnlocked: background sync can run while
            // the phone is locked, and WhenUnlocked would make the token
            // unreadable exactly then. Still requires one unlock since boot.
            CFDictionaryAddValue(it, kSecAttrAccessible, kSecAttrAccessibleAfterFirstUnlock)
        }
        val status = SecItemAdd(query, null)
        CFRelease(query)
        data?.let { CFRelease(it) }
        // H7: clear() has already removed the previous credential by this point,
        // so a silently ignored failure here leaves the user with no token at
        // all and no idea why they were signed out. errSecMissingEntitlement
        // (-34018) is the usual cause in a misconfigured build.
        if (status != 0) {
            throw TokenStoreException("keychain write failed", status)
        }
    }

    override fun load(): StoredTokens? = memScoped {
        val result = alloc<CFTypeRefVar>()
        val query = baseQuery {
            CFDictionaryAddValue(it, kSecReturnData, kCFBooleanTrue)
            CFDictionaryAddValue(it, kSecMatchLimit, kSecMatchLimitOne)
        }
        val status = SecItemCopyMatching(query, result.ptr)
        CFRelease(query)
        if (status != 0) return@memScoped null

        @Suppress("UNCHECKED_CAST")
        val data = result.value as CFDataRef?
        val bytes = data?.let {
            CFDataGetBytePtr(it)?.readBytes(CFDataGetLength(it).toInt())
        }
        data?.let { CFRelease(it) }
        bytes?.decodeToString()?.let {
            runCatching { Json.decodeFromString(StoredTokens.serializer(), it) }.getOrNull()
        }
    }

    override fun clear() {
        val query = baseQuery { }
        SecItemDelete(query)
        CFRelease(query)
    }

    private inline fun baseQuery(extra: (CFMutableDictionaryRef?) -> Unit): CFDictionaryRef? {
        val dict = CFDictionaryCreateMutable(
            null, 0, kCFTypeDictionaryKeyCallBacks.ptr, kCFTypeDictionaryValueCallBacks.ptr,
        )
        CFDictionaryAddValue(dict, kSecClass, kSecClassGenericPassword)
        val serviceRef = cfString(service)
        val accountRef = cfString(ACCOUNT)
        CFDictionaryAddValue(dict, kSecAttrService, serviceRef)
        CFDictionaryAddValue(dict, kSecAttrAccount, accountRef)
        extra(dict)
        serviceRef?.let { CFRelease(it) }
        accountRef?.let { CFRelease(it) }
        return dict
    }

    private fun cfString(value: String) =
        CFStringCreateWithCString(null, value, kCFStringEncodingUTF8)

    private companion object {
        const val ACCOUNT = "google"
    }
}

actual fun platformTokenStore(): TokenStore = KeychainTokenStore()
