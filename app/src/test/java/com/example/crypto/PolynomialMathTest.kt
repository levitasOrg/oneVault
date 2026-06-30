package com.example.crypto

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure-JVM tests for the polynomial ring math and the deterministic PRNG. These need no Android
 * framework, so they run fast without Robolectric.
 */
class PolynomialMathTest {

    @Test
    fun `DeterministicRandom is reproducible from the same seed`() {
        val seed = byteArrayOf(1, 2, 3, 4, 5)
        val a = DeterministicRandom(seed)
        val b = DeterministicRandom(seed)

        val outA = ByteArray(64)
        val outB = ByteArray(64)
        a.nextBytes(outA)
        b.nextBytes(outB)

        assertArrayEquals("same seed must yield same byte stream", outA, outB)
    }

    @Test
    fun `DeterministicRandom diverges for different seeds`() {
        val a = DeterministicRandom(byteArrayOf(1))
        val b = DeterministicRandom(byteArrayOf(2))
        assertTrue(a.nextInt(1_000_000) != b.nextInt(1_000_000) || a.nextInt(1_000_000) != b.nextInt(1_000_000))
    }

    @Test
    fun `nextInt stays within bound`() {
        val r = DeterministicRandom(byteArrayOf(7, 7, 7))
        repeat(500) {
            val v = r.nextInt(10)
            assertTrue("value $v out of range", v in 0..9)
        }
    }

    @Test
    fun `polynomial addition is coefficient-wise mod q`() {
        val a = Polynomial(IntArray(Polynomial.N) { 1 })
        val b = Polynomial(IntArray(Polynomial.N) { 2 })
        val sum = a.add(b)
        assertEquals(3, sum.coeffs[0])
        assertEquals(3, sum.coeffs[Polynomial.N - 1])
    }

    @Test
    fun `polynomial subtraction wraps negatives into the field`() {
        val a = Polynomial(IntArray(Polynomial.N) { 0 })
        val b = Polynomial(IntArray(Polynomial.N) { 1 })
        val diff = a.subtract(b)
        // 0 - 1 mod Q == Q - 1
        assertEquals(Polynomial.Q - 1, diff.coeffs[0])
    }

    @Test
    fun `fromBytes then toBytes round-trips a 32-byte key`() {
        val key = ByteArray(32) { (it * 7 + 3).toByte() }
        val poly = Polynomial.fromBytes(key)
        val recovered = poly.toBytes()
        assertArrayEquals(key, recovered)
    }
}
