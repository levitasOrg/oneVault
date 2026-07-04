# Plan: OneVault — Real Cloud Backup, Restore-on-New-Phone, TOTP, Passkeys, Crash Reports

> Decision-complete implementation plan for an Opus agent to execute without user input.
> Same contract style as `plan-onevault-v1.md` and `commuter-platform/docs/plan-gaps-hardening.md`.
> This is the **OV-9..OV-14** block; it runs **after** OV-1..8 of `plan-onevault-v1.md`
> and **supersedes** the "remove INTERNET" line in OV-6 (reconciled in OV-13 below).
>
> Build gate after every numbered step (two separate invocations):
> `JAVA_HOME=/opt/homebrew/opt/openjdk@17 ./gradlew :app:assembleDebug --no-configuration-cache`
> and `./gradlew :app:testDebugUnitTest`.
> Standing laws still apply: never weaken crypto, keep v1/v2/v3 decrypt paths, never log a
> secret, ask the user before pushing a release tag.

---

## 0. Ground truth (verified 2026-07-04, main = 12b5269)

Confirmed by reading the code, not memory:

- **The "social login" is a dead vestige.** `app/src/main/java/com/example/ui/backup/BackupDialogs.kt`
  line 86 documents that the *previous* implementation loaded the real
  Google/Microsoft/Yahoo login pages inside a WebView. That WebView is what the user still
  sees as "a browser that opens and shows nothing" (no real OAuth client is registered, so
  the page never resolves). The current in-tree `SocialConnectDialog` / `SocialConnectButton`
  are an **honest local-profile picker** — they collect a name + email and nothing else.
- **`connectSocialCloud(provider, email, name)`** (`VaultViewModel.kt:130`) writes
  `cloud_logged_in_*` keys to **SharedPreferences**. No network, no token, no account.
- **`backupToCloud(email)`** (`VaultViewModel.kt:168`) encrypts the vault and stores the
  blob under `cloud_backup_email_<email>` in **SharedPreferences on this device** — the code
  comment at line 193 says so explicitly: *"stored locally on this device (encrypted), not
  uploaded to any remote server."* So "restore on a new phone" is currently **impossible** —
  the backup never leaves the handset. That is the real bug behind "the feature is not working."
- **No `INTERNET` permission.** `AndroidManifest.xml` declares only `SYSTEM_ALERT_WINDOW`
  (+ `BIND_AUTOFILL_SERVICE` on the service). Real cloud upload requires adding `INTERNET`.
- **Deps already on the classpath** (`gradle/libs.versions.toml`): `okhttp 4.10.0`,
  `retrofit 2.12.0`, `converter-moshi`, `play-services-location 21.3.0`, `accompanist-permissions`.
  → Drive REST can ride the existing okhttp/retrofit. Only the **sign-in / token** piece needs a new dep.
- **Crypto honesty flag:** `crypto/QuantumCrypto.kt:205` self-describes as a *"homerolled,
  educational approximation of Kyber/ML-KEM … not the NIST-standardized … not independently
  audited."* Backup encryption below is keyed by a real, standard KDF+AEAD path (OV-11) and does
  **not** depend on the PQC layer for confidentiality; the threat-model note in OV-14 records this.

---

## 1. What the user asked for vs. what exists

User's desired flow: **pick an on-device account (native Android picker, not a browser) →
grant access to cloud storage → an encrypted backup, locked by *their* pass key, is uploaded →
on a new phone, enter the pass key → everything decrypts and restores.**

Gap: today there is no account, no upload, no restore — only a labelled local slot. This plan
builds the real thing for **Google Drive** and is honest about the other two providers (§2).

**Terminology (used consistently below):**
- **Backup Recovery Phrase** = the user's "pass key" for backups. A secret *they* choose,
  used only to derive the backup encryption key. **Never uploaded, never leaves the device.**
  Distinct from the vault master password (which also stays local and is *not* used for backup).
- **Passkey (FIDO2/WebAuthn)** = the separate, unrelated feature in OV-14. When the user says
  "pass key" for backup they mean the Recovery Phrase; the FIDO2 feature is additive.

---

## 2. Decisions (each with the losing option and why)

| # | Decision | Rejected alternative → why |
|---|----------|----------------------------|
| D1 | **Sign-in = Google Identity *Authorization* API** (`Identity.getAuthorizationClient()` + `AuthorizationRequest` for scope `drive.appdata`). Shows the **native account chooser + native consent sheet — no browser/WebView.** Returns an OAuth access token. | (a) WebView/Custom-Tab OAuth → this is exactly the broken thing; a browser is what the user rejected. (b) Credential Manager "Sign in with Google" → returns an *ID token* for auth, **not** a Drive access token with a storage scope; wrong tool. (c) Bare `AccountManager.newChooseAccountIntent` → picks the account but still needs `GoogleAuthUtil`/play-services-auth to mint a Drive token, so it buys nothing over D1. |
| D2 | **Storage = Google Drive REST v3, `appDataFolder`** (`spaces=appDataFolder`). Hidden, app-private folder; the backup is invisible in the user's Drive UI and only this app (this OAuth client) can read it. | User's visible "My Drive" root → clutters their Drive, users delete it by mistake, and it needs the broad `drive.file`/`drive` scope. `appDataFolder` needs only `drive.appdata`. |
| D3 | **OneDrive = phase 2, browser-based, honestly flagged.** Microsoft Graph requires MSAL, an Azure app registration, and MSAL uses a **Custom Tab (a browser)** — Android has no on-device Microsoft-account picker equivalent to Google's. So OneDrive *cannot* meet the "no browser" bar; ship it later, clearly labelled "opens a Microsoft sign-in page." | Faking a native picker for Microsoft → impossible; pretending it's seamless → dishonest. |
| D4 | **Yahoo = dropped. Say so in the UI copy.** Yahoo has **no consumer file-storage / drive API** — there is nowhere to put a backup. The old "Yahoo" button was cargo-cult. Remove it. | Keeping a dead Yahoo button → the exact class of vestige this plan exists to kill. |
| D5 | **Backup crypto = existing v3 AEAD, re-keyed from the Backup Recovery Phrase via PBKDF2 (≥210k, per-backup random salt).** The master password and any Keystore secret are **never** used for backup and **never** uploaded. Drive holds only: version header + KDF salt/params + AEAD nonce + ciphertext. | Deriving the backup key from the master password → then the master password effectively travels (change it and old backups die; reuse across devices leaks it). Uploading any key material → defeats the whole model. |
| D6 | **New dependency: `com.google.android.gms:play-services-auth` only.** Needed for D1 (native picker + Authorization API + token). Drive HTTP uses existing okhttp. No Drive SDK, no MSAL in v1. | Full `google-api-services-drive` client → drags in a large transitive tree and its own HTTP stack for what is 3 REST calls. |

---

## OV-9 — Delete the dead OAuth/fake-cloud (honesty first)

Do this before adding anything, so no fake path lingers behind a real one.

- Remove the WebView-OAuth remnants entirely (any `WebView`/Custom-Tab launch for
  "login"), the Yahoo button (D4), and the SharedPreferences "cloud profile" simulation
  (`connectSocialCloud`, `logoutCloud`, `cloud_logged_in_*`, `cloud_connected_*` keys).
- Keep `backupToCloud`'s **encryption** logic but it is superseded by OV-11's keying; the
  local-SharedPreferences *storage* is replaced by Drive. If a local encrypted-file backup
  (SD/Downloads, master-password-keyed) already exists from OV-1..8, **leave it** — it's a
  legitimate offline path and complements cloud. Only the *fake "cloud"* is removed.
- Migration: on first launch after update, if `cloud_backup_email_*` blobs exist in prefs,
  surface a one-time notice — "Your previous on-device 'cloud' backup was never uploaded.
  Create a real cloud backup now?" — and delete the stale keys after the user acknowledges.
  Never silently drop data the user believed was a backup.
- **Done when:** no `WebView`, no Yahoo, no `cloud_logged_in_*` remain (grep clean); app builds;
  the Backup screen shows only real options.

## OV-10 — Native Google account picker + Drive authorization (no browser)

- Add `play-services-auth` (D6). New module `cloud/` with a `CloudBackupProvider` interface
  (`signIn(): Account`, `authorize(): AccessToken`, `upload(bytes)`, `list()`, `download(id)`,
  `signOut()`) and a `GoogleDriveBackupProvider` implementation. The interface is the
  OneDrive/phase-2 swap point (mirror the Reyy `VoiceRoom` port pattern).
- Sign-in: `AuthorizationRequest.builder().setRequestedScopes(listOf(Scope("https://www.googleapis.com/auth/drive.appdata")))`,
  launch via `authorizationClient.authorize(...)`; if `result.hasResolution()` launch the
  returned `PendingIntent` — this is the **native account chooser + consent sheet**. Cache the
  access token in memory only; on 401, re-authorize silently (no `PendingIntent` needed if
  consent already granted).
- Permissions UX: this replaces "log in with your social profile." Copy: "Choose a Google
  account to store your encrypted backup. OneVault can only see files it creates — never your
  other Drive files." No distances, no dark patterns.
- **Done when:** tapping "Back up to Google Drive" shows the OS account list (not a browser),
  and after consent the app holds a Drive access token (log *"token acquired"*, never the token).

## OV-11 — Encrypted backup upload

- **Key derivation:** prompt for the Backup Recovery Phrase (create + confirm on first backup;
  enforce a minimum strength — ≥ 10 chars or a 4-word phrase; show a "write this down, it is the
  ONLY key" warning). `backupKey = PBKDF2-HMAC-SHA256(phrase, salt=random16, iters≥210_000, 32B)`.
- **Payload:** `magic "OVBK" | version=1 | kdf=pbkdf2 | iters | salt(16) | nonce(12) | AES-256-GCM(vault-export-json)`.
  Reuse the existing v3 vault export/serialization for the plaintext. The Recovery Phrase and the
  derived key are **never** written to Drive, Keystore-for-upload, or logs.
- **Upload:** Drive REST multipart to `appDataFolder`, fixed name `onevault-backup.ovb`
  (`POST /upload/drive/v3/files?uploadType=multipart&supportsAllDrives=false`, parent
  `appDataFolder`). Keep **the last 3 versions** (list by name, sort by `modifiedTime`, delete
  older) so a corrupt/interrupted upload never destroys the only copy.
- **Done when:** a backup round-trips — upload, then a fresh install on the **same** device
  restores it (OV-12) with the correct phrase; wrong phrase fails cleanly.

## OV-12 — Restore on a new phone

- Entry point in onboarding/first-run: **"Restore from cloud."**
- Flow: native Google picker (OV-10) → authorize `drive.appdata` → `list()` appDataFolder →
  if `onevault-backup.ovb` present, download ciphertext → prompt for Backup Recovery Phrase →
  derive key → AES-GCM decrypt → **import** items into a fresh vault. The new device sets its
  **own new master password** locally; the restore does not carry the old master password
  (D5) — items are re-encrypted at rest under the new device's Keystore/SQLCipher as usual.
- **Done when:** phrase-correct restore reproduces every item (count + spot-check assertion in an
  instrumented/unit test with a fixture backup); wrong phrase yields "Recovery phrase is
  incorrect" with **zero** partial import.

## OV-13 — Permissions & network hardening (supersedes OV-6 "remove INTERNET")

- **Add `<uses-permission android:name="android.permission.INTERNET"/>`.** This directly
  reverses the OV-6 note; record the reversal in that plan too. Cloud backup makes INTERNET
  legitimately required — but *only* for the `cloud/` module.
- Add a `network-security-config` that allows cleartext = false and restricts to
  `*.googleapis.com` (+ `oauth2.googleapis.com`); reference it from the manifest.
- ArchUnit/test guard: assert no class **outside** `com.example.cloud` opens a socket /
  references okhttp `Call`, so INTERNET can never quietly grow into telemetry elsewhere.
- Autofill/`FLAG_SECURE`/allowBackup=false from OV-6 stand unchanged.
- **Done when:** INTERNET present, network-security-config enforced, guard test green.

## OV-14 — Fold-in of the previously suggested items (GP-9)

- **TOTP (RFC-6238), no new dep.** New item type `totp` storing the base32 secret (encrypted
  like any field). Compute HOTP/TOTP with `javax.crypto.Mac` (HmacSHA1/256), 30s window, 6/8
  digits. UI: current code + countdown ring; autofill exposes the *current* code. Manual secret
  entry in v1; QR scan only if a no-network barcode decoder is already available — else defer,
  documented. Unit tests against the RFC 6238 vectors.
- **Passkeys (FIDO2/WebAuthn) — spike only, no implementation.** Write `docs/passkeys-spike.md`:
  feasibility of OneVault as an Android **Credential Manager provider** (`CredentialProviderService`),
  what storing/serving a WebAuthn credential entails, and the decision on whether it lands next
  cycle. (This is separate from the Backup Recovery Phrase — see §1.)
- **Crash reporting / "report gathering" — Sentry, OFF by default (GP-1).** Add `sentry-android`;
  a Settings toggle "Share crash reports" defaulting **off** (a password manager must never phone
  home silently). When on: `sendDefaultPii=false`, no user ids, scrub breadcrumbs of text inputs,
  no session replay; a `beforeSend` scrubber test proves no secret/URL/token can ride an event.
- **Threat model.** Extend/`create docs/THREAT-MODEL.md`: cover the backup path
  (PBKDF2→AES-GCM, phrase-only recovery, appDataFolder isolation, token handling) **and** the
  honest note that `QuantumCrypto` is a homerolled PQC approximation (§0) — backup confidentiality
  does **not** rely on it; recommend either dropping the "quantum" claim from user-facing copy or
  replacing the layer with BouncyCastle's real ML-KEM. Present an external audit as a user budget
  decision; do not book it.

---

## Edge-case catalog (must be handled, not just listed)

| Scenario | Required behavior |
|---|---|
| Wrong Recovery Phrase on restore | AEAD tag fails → "Recovery phrase is incorrect"; no partial import; allow retry. |
| No backup in the chosen account | "No OneVault backup found in this Google account." Offer to pick a different account. |
| Multiple backup versions | Restore the newest by `modifiedTime`; offer a "choose an earlier version" list (last 3 kept). |
| Interrupted / partial upload | Never overwrite the previous good file in place; upload new, verify 200 + returned id, *then* prune old. |
| Drive quota exceeded (403) | Clear message; keep local vault intact; suggest freeing space or local export. |
| Token expired / revoked mid-session | Silent re-authorize; if consent withdrawn, fall back to the `PendingIntent` picker. |
| Offline during backup/restore | Detect no connectivity up front; queue nothing silently — tell the user and let them retry. |
| Account has no Google Play services | Feature disabled with an explanation; local export still offered. |
| User forgets the Recovery Phrase | Unrecoverable **by design** — state this at creation time and on the restore screen. No backdoor. |
| Keystore wiped / factory reset | Irrelevant to restore: the backup key derives from the phrase, not Keystore — restore still works. |
| Stale local "cloud" prefs from old build | OV-9 migration notice, then delete keys. |

---

## New dependencies authorized by this plan

- `com.google.android.gms:play-services-auth` (OV-10, D6).
- `io.sentry:sentry-android` (OV-14, GP-1).
- Nothing else. OneDrive (MSAL) and any Drive SDK are explicitly **not** added in v1.

## Order & gates

OV-9 → OV-10 → OV-11 → OV-12 → OV-13 → OV-14 (TOTP, then Sentry, then spike/threat-model docs).
Each step ends green on both build commands in the preamble; OV-11/12 gated by the round-trip test.

## User-owned actions (print exact steps, never perform)

Google Drive backup **cannot work until the user registers an OAuth client** — this is the real
reason a blank browser appeared before (no client existed). Provide these verbatim:

1. Google Cloud Console → create/choose a project → **enable the Drive API**.
2. **OAuth consent screen**: External; add scope `.../auth/drive.appdata`; while testing, add
   friends' emails under **Test users** (up to 100, no Google verification needed). *For a public
   Play release, the `drive.appdata` sensitive scope requires Google verification — flag this, it
   is a real gate, not optional.*
3. **Credentials → OAuth client ID → Android**: package name `com.example…` (the real
   `applicationId`) + the signing cert **SHA-1** (`keytool -list -v -keystore <release.jks>`).
   One client per signing key (debug vs release differ).
4. No client secret is embedded in the app (Android clients are public).

Other standing user actions from `plan-onevault-v1.md` (release keystore, first release-tag push
approval) still apply. **Ask before the first release-tag push to `git@github.com:levitasOrg/oneVault.git`.**

## Commit

`docs: plan real cloud backup + restore, TOTP, passkeys spike, crash reports (OV-9..14)` —
this doc only. Implementation lands in later commits per the order above.
