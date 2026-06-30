package com.example.crypto

import android.util.Base64
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec
import kotlin.math.absoluteValue

/**
 * Pure Kotlin deterministic Pseudo-Random Number Generator (PRNG)
 * to ensure 100% deterministic key derivation across all Android platforms.
 */
class DeterministicRandom(val seed: ByteArray) {
    private var counter = 0
    private val md = MessageDigest.getInstance("SHA-256")
    private var buffer = ByteArray(0)
    private var bufferIndex = 0

    private fun refill() {
        md.reset()
        md.update(seed)
        md.update((counter and 0xFF).toByte())
        md.update(((counter shr 8) and 0xFF).toByte())
        md.update(((counter shr 16) and 0xFF).toByte())
        md.update(((counter shr 24) and 0xFF).toByte())
        buffer = md.digest()
        counter++
        bufferIndex = 0
    }

    fun nextBytes(out: ByteArray) {
        var written = 0
        while (written < out.size) {
            if (bufferIndex >= buffer.size) {
                refill()
            }
            val toCopy = Math.min(out.size - written, buffer.size - bufferIndex)
            System.arraycopy(buffer, bufferIndex, out, written, toCopy)
            bufferIndex += toCopy
            written += toCopy
        }
    }

    fun nextInt(max: Int): Int {
        val intBytes = ByteArray(4)
        nextBytes(intBytes)
        val value = ((intBytes[0].toInt() and 0xFF) shl 24) or
                    ((intBytes[1].toInt() and 0xFF) shl 16) or
                    ((intBytes[2].toInt() and 0xFF) shl 8) or
                    (intBytes[3].toInt() and 0xFF)
        return (value and 0x7FFFFFFF) % max
    }

    fun nextBoolean(): Boolean {
        if (bufferIndex >= buffer.size) { refill() }
        val b = buffer[bufferIndex++].toInt() and 1
        return b == 1
    }
}

/**
 * Polynomial Ring Arithmetic over R_q = Z_q[X] / (X^256 + 1)
 *
 * This implements the modular polynomial arithmetic used in NIST-standardized
 * Post-Quantum Cryptography schemes (Kyber/ML-KEM).
 *
 * It uses q = 3329. Degree of polynomials is 256.
 */
class Polynomial(val coeffs: IntArray) {
    companion object {
        const val N = 256
        const val Q = 3329

        // Generate a random polynomial with small coefficients (noise/error)
        fun randomNoise(random: SecureRandom): Polynomial {
            val arr = IntArray(N)
            for (i in 0 until N) {
                // Binomial distribution simulation with small range [-1, 0, 1]
                val coin1 = if (random.nextBoolean()) 1 else 0
                val coin2 = if (random.nextBoolean()) 1 else 0
                arr[i] = (coin1 - coin2 + Q) % Q
            }
            return Polynomial(arr)
        }

        fun randomNoise(random: DeterministicRandom): Polynomial {
            val arr = IntArray(N)
            for (i in 0 until N) {
                val coin1 = if (random.nextBoolean()) 1 else 0
                val coin2 = if (random.nextBoolean()) 1 else 0
                arr[i] = (coin1 - coin2 + Q) % Q
            }
            return Polynomial(arr)
        }

        // Generate a random uniform polynomial
        fun randomUniform(random: SecureRandom): Polynomial {
            val arr = IntArray(N)
            for (i in 0 until N) {
                arr[i] = random.nextInt(Q)
            }
            return Polynomial(arr)
        }

        fun randomUniform(random: DeterministicRandom): Polynomial {
            val arr = IntArray(N)
            for (i in 0 until N) {
                arr[i] = random.nextInt(Q)
            }
            return Polynomial(arr)
        }

        // Parse 32-byte key into a binary representation polynomial
        fun fromBytes(bytes: ByteArray): Polynomial {
            val arr = IntArray(N)
            for (i in 0 until Math.min(bytes.size * 8, N)) {
                val byteIdx = i / 8
                val bitIdx = i % 8
                val bit = (bytes[byteIdx].toInt() shr bitIdx) and 1
                // Map bit {0 -> 0, 1 -> Q/2} for error-tolerance decapsulation
                arr[i] = if (bit == 1) Q / 2 else 0
            }
            return Polynomial(arr)
        }
    }

    init {
        require(coeffs.size == N)
    }

    fun add(other: Polynomial): Polynomial {
        val result = IntArray(N)
        for (i in 0 until N) {
            result[i] = (this.coeffs[i] + other.coeffs[i]) % Q
        }
        return Polynomial(result)
    }

    fun subtract(other: Polynomial): Polynomial {
        val result = IntArray(N)
        for (i in 0 until N) {
            result[i] = (this.coeffs[i] - other.coeffs[i] + Q) % Q
        }
        return Polynomial(result)
    }

    /**
     * Polynomial multiplication modulo (X^256 + 1) and q = 3329
     */
    fun multiply(other: Polynomial): Polynomial {
        val result = IntArray(N)
        for (i in 0 until N) {
            for (j in 0 until N) {
                val mul = (this.coeffs[i].toLong() * other.coeffs[j]) % Q
                val k = i + j
                if (k < N) {
                    result[k] = ((result[k] + mul) % Q).toInt()
                } else {
                    // X^256 = -1, so coefficients starting from index 256 wrap around with negative sign
                    result[k - N] = ((result[k - N] - mul + Q) % Q).toInt()
                }
            }
        }
        return Polynomial(result)
    }

    /**
     * Decode the polynomial back into key bytes (32 bytes) by checking if coefficients
     * are closer to Q/2 (bit 1) or closer to 0 (bit 0). This provides error resilience.
     */
    fun toBytes(): ByteArray {
        val bytes = ByteArray(32)
        for (i in 0 until N) {
            val valMod = coeffs[i] % Q
            val distToQ2 = (valMod - Q / 2).absoluteValue
            val distTo0OrQ = Math.min(valMod, Q - valMod)
            
            val bit = if (distToQ2 < distTo0OrQ) 1 else 0
            if (bit == 1) {
                val byteIdx = i / 8
                val bitIdx = i % 8
                bytes[byteIdx] = (bytes[byteIdx].toInt() or (1 shl bitIdx)).toByte()
            }
        }
        return bytes
    }
}

/**
 * Post-Quantum Cryptography Hybrid Encryption Engine.
 *
 * SECURITY CAVEAT (read before trusting this in production):
 * This is a *homerolled, educational* approximation of Kyber/ML-KEM, not the NIST-standardized
 * algorithm. It lacks NTT, proper Centered Binomial Distribution sampling, and the compression
 * steps of real ML-KEM, and it has not been independently audited. The DeterministicRandom PRNG
 * is a SHA-256 counter construction used to regenerate Alice's key deterministically from the
 * password — acceptable for that narrow purpose, but not a general-purpose CSPRNG.
 *
 * The actual confidentiality of stored data rests on the AES-256-GCM layer keyed by the
 * PBKDF2-stretched master password (see hybridKDF), which is sound. The Kyber layer adds defense
 * in depth but should NOT be relied on as the sole post-quantum guarantee. For a production
 * password manager, replace this with a vetted library (e.g. BouncyCastle's ML-KEM / Tink).
 */
object QuantumCrypto {
    private val random = SecureRandom()

    // Marks the PBKDF2-based payload format. Anything without this prefix is treated as the
    // legacy single-SHA-256 format and decrypted with legacyHybridKDF for backward compatibility.
    private const val PAYLOAD_VERSION = "v2"

    // OWASP-recommended floor for PBKDF2-HMAC-SHA256 (2023). High enough to slow brute force,
    // low enough to stay responsive on-device since work already runs off the main thread.
    private const val PBKDF2_ITERATIONS = 210_000

    // Struct holding the encapsulated key and public Kyber cipher values
    data class KyberKEM(
        val uHex: String,
        val vHex: String,
        val sharedSecret: ByteArray
    )

    /**
     * Kyber-like Ring Learning with Errors (RLWE) Key Encapsulation Mechanism
     *
     * Given a master password, we derive a deterministic uniform polynomial "A" and Alice's secret key "s".
     * Bob (creating a new item) generates a key-encapsulation token (sharedSecret), wraps it using public s,
     * producing ciphertext (u, v). Alice recovers the sharedSecret using her master credentials.
     */
    fun encapsulate(password: String): KyberKEM {
        // Derive master seed from password
        val md = MessageDigest.getInstance("SHA-256")
        val passwordSeed = md.digest(password.toByteArray(Charsets.UTF_8))

        // Alice (Vault Authority) Keys
        val aliceRandom = DeterministicRandom(passwordSeed)
        val A = Polynomial.randomUniform(aliceRandom)
        val s = Polynomial.randomNoise(aliceRandom) // Secret key
        val e = Polynomial.randomNoise(aliceRandom) // Error term

        // Public key t = A*s + e
        val t = A.multiply(s).add(e)

        // Bob (Caller session) Encapsulation
        // Bob wants to encaps a fresh 256-bit symmetric key
        val sharedSecret = ByteArray(32)
        random.nextBytes(sharedSecret)
        
        val keyPoly = Polynomial.fromBytes(sharedSecret)

        val r = Polynomial.randomNoise(random)
        val e1 = Polynomial.randomNoise(random)
        val e2 = Polynomial.randomNoise(random)

        // cipher u = A*r + e1
        val u = A.multiply(r).add(e1)
        // cipher v = t*r + e2 + encode(sharedSecret)
        val v = t.multiply(r).add(e2).add(keyPoly)

        val uHex = coeffsToHex(u.coeffs)
        val vHex = coeffsToHex(v.coeffs)

        return KyberKEM(uHex, vHex, sharedSecret)
    }

    /**
     * Decapsulates Kyber-like LWE token to retrieve the shared secret key
     */
    fun decapsulate(password: String, uHex: String, vHex: String): ByteArray {
        val md = MessageDigest.getInstance("SHA-256")
        val passwordSeed = md.digest(password.toByteArray(Charsets.UTF_8))

        // Alice regenerates her key pair from master config
        val aliceRandom = DeterministicRandom(passwordSeed)
        val A = Polynomial.randomUniform(aliceRandom)
        val s = Polynomial.randomNoise(aliceRandom)

        // Parse ciphertext from Hex
        val uCoeffs = hexToCoeffs(uHex)
        val vCoeffs = hexToCoeffs(vHex)
        
        val u = Polynomial(uCoeffs)
        val v = Polynomial(vCoeffs)

        // Decryption: decryptPoly = v - u * s = t*r + e2 + m - (A*r + e1)*s
        // Because t = A*s + e, this expands to:
        // (A*s + e)*r + e2 + m - A*r*s - e1*s = e*r + e2 + m - e1*s
        // Since e, r, e1, e2, s are extremely small, they act as noise, leaving "m" readable!
        val uMulS = u.multiply(s)
        val decryptedPoly = v.subtract(uMulS)

        return decryptedPoly.toBytes()
    }

    /**
     * Robust Hybrid Encryption: Protects plaintext using AES-256-GCM.
     * The AES key is derived from the Master Key blended with the encapsulated Post-Quantum Kyber Key!
     */
    fun encrypt(plaintext: String, password: String): String {
        // Step 1: Run Kyber encapsulation to generate shared token
        val kem = encapsulate(password)

        // Step 2: Derive the AES key by stretching the master password together with the
        // Post-Quantum shared secret through PBKDF2 (see hybridKDF). A fresh random salt per
        // payload makes precomputation / rainbow-table attacks useless.
        val salt = ByteArray(16)
        random.nextBytes(salt)
        val combinedKey = hybridKDF(password, kem.sharedSecret, salt)

        // Step 3: Standard AES-256-GCM authenticated encryption
        val iv = ByteArray(12)
        random.nextBytes(iv)

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val spec = GCMParameterSpec(128, iv)
        val secretKeySpec = SecretKeySpec(combinedKey, "AES")
        cipher.init(Cipher.ENCRYPT_MODE, secretKeySpec, spec)

        val ciphertext = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))

        // Pack as v2: VERSION | iv | uHex | vHex | salt | ciphertext
        val ivBase64 = Base64.encodeToString(iv, Base64.NO_WRAP)
        val cipherBase64 = Base64.encodeToString(ciphertext, Base64.NO_WRAP)
        val saltBase64 = Base64.encodeToString(salt, Base64.NO_WRAP)

        return "$PAYLOAD_VERSION|$ivBase64|${kem.uHex}|${kem.vHex}|$saltBase64|$cipherBase64"
    }

    /**
     * Decrypts hybrid AES payload. Supports both the current PBKDF2-based v2 format and the
     * legacy 4-part SHA-256 format so vaults created before the KDF upgrade still open.
     */
    fun decrypt(encryptedPayload: String, password: String): String {
        val parts = encryptedPayload.split("|")

        val iv: ByteArray
        val uHex: String
        val vHex: String
        val ciphertext: ByteArray
        val salt: ByteArray?

        when {
            // v2: VERSION | iv | uHex | vHex | salt | ciphertext
            parts.size >= 6 && parts[0] == PAYLOAD_VERSION -> {
                iv = Base64.decode(parts[1], Base64.DEFAULT)
                uHex = parts[2]
                vHex = parts[3]
                salt = Base64.decode(parts[4], Base64.DEFAULT)
                ciphertext = Base64.decode(parts[5], Base64.DEFAULT)
            }
            // legacy v1: iv | uHex | vHex | ciphertext (SHA-256 KDF, no salt)
            parts.size >= 4 -> {
                iv = Base64.decode(parts[0], Base64.DEFAULT)
                uHex = parts[1]
                vHex = parts[2]
                ciphertext = Base64.decode(parts[3], Base64.DEFAULT)
                salt = null
            }
            else -> throw IllegalArgumentException("Invalid encrypted payload format")
        }

        // Decapsulate Post-Quantum secret key
        val sharedSecret = decapsulate(password, uHex, vHex)

        // Derive the identical combined hybrid key (PBKDF2 for v2, legacy SHA-256 for v1)
        val combinedKey = if (salt != null) {
            hybridKDF(password, sharedSecret, salt)
        } else {
            legacyHybridKDF(password, sharedSecret)
        }

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val spec = GCMParameterSpec(128, iv)
        val secretKeySpec = SecretKeySpec(combinedKey, "AES")
        cipher.init(Cipher.DECRYPT_MODE, secretKeySpec, spec)

        val decryptedBytes = cipher.doFinal(ciphertext)
        return String(decryptedBytes, Charsets.UTF_8)
    }

    /**
     * Hybrid KDF: stretches the master password + Post-Quantum session secret through PBKDF2
     * with a high iteration count. Stretching is what makes a stolen vault expensive to brute
     * force — a single SHA-256 hash (the old approach) can be guessed billions of times/second
     * on a GPU, whereas PBKDF2 with 210k iterations slows each guess by orders of magnitude.
     */
    private fun hybridKDF(password: String, pqSecret: ByteArray, salt: ByteArray): ByteArray {
        // Bind the PQ secret to the password by appending it to the passphrase characters, so
        // both inputs must be known to derive the key.
        val pqHex = pqSecret.joinToString("") { "%02x".format(it) }
        val passphrase = (password + pqHex).toCharArray()
        val spec = PBEKeySpec(passphrase, salt, PBKDF2_ITERATIONS, 256)
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        return factory.generateSecret(spec).encoded
    }

    /** Legacy single-SHA-256 KDF, retained only to decrypt pre-upgrade (v1) payloads. */
    private fun legacyHybridKDF(password: String, pqSecret: ByteArray): ByteArray {
        val md = MessageDigest.getInstance("SHA-256")
        md.update(password.toByteArray(Charsets.UTF_8))
        md.update(pqSecret)
        return md.digest()
    }

    private fun coeffsToHex(coeffs: IntArray): String {
        val sb = StringBuilder()
        for (c in coeffs) {
            sb.append(c.toString(16).padStart(3, '0'))
        }
        return sb.toString()
    }

    private fun hexToCoeffs(hex: String): IntArray {
        val coeffs = IntArray(Polynomial.N)
        for (i in 0 until Polynomial.N) {
            val start = i * 3
            if (start + 3 <= hex.length) {
                coeffs[i] = hex.substring(start, start + 3).toInt(16)
            }
        }
        return coeffs
    }

    /**
     * Generate actual post-quantum metrics for visual display
     */
    fun getQuantumStats(password: String): Map<String, Any> {
        val md = MessageDigest.getInstance("SHA-256")
        val seed = md.digest(password.toByteArray(Charsets.UTF_8))
        val pseudoGen = DeterministicRandom(seed)
        val A = Polynomial.randomUniform(pseudoGen)
        val s = Polynomial.randomNoise(pseudoGen)
        val e = Polynomial.randomNoise(pseudoGen)
        val t = A.multiply(s).add(e)

        return mapOf(
            "ring_sz" to Polynomial.N,
            "modulus" to Polynomial.Q,
            "secret_noise_avg" to s.coeffs.map { if (it > Polynomial.Q / 2) it - Polynomial.Q else it }.average(),
            "err_noise_avg" to e.coeffs.map { if (it > Polynomial.Q / 2) it - Polynomial.Q else it }.average(),
            "pub_key_head" to t.coeffs.take(12).joinToString(", "),
            "sec_key_head" to s.coeffs.take(12).map { if (it > Polynomial.Q / 2) it - Polynomial.Q else it }.joinToString(", ")
        )
    }
}
