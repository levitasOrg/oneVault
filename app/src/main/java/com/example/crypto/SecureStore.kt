package com.example.crypto

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Hardware-backed secret storage built directly on the Android Keystore.
 *
 * Values written here are encrypted with an AES-256-GCM key that lives inside the
 * Keystore (and, on supported devices, inside the hardware-backed StrongBox / TEE).
 * The key material never enters the app process, so even a rooted device that can
 * read SharedPreferences only sees ciphertext.
 *
 * We deliberately avoid androidx.security:security-crypto here: that library is
 * deprecated and has known crash/migration issues on newer API levels, while the
 * raw Keystore API below is stable and has no transitive baggage.
 */
class SecureStore(context: Context) {

    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences(SECURE_PREFS, Context.MODE_PRIVATE)

    private val keyStore: KeyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }

    private fun getOrCreateKey(): SecretKey {
        (keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }

        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        val spec = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .build()
        generator.init(spec)
        return generator.generateKey()
    }

    /** Encrypts [value] under the Keystore key and stores it. Pass null to remove the entry. */
    fun putString(name: String, value: String?) {
        if (value == null) {
            prefs.edit().remove(name).apply()
            return
        }
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val iv = cipher.iv
        val ciphertext = cipher.doFinal(value.toByteArray(Charsets.UTF_8))

        // Pack iv | ciphertext so a single Base64 blob round-trips cleanly.
        val packed = ByteArray(1 + iv.size + ciphertext.size)
        packed[0] = iv.size.toByte()
        System.arraycopy(iv, 0, packed, 1, iv.size)
        System.arraycopy(ciphertext, 0, packed, 1 + iv.size, ciphertext.size)

        prefs.edit().putString(name, Base64.encodeToString(packed, Base64.NO_WRAP)).apply()
    }

    /** Returns the decrypted value for [name], or null if absent or undecryptable. */
    fun getString(name: String): String? {
        val stored = prefs.getString(name, null) ?: return null
        return try {
            val packed = Base64.decode(stored, Base64.NO_WRAP)
            val ivSize = packed[0].toInt()
            val iv = packed.copyOfRange(1, 1 + ivSize)
            val ciphertext = packed.copyOfRange(1 + ivSize, packed.size)

            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(128, iv))
            String(cipher.doFinal(ciphertext), Charsets.UTF_8)
        } catch (e: Exception) {
            // Key rotated/invalidated or data corrupted: treat as absent so callers fall
            // back to asking for the master password rather than crashing.
            null
        }
    }

    fun remove(name: String) = prefs.edit().remove(name).apply()

    /**
     * Returns a stable, randomly-generated passphrase for the encrypted database, creating one
     * on first use. The passphrase itself is stored Keystore-encrypted, so the SQLCipher key is
     * never persisted in the clear.
     */
    fun getOrCreateDbPassphrase(): ByteArray {
        getString(DB_PASSPHRASE_KEY)?.let { return Base64.decode(it, Base64.NO_WRAP) }

        val fresh = ByteArray(32)
        java.security.SecureRandom().nextBytes(fresh)
        putString(DB_PASSPHRASE_KEY, Base64.encodeToString(fresh, Base64.NO_WRAP))
        return fresh
    }

    companion object {
        private const val SECURE_PREFS = "onevault_secure_prefs"
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val KEY_ALIAS = "onevault_master_secret_key"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val DB_PASSPHRASE_KEY = "db_passphrase"
    }
}
