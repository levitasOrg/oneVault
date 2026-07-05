package com.example.totp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure-JVM correctness tests for the TOTP/HOTP engine. These use the canonical
 * test vectors published in RFC 4226 (HOTP, Appendix D) and RFC 6238 (TOTP,
 * Appendix B) — if the code ever drifts, these fail. No Android framework, so
 * they run without Robolectric.
 */
class TotpTest {

    // RFC 4226 shared secret: ASCII "12345678901234567890".
    private val hotpKey = "12345678901234567890".toByteArray(Charsets.US_ASCII)

    // Base32 of that same 20-byte secret (RFC 6238 SHA-1 vectors use it).
    private val sha1Base32 = "GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ"

    // RFC 6238 SHA-256 / SHA-512 seeds (ASCII, 32 and 64 bytes).
    private val sha256Key = "12345678901234567890123456789012".toByteArray(Charsets.US_ASCII)
    private val sha512Key =
        "1234567890123456789012345678901234567890123456789012345678901234".toByteArray(Charsets.US_ASCII)

    @Test
    fun `RFC 4226 HOTP vectors, counters 0 through 9`() {
        val expected = listOf(
            "755224", "287082", "359152", "969429", "338314",
            "254676", "287922", "162583", "399871", "520489",
        )
        expected.forEachIndexed { counter, code ->
            assertEquals(
                "HOTP mismatch at counter $counter",
                code,
                Totp.hotp(hotpKey, counter.toLong(), 6, Totp.Algorithm.SHA1),
            )
        }
    }

    @Test
    fun `RFC 6238 TOTP SHA-1 vectors, 8 digits`() {
        // (unix seconds) to expected 8-digit code, SHA-1, 30s step.
        val vectors = mapOf(
            59L to "94287082",
            1111111109L to "07081804",
            1111111111L to "14050471",
            1234567890L to "89005924",
            2000000000L to "69279037",
            20000000000L to "65353130",
        )
        vectors.forEach { (seconds, code) ->
            assertEquals(
                "TOTP SHA-1 mismatch at t=$seconds",
                code,
                Totp.currentCode(sha1Base32, atMillis = seconds * 1000L, digits = 8, algorithm = Totp.Algorithm.SHA1),
            )
        }
    }

    @Test
    fun `RFC 6238 TOTP SHA-256 and SHA-512 vectors at t=59`() {
        // T = 59 / 30 = 1.
        assertEquals("46119246", Totp.hotp(sha256Key, 1L, 8, Totp.Algorithm.SHA256))
        assertEquals("90693936", Totp.hotp(sha512Key, 1L, 8, Totp.Algorithm.SHA512))
    }

    @Test
    fun `default authenticator app code is 6 digits SHA-1 30s`() {
        val code = Totp.currentCode(sha1Base32, atMillis = 59_000L)
        assertEquals("287082", code) // last 6 digits of the 8-digit 94287082
        assertEquals(6, code.length)
    }

    @Test
    fun `secondsRemaining counts down within the 30s window`() {
        assertEquals(30, Totp.secondsRemaining(atMillis = 0L))
        assertEquals(29, Totp.secondsRemaining(atMillis = 1_000L))
        assertEquals(1, Totp.secondsRemaining(atMillis = 29_000L))
        assertEquals(30, Totp.secondsRemaining(atMillis = 30_000L))
    }

    @Test
    fun `blank or unset secret yields empty code, not a crash`() {
        assertEquals("", Totp.currentCode(""))
        assertEquals("", Totp.currentCode("   "))
    }

    @Test
    fun `isValidSecret accepts real secrets and rejects junk`() {
        assertTrue(Totp.isValidSecret(sha1Base32))
        assertTrue(Totp.isValidSecret("jbsw y3dp ehpk 3pxp")) // lower case, spaced — as printed
        assertFalse(Totp.isValidSecret("not-base-32!!!"))
        assertFalse(Totp.isValidSecret(""))
    }

    @Test
    fun `base32 decoder is case and whitespace insensitive`() {
        val a = Base32.decode("GEZDGNBVGY3TQOJQ")
        val b = Base32.decode("gezd gnbv gy3t qojq")
        val c = Base32.decode("GEZDGNBVGY3TQOJQ====")
        assertTrue(a.contentEquals(b))
        assertTrue(a.contentEquals(c))
        assertEquals("1234567890", String(a, Charsets.US_ASCII))
    }
}
