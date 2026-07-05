package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "vault_items")
data class VaultItem(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val title: String,
    val category: String, // "LOGIN", "CARD", "NOTE", "IDENTITY"
    val vaultName: String = "Personal", // "Personal", "Work", "Private"
    val website: String = "",
    val encryptedPayload: String, // Kyber KEM + AES hybrid encrypted payload
    val isFavorite: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

// Helper models for decrypted types simple storage
data class DecryptedFields(
    // Common fields
    val username: String = "",
    val secretText: String = "", // Password or note body
    val website: String = "",

    // Two-factor (TOTP) — base32 secret from an authenticator "otpauth://" QR.
    // Stored encrypted like every other field; the live 6/8-digit code is derived
    // on the fly (see com.example.totp.Totp). Blank ⇒ item has no 2FA.
    val totpSecret: String = "",
    
    // Credit card fields
    val cardNumber: String = "",
    val cardholderName: String = "",
    val expiry: String = "",
    val cvv: String = "",
    val pin: String = "",

    // Identity fields
    val fullName: String = "",
    val email: String = "",
    val phone: String = "",
    val ssn: String = "",
    val address: String = "",

    // Extra custom flexible fields (JSON or comma separated)
    val customNotes: String = ""
)
