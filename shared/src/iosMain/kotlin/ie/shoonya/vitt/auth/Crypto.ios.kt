package ie.shoonya.vitt.auth

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.usePinned
import platform.CoreCrypto.CC_SHA256
import platform.CoreCrypto.CC_SHA256_DIGEST_LENGTH
import platform.Security.SecRandomCopyBytes
import platform.Security.kSecRandomDefault

@OptIn(ExperimentalForeignApi::class)
actual object Crypto {

    /**
     * `SecRandomCopyBytes` is the system CSPRNG. It can fail (returns non-zero),
     * and the only correct response is to abort: continuing with a partially
     * filled buffer would produce a predictable PKCE verifier, which is worse
     * than not signing in at all.
     */
    actual fun randomBytes(size: Int): ByteArray {
        val bytes = ByteArray(size)
        val status = bytes.usePinned { pinned ->
            SecRandomCopyBytes(kSecRandomDefault, size.toULong(), pinned.addressOf(0))
        }
        check(status == 0) { "SecRandomCopyBytes failed with status $status" }
        return bytes
    }

    actual fun sha256(input: ByteArray): ByteArray {
        val digest = ByteArray(CC_SHA256_DIGEST_LENGTH)
        input.usePinned { inPinned ->
            digest.usePinned { outPinned ->
                CC_SHA256(
                    if (input.isEmpty()) null else inPinned.addressOf(0),
                    input.size.toUInt(),
                    outPinned.addressOf(0).reinterpret(),
                )
            }
        }
        return digest
    }
}
