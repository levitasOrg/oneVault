package com.example.totp

import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * RFC-6238 TOTP (and its RFC-4226 HOTP core), implemented with the platform's
 * own JCE — no new dependency. Secrets are the standard base32-encoded strings
 * you get from an authenticator QR ("otpauth://"). GP-9.
 */
object Totp {

    enum class Algorithm(val jce: String) {
        SHA1("HmacSHA1"),
        SHA256("HmacSHA256"),
        SHA512("HmacSHA512"),
    }

    /** The current code for [base32Secret] at [atMillis]. Blank secret ⇒ "". */
    fun currentCode(
        base32Secret: String,
        atMillis: Long = System.currentTimeMillis(),
        digits: Int = 6,
        periodSeconds: Int = 30,
        algorithm: Algorithm = Algorithm.SHA1,
    ): String {
        val key = Base32.decode(base32Secret)
        if (key.isEmpty()) return ""
        val counter = (atMillis / 1000L) / periodSeconds
        return hotp(key, counter, digits, algorithm)
    }

    /** Seconds until the current code rolls over — drives the countdown ring. */
    fun secondsRemaining(atMillis: Long = System.currentTimeMillis(), periodSeconds: Int = 30): Int {
        val elapsed = (atMillis / 1000L) % periodSeconds
        return (periodSeconds - elapsed).toInt()
    }

    /** True if [base32Secret] decodes to a usable key — for input validation. */
    fun isValidSecret(base32Secret: String): Boolean =
        runCatching { Base32.decode(base32Secret).isNotEmpty() }.getOrDefault(false)

    /** RFC-4226 HOTP. Exposed for the RFC test vectors. */
    fun hotp(key: ByteArray, counter: Long, digits: Int, algorithm: Algorithm): String {
        val msg = ByteArray(8)
        var value = counter
        for (i in 7 downTo 0) {
            msg[i] = (value and 0xff).toByte()
            value = value shr 8
        }
        val mac = Mac.getInstance(algorithm.jce)
        mac.init(SecretKeySpec(key, "RAW"))
        val hash = mac.doFinal(msg)
        val offset = (hash[hash.size - 1].toInt() and 0x0f)
        val binary = ((hash[offset].toInt() and 0x7f) shl 24) or
            ((hash[offset + 1].toInt() and 0xff) shl 16) or
            ((hash[offset + 2].toInt() and 0xff) shl 8) or
            (hash[offset + 3].toInt() and 0xff)
        val mod = Math.pow(10.0, digits.toDouble()).toLong()
        val otp = (binary.toLong() % mod)
        return otp.toString().padStart(digits, '0')
    }
}
