package com.example.totp

/**
 * RFC-4648 base32 decoder — just enough for TOTP secrets. Case-insensitive,
 * ignores spaces and '=' padding (authenticator secrets are often printed in
 * lower case with spaces every 4 chars).
 */
internal object Base32 {

    private const val ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567"

    fun decode(input: String): ByteArray {
        val clean = input.trim().replace(" ", "").replace("-", "").trimEnd('=').uppercase()
        if (clean.isEmpty()) return ByteArray(0)

        val out = ArrayList<Byte>(clean.length * 5 / 8)
        var buffer = 0
        var bitsLeft = 0
        for (c in clean) {
            val value = ALPHABET.indexOf(c)
            require(value >= 0) { "Invalid base32 character: $c" }
            buffer = (buffer shl 5) or value
            bitsLeft += 5
            if (bitsLeft >= 8) {
                bitsLeft -= 8
                out.add(((buffer shr bitsLeft) and 0xff).toByte())
            }
        }
        return out.toByteArray()
    }
}
