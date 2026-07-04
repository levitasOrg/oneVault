package com.example

import android.app.Application
import android.content.Context

import io.sentry.Sentry
import io.sentry.SentryOptions
import io.sentry.android.core.SentryAndroid

/**
 * GP-1 crash reporting, held to a password manager's standard: **off by default,
 * never silent, and inert without a compiled-in DSN.**
 *
 * Sentry's automatic ContentProvider init is disabled in the manifest
 * (`io.sentry.auto-init=false`); the SDK only ever starts here, and only when
 * BOTH are true: the user flipped the Settings opt-in, and a real DSN was built
 * in ({@code BuildConfig.SENTRY_DSN}, injected from the environment for release
 * builds). No breadcrumbs are ever attached — they could echo item titles,
 * search text, or decrypted fields.
 */
object CrashReporting {

    private const val PREF_FILE = "onevault_prefs"
    const val PREF_KEY = "crash_reports_enabled"

    fun isEnabled(context: Context): Boolean =
        context.getSharedPreferences(PREF_FILE, Context.MODE_PRIVATE)
            .getBoolean(PREF_KEY, false)

    /** From Application.onCreate — starts Sentry only if the user already opted in. */
    fun initIfEnabled(app: Application) {
        if (isEnabled(app)) start(app)
    }

    /** Runtime toggle: persist the choice and start/stop Sentry immediately. */
    fun setEnabled(app: Application, enabled: Boolean) {
        app.getSharedPreferences(PREF_FILE, Context.MODE_PRIVATE)
            .edit().putBoolean(PREF_KEY, enabled).apply()
        if (enabled) start(app) else Sentry.close()
    }

    private fun start(app: Application) {
        val dsn = BuildConfig.SENTRY_DSN
        if (dsn.isBlank() || Sentry.isEnabled()) return
        SentryAndroid.init(app) { options ->
            options.dsn = dsn
            options.isEnableAutoSessionTracking = true
            options.isSendDefaultPii = false
            options.tracesSampleRate = 0.0
            // Drop every breadcrumb: a vault app must not ship user activity trails.
            options.beforeBreadcrumb = SentryOptions.BeforeBreadcrumbCallback { _, _ -> null }
        }
    }
}
