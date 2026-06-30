package com.example.crypto

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
    fun `new payloads use the v2 PBKDF2 format`() {
        val payload = QuantumCrypto.encrypt("hello", "pw12345")
        // v2 | iv | uHex | vHex | salt | ciphertext  -> at least 6 pipe-delimited parts, v2 prefix
        assertTrue("payload should be v2-prefixed: $payload", payload.startsWith("v2|"))
        assertTrue("v2 payload should have >= 6 parts", payload.split("|").size >= 6)
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
}
