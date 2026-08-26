package ie.shoonya.vitt.auth

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import kotlinx.serialization.json.Json
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Keystore-backed token storage.
 *
 * The token is encrypted with an AES key held in the Android Keystore, so the
 * key material never enters the app's process memory and cannot be extracted
 * from a backup or a rooted filesystem read. Only the ciphertext is written to
 * preferences.
 *
 * Written directly against Keystore rather than using `EncryptedSharedPreferences`,
 * which was deprecated in androidx.security 1.1.0-alpha07 (April 2025) with no
 * stable successor.
 */
actual class TokenStore(private val context: Context) {

    private val prefs by lazy {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    }

    actual fun save(tokens: StoredTokens) {
        val plaintext = Json.encodeToString(StoredTokens.serializer(), tokens).toByteArray()
        val cipher = Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.ENCRYPT_MODE, key()) }
        val ciphertext = cipher.doFinal(plaintext)
        prefs.edit()
            // The GCM IV must be stored alongside; it is not secret, but reusing
            // one with the same key would be catastrophic, so it is generated
            // fresh by the cipher on every save.
            .putString(KEY_IV, Base64.encodeToString(cipher.iv, Base64.NO_WRAP))
            .putString(KEY_PAYLOAD, Base64.encodeToString(ciphertext, Base64.NO_WRAP))
            .apply()
    }

    actual fun load(): StoredTokens? {
        val iv = prefs.getString(KEY_IV, null) ?: return null
        val payload = prefs.getString(KEY_PAYLOAD, null) ?: return null
        return runCatching {
            val cipher = Cipher.getInstance(TRANSFORMATION).apply {
                init(
                    Cipher.DECRYPT_MODE,
                    key(),
                    GCMParameterSpec(TAG_BITS, Base64.decode(iv, Base64.NO_WRAP)),
                )
            }
            val plaintext = cipher.doFinal(Base64.decode(payload, Base64.NO_WRAP))
            Json.decodeFromString(StoredTokens.serializer(), plaintext.decodeToString())
        }.getOrElse {
            // Decryption fails if the key was invalidated — a device-security
            // change, or a restore onto different hardware. Treat it as "signed
            // out" rather than crashing: the user re-authenticates and nothing
            // is lost, because tokens are not data.
            clear()
            null
        }
    }

    actual fun clear() {
        prefs.edit().remove(KEY_IV).remove(KEY_PAYLOAD).apply()
    }

    private fun key(): SecretKey {
        val keystore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (keystore.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }

        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE).apply {
            init(
                KeyGenParameterSpec.Builder(
                    KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    // Deliberately not requiring user authentication: background
                    // sync must be able to refresh a token while the phone is
                    // locked, which setUserAuthenticationRequired would prevent.
                    .setUserAuthenticationRequired(false)
                    .build()
            )
        }.generateKey()
    }

    private companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val KEY_ALIAS = "ie.shoonya.vitt.oauth"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val TAG_BITS = 128
        const val PREFS = "vitt_auth"
        const val KEY_IV = "iv"
        const val KEY_PAYLOAD = "payload"
    }
}
