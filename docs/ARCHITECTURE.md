# OneVault Architecture Notes

This document records the current architecture, the structural debt identified during the
security/quality review, and a concrete, low-risk plan to address items #25–#27 (file structure,
dependency injection, navigation). These are deliberately **not** done in the same pass as the
security fixes because they are large, cross-cutting refactors and the priority was a working,
hardened build.

## Current state (as built)

- **UI**: Jetpack Compose + Material 3. `MainActivity.kt` is a single ~3000-line file holding every
  screen, dialog, and component.
- **State**: One `VaultViewModel` (AndroidViewModel) created directly via `viewModel()`. It builds
  its own `AppDatabase`/`VaultRepository` — no dependency injection.
- **Navigation**: An `activeTab: Int` state variable inside `DashboardScreen`. No back stack, no
  deep links, no type safety.
- **Data**: Room (now SQLCipher-encrypted) via `AppDatabase` → `VaultDao` → `VaultRepository`.
- **Crypto**: `QuantumCrypto` (homerolled Kyber-like KEM + AES-256-GCM, PBKDF2 KDF), `SecureStore`
  (Keystore-backed secret storage), `VaultSession` (in-memory active password).

## Debt items and recommended target

### #25 — `MainActivity.kt` is monolithic (~3000 lines)

**Recommendation:** split by responsibility into a `ui/` package. Suggested files:

```
ui/
├── OneVaultApp.kt          // top-level Scaffold + routing
├── auth/
│   ├── MasterSetupScreen.kt
│   └── MasterUnlockScreen.kt
├── dashboard/
│   ├── DashboardScreen.kt
│   ├── VaultContentsTab.kt
│   ├── StandaloneGeneratorTab.kt
│   ├── ImportDataTab.kt
│   └── SettingsMenuTab.kt
├── item/
│   ├── ItemManageDialog.kt
│   └── ItemDetailDialog.kt
├── backup/
│   ├── SocialConnectDialog.kt
│   └── CloudRestoreDialog.kt
└── components/
    └── ClipboardComponents.kt   // secureCopyToClipboard, ClipboardDisplayRow, SensitiveClipboardDisplayRow
```

Compose `@Composable` functions move cleanly between files in the same module — the only work is
fixing imports. Do this one screen at a time, compiling between each move, to keep regressions
contained. Start with `components/ClipboardComponents.kt` (zero cross-references) as the safest
first extraction.

### #26 — No dependency injection

**Recommendation:** introduce Hilt.

- Add `com.google.dagger:hilt-android` + the Hilt Gradle plugin (KSP-based).
- Provide `AppDatabase`, `VaultDao`, `VaultRepository`, `SecureStore` from a `@Module`.
- Annotate `VaultViewModel` with `@HiltViewModel` and inject the repository/secure store.
- Annotate `MainActivity` with `@AndroidEntryPoint` and the Application with `@HiltAndroidApp`.

**Risk note:** this project is on AGP 9.1.1 / Kotlin 2.2.10, which is very new. Verify the Hilt
version supports this KSP/Kotlin combination before adopting, or the build will break. Koin
(runtime DI, no codegen) is a lower-risk alternative if Hilt lags the toolchain.

### #27 — No navigation framework

**Recommendation:** adopt `androidx.navigation:navigation-compose` (already declared, currently
commented out in `libs.versions.toml`). Replace the `activeTab` integer with a typed `NavHost`:

```kotlin
@Serializable object Vault
@Serializable object Generator
@Serializable object Import
@Serializable object Menu
```

This gives a real back stack, restores tab state across config changes, and enables deep links
(e.g. open straight to an item from the autofill service).

## Why these were deferred

A working, signed APK with all critical→medium security issues fixed is more valuable than a
half-finished refactor. Each item above is independently shippable and should be done as its own
reviewed change with the build green at every step.
