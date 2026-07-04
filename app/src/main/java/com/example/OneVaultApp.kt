package com.example

import android.app.Application

/**
 * Application entry point. Its only job today is GP-1: honour the user's
 * crash-reporting opt-in at startup (default off — see {@link CrashReporting}).
 */
class OneVaultApp : Application() {
    override fun onCreate() {
        super.onCreate()
        CrashReporting.initIfEnabled(this)
    }
}
