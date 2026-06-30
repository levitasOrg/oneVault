package com.example.crypto

import java.io.File

/**
 * Lightweight, best-effort root/jailbreak detection.
 *
 * Root detection can never be perfect (a determined attacker can defeat any client-side check),
 * so this is used only to *warn* the user — never to hard-block the app. On a rooted device the
 * OS sandbox protections OneVault relies on (private SharedPreferences, Keystore isolation) are
 * weaker, so the user deserves to know.
 */
object DeviceSecurity {

    private val SU_PATHS = arrayOf(
        "/system/bin/su",
        "/system/xbin/su",
        "/sbin/su",
        "/system/app/Superuser.apk",
        "/system/xbin/daemonsu",
        "/data/local/bin/su",
        "/data/local/xbin/su",
        "/su/bin/su"
    )

    private val ROOT_APP_PACKAGES = arrayOf(
        "com.topjohnwu.magisk",
        "eu.chainfire.supersu",
        "com.koushikdutta.superuser"
    )

    /** Returns true if the device shows common signs of being rooted. */
    fun isDeviceRooted(): Boolean {
        return hasSuBinary() || hasTestKeysBuild() || hasRootManagementApp()
    }

    private fun hasSuBinary(): Boolean = SU_PATHS.any { File(it).exists() }

    private fun hasTestKeysBuild(): Boolean {
        val tags = android.os.Build.TAGS
        return tags != null && tags.contains("test-keys")
    }

    private fun hasRootManagementApp(): Boolean {
        // Existence of the Magisk path is the cheapest signal without needing PackageManager here.
        return ROOT_APP_PACKAGES.any { File("/data/data/$it").exists() }
    }
}
