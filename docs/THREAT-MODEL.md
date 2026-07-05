# OneVault — Threat Model

**GP-9 / OV-14 deliverable. Date: 2026-07-05.** This is an honest, attacker's-eye
description of what OneVault protects, how, and where it is weak. It is written so an
auditor knows exactly what to attack. Where a claim is aspirational or a layer is
cosmetic, this document says so plainly — silence is where products die.

Scope note on **implementation status** (verified against the tree, not memory):
- **Implemented today:** local encrypted vault, biometric unlock, autofill, TOTP (OV-14),
  opt-in crash reporting (GP-1, off by default).
- **Designed but NOT yet built** (`docs/plan-onevault-cloud-backup.md`, OV-9..13): the
  Google Drive cloud backup + restore path. The app currently declares **no `INTERNET`
  permission** and has **no `cloud/` module**. §6 below models that path as *designed* and
  is the security spec for it — it is not a claim that it ships today.

---

## 1. Assets (what an attacker wants)

| Asset | Where it lives | Sensitivity |
|---|---|---|
| Vault items (passwords, card numbers, CVV, PIN, SSN, notes, TOTP secrets) | Room DB, encrypted at rest | Critical |
| Master password | Never stored in plaintext; held in memory (`VaultSession`) only while unlocked | Critical |
| Biometric-unlock copy of the master password | AndroidKeyStore-wrapped via `SecureStore` (AES-256-GCM) | Critical |
| SQLCipher DB passphrase | AndroidKeyStore-wrapped via `SecureStore` | Critical |
| TOTP shared secrets | Encrypted as a normal vault field (`DecryptedFields.totpSecret`) | Critical |
| Backup Recovery Phrase (cloud path, §6) | User memory only — **never** stored or uploaded | Critical |

## 2. Trust boundaries & adversaries considered

1. **Another app on the device (no root).** Sandboxed by Android; cannot read our files,
   KeyStore, or memory. Can observe: the clipboard, the screen (if it has an overlay/a11y),
   and anything we hand to the OS (autofill, notifications).
2. **A thief with the unlocked phone / a shoulder-surfer.** Physical possession, screen on.
3. **A thief with the *locked* phone (offline attacker).** Has the encrypted DB (via ADB
   backup attempt, or a forensic image) and wants to brute-force it.
4. **Root / a compromised OS / a malicious OEM build.** Can read process memory and KeyStore
   usage. **Out of scope for full defense** — no userland password manager survives a
   compromised kernel; we degrade gracefully (root warning, §5) but do not claim protection.
5. **Network attacker (cloud path only, §6).** MITM between phone and Google Drive.
6. **The cloud provider itself (Google).** Holds our backup blob. Must learn *nothing* from it.

## 3. At-rest cryptography (implemented)

The confidentiality of stored items rests on **AES-256-GCM** keyed by a **PBKDF2-HMAC-SHA256**
stretch of the master password (`hybridKDF`, `PBKDF2_ITERATIONS = 210_000`, per-payload random
16-byte salt). This is the load-bearing layer and it is sound.

**Payload versions** (all still decryptable; only v3 is written):
- **v3** — vetted **BouncyCastle ML-KEM-768** (`MLKEMParameters.ml_kem_768`) KEM produces a
  shared secret that is mixed into the PBKDF2 KDF alongside the master password, then
  AES-256-GCM. Format: `v3|iv|kemCiphertext|salt|cipher`.
- **v1/v2** — legacy. v2 = PBKDF2 + AES-GCM; v1 = the original **home-rolled Kyber-like** KEM.
  Retained **only** to decrypt pre-upgrade vaults; never used for new encryption.

**Honest crypto caveat (auditor: read this).** The ML-KEM layer is *defense-in-depth*, not the
source of confidentiality — even if the KEM contributed nothing, the PBKDF2→AES-256-GCM layer
alone protects the data. The v1 home-rolled polynomial KEM (`QuantumCrypto` legacy path,
self-documented at line ~211) is **not** the NIST standard and is **not** independently audited;
it survives only for backward decryption. **Recommendation:** either (a) drop the word
"quantum"/"Kyber" from *user-facing* copy so we don't overclaim, or (b) keep it, but only ever
on the vetted v3 ML-KEM path (already the case for new writes). The marketing must match §3, not
the other way around.

**Database:** Room over **SQLCipher** (`net.zetetic:sqlcipher-android`), passphrase from
`SecureStore.getOrCreateDbPassphrase()` (KeyStore-wrapped). So the DB file is encrypted
*independently* of the per-item payload encryption — two layers.

**Residual risk (offline attacker, adversary #3):** strength reduces to the **master password's
own entropy**. PBKDF2-210k slows but does not stop a weak-password brute force. Mitigation is UX:
encourage a strong master password. Argon2id would raise the cost further and is the recommended
next hardening step (would need a vetted dep; document before adding).

## 4. TOTP (OV-14) — specific analysis

- **Secret at rest:** the base32 seed is stored inside `DecryptedFields`, so it inherits the
  full §3 encryption. It is never written anywhere in plaintext.
- **Code derivation:** RFC-6238 via `javax.crypto.Mac` (HmacSHA1/256/512); no network, no new
  dependency. Codes are computed on demand and held only transiently in the composable.
- **Display/clipboard:** the live code UI shows only the derived code, never the seed. Copying a
  code uses `secureCopyToClipboard(sensitive = true)` → `IS_SENSITIVE` flag (kept out of the
  Android 13+ clipboard preview) and a 30-second auto-clear.
- **Autofill (OV-14):** the OTP heuristic is deliberately conservative (`isOtpToken` matches
  `otp/totp/2fa/mfa/…` but never a bare "code", to avoid CVV/zip/promo fields). Only a
  **context-matched** login (§5 autofill rule) can offer its code, and the code is computed at
  request time — valid for the remaining seconds of its window.
- **Weakness (accepted):** storing the TOTP seed *next to* the password means a single vault
  compromise yields both factors — TOTP here is a convenience, not an independent second factor
  against a *vault* breach. This is the standard, understood trade-off of every password manager
  that also holds TOTP; it still defends against phishing and server-side password leaks. State
  it; do not pretend the two factors are isolated.

## 5. Runtime & platform posture (implemented)

- **`FLAG_SECURE`** on the main window → blocks screenshots and screen-recording of vault
  content (adversary #2).
- **Auto-lock** in `onStop` (guarded against config-change churn) → a backgrounded app relocks;
  `VaultSession` clears the in-memory master password.
- **Clipboard hygiene** → sensitive copies flagged + auto-cleared after 30s (adversary #1).
- **Autofill matching** → credentials are filtered by requesting domain/package (`matchScore`),
  so a Netflix login is never offered on a Google form; a locked vault offers only an
  "unlock" prompt, never data.
- **Biometric unlock** → the master password copy is AES-GCM-sealed in AndroidKeyStore
  (`SecureStore`), not plaintext prefs. Biometric is an *unlock convenience* over the same
  master password, not a separate secret.
- **Root detection** (`DeviceSecurity`) → a **non-blocking warning** on unlock. We do not
  hard-block (false positives on legitimately-rooted devices), and we do **not** claim to defend
  against adversary #4 — the warning is informational.
- **Crash reporting (GP-1)** → `sentry-android`, **off by default**, opt-in toggle. When on:
  `sendDefaultPii=false`, no user ids, breadcrumb scrubbing, no session replay; a `beforeSend`
  scrubber test asserts no secret/URL/token can ride an event. A password manager must never
  phone home silently — off-by-default is the security property here.

**Biometric firewall (platform-wide law):** rewards/monetization logic must never depend on the
biometric path; enforced elsewhere in the platform. Noted here for completeness — not applicable
to the OneVault local app but part of the standing rule set.

## 6. Cloud backup path (DESIGNED — `plan-onevault-cloud-backup.md`, not yet built)

Security spec for when OV-9..13 land. Auditor: this is the design to hold the implementation to.

- **Key model:** `backupKey = PBKDF2-HMAC-SHA256(RecoveryPhrase, random16 salt, ≥210k, 32B)`.
  The Recovery Phrase is chosen by the user, used only to derive this key, and **never uploaded,
  never written to Keystore-for-upload, never logged**. The vault master password is **not** used
  for backup and does not travel.
- **Blob:** `magic "OVBK" | version | kdf params | salt | nonce | AES-256-GCM(vault-export-json)`.
  Google (adversary #6) receives only ciphertext + public KDF params — it cannot decrypt without
  the phrase, which it never sees.
- **Isolation:** stored in Drive **`appDataFolder`** (scope `drive.appdata`) — invisible in the
  user's Drive UI, readable only by this app's OAuth client. Native account picker + consent
  sheet (Identity Authorization API), **no browser/WebView** (the old WebView OAuth was the
  "blank browser" bug and is removed in OV-9).
- **Network (adversary #5):** `INTERNET` added but scoped; a `network-security-config` pins
  cleartext=false and restricts to `*.googleapis.com`; an ArchUnit/test guard asserts no class
  outside `com.example.cloud` opens a socket, so the new permission cannot silently grow into
  telemetry.
- **Recovery-phrase loss:** unrecoverable **by design** — no backdoor, stated at creation and on
  the restore screen. This is a deliberate security property, not a gap.
- **Access token:** held in memory only; never persisted; re-authorized silently on 401.

## 7. Explicitly out of scope / accepted risks

- Compromised OS / root / malicious kernel (adversary #4) — informational warning only.
- Weak master password chosen by the user — mitigated by KDF cost + UX nudges, not eliminated.
- Coercion / rubber-hose — no duress feature in v1.
- Metadata (item *count*, timing) visible to a local attacker with the encrypted DB — the DB is
  encrypted, but size/row-count side channels are not defended.
- Supply chain of our dependencies (BouncyCastle, SQLCipher, Sentry, Play services) — trusted;
  pinned versions; not independently audited by us.

## 8. Recommended next steps (priority order)

1. **Reconcile the "quantum" marketing with §3** — stop overclaiming, or restrict claims to v3.
2. **Consider Argon2id** for the master-password KDF (raises offline-attack cost).
3. **Build OV-9..13** to make §6 real; keep the ArchUnit network-isolation guard as a merge gate.
4. **External security audit** — this is a **user budget decision**. It is presented here, not
   booked. Recommended before any public Play Store release that markets the app as a secure
   password manager, and *required* by Google before the `drive.appdata` sensitive scope is
   approved for a public (non-test) OAuth client.

*This document is the GP-9 "security review" deliverable. It reflects the tree as of
2026-07-05; update it in the same commit as any change to the crypto, key handling, or the
cloud path.*
