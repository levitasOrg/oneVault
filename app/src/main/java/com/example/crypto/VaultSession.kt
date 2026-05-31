package com.example.crypto

/**
 * Thread-safe singleton containing the active volatile master password.
 * Keeps vault state decrypted in-memory during active phone use.
 */
object VaultSession {
    @Volatile
    private var activePassword: String? = null

    fun isLocked(): Boolean {
        return activePassword == null
    }

    fun unlock(password: String) {
        synchronized(this) {
            activePassword = password
        }
    }

    fun lock() {
        synchronized(this) {
            activePassword = null
        }
    }

    fun getActivePassword(): String? {
        return activePassword
    }
}
