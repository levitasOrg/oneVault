package com.example.crypto

import android.util.Base64
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Unit tests for the hybrid encryption engine. Runs under Robolectric because QuantumCrypto uses
 * android.util.Base64, which is stubbed by the JVM and needs the Android framework shadow.
 */
// SDK 35 (not 36): Robolectric's SDK 36 sandbox requires Java 21, but the project builds on
// Java 17. SDK 35 exercises the same android.util.Base64 shadow our crypto needs.
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class QuantumCryptoTest {

    @Test
    fun `encrypt then decrypt round-trips the plaintext`() {
        val plaintext = """{"username":"alice","secretText":"hunter2"}"""
        val password = "correct horse battery"

        val payload = QuantumCrypto.encrypt(plaintext, password)
        val decrypted = QuantumCrypto.decrypt(payload, password)

        assertEquals(plaintext, decrypted)
    }

    @Test
    fun `new payloads use the v3 ML-KEM format`() {
        val payload = QuantumCrypto.encrypt("hello", "pw12345")
        // v3 | iv | kemCiphertext | salt | ciphertext  -> at least 5 pipe-delimited parts, v3 prefix
        assertTrue("payload should be v3-prefixed: $payload", payload.startsWith("v3|"))
        assertTrue("v3 payload should have >= 5 parts", payload.split("|").size >= 5)
    }

    @Test
    fun `same plaintext encrypts to different ciphertexts (random salt and iv)`() {
        val a = QuantumCrypto.encrypt("same", "pw12345")
        val b = QuantumCrypto.encrypt("same", "pw12345")
        assertNotEquals(a, b)
        // Both still decrypt back to the same value.
        assertEquals("same", QuantumCrypto.decrypt(a, "pw12345"))
        assertEquals("same", QuantumCrypto.decrypt(b, "pw12345"))
    }

    @Test
    fun `decrypt with wrong password fails`() {
        val payload = QuantumCrypto.encrypt("secret", "rightpassword")
        // GCM auth tag mismatch throws; we assert it does not silently return the plaintext.
        assertThrows(Exception::class.java) {
            QuantumCrypto.decrypt(payload, "wrongpassword")
        }
    }

    @Test
    fun `getQuantumStats is deterministic for a given password`() {
        val s1 = QuantumCrypto.getQuantumStats("samepw")
        val s2 = QuantumCrypto.getQuantumStats("samepw")
        assertEquals(s1["pub_key_head"], s2["pub_key_head"])
        assertEquals(s1["sec_key_head"], s2["sec_key_head"])
    }

    /**
     * Backward compatibility: a legacy v1 payload (no version prefix, 4 parts, single-SHA-256 KDF,
     * homerolled lattice KEM) produced the way old vaults stored data must still decrypt with the
     * new code. We build one here using the still-present legacy `encapsulate` plus the legacy KDF
     * (SHA-256(password || sharedSecret)), exactly mirroring the pre-upgrade encrypt path.
     */
    @Test
    fun `legacy v1 payload still decrypts`() {
        val password = "legacyMaster!"
        val plaintext = """{"username":"bob","secretText":"old-vault-secret"}"""

        val legacyPayload = makeLegacyV1Payload(plaintext, password)
        // v1 has no version prefix and exactly 4 parts.
        assertEquals(4, legacyPayload.split("|").size)
        assertTrue(!legacyPayload.startsWith("v"))

        val decrypted = QuantumCrypto.decrypt(legacyPayload, password)
        assertEquals(plaintext, decrypted)
    }

    // Mirrors the original v1 encrypt path: homerolled KEM + SHA-256(password||secret) KDF, AES-GCM,
    // packed as iv | uHex | vHex | ciphertext (no version prefix, no salt).
    private fun makeLegacyV1Payload(plaintext: String, password: String): String {
        val kem = QuantumCrypto.encapsulate(password)

        val md = MessageDigest.getInstance("SHA-256")
        md.update(password.toByteArray(Charsets.UTF_8))
        md.update(kem.sharedSecret)
        val combinedKey = md.digest()

        val iv = ByteArray(12)
        java.security.SecureRandom().nextBytes(iv)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(
            Cipher.ENCRYPT_MODE,
            SecretKeySpec(combinedKey, "AES"),
            GCMParameterSpec(128, iv)
        )
        val ciphertext = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))

        val ivB64 = Base64.encodeToString(iv, Base64.NO_WRAP)
        val cipherB64 = Base64.encodeToString(ciphertext, Base64.NO_WRAP)
        return "$ivB64|${kem.uHex}|${kem.vHex}|$cipherB64"
    }
}
