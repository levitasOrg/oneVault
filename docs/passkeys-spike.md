# Passkeys (FIDO2 / WebAuthn) — feasibility spike

**Status: research only. No implementation this cycle. GP-9 / OV-14.**
**Date: 2026-07-05.** Ground truth: `minSdk 24`, `targetSdk 36`, Kotlin 2.2.x, AGP 9.1.1.

This memo answers one question: *can OneVault become an Android **passkey provider** —
so it stores and serves WebAuthn credentials the way Google Password Manager or 1Password
do — and should we build it next cycle?* It does **not** design the feature.

> Terminology guard (see `plan-onevault-cloud-backup.md` §1): a **passkey** here is a
> FIDO2/WebAuthn credential. It is unrelated to the **Backup Recovery Phrase** (the "pass
> key" the user types to unlock a cloud backup). Two different things; do not conflate.

---

## 1. What "being a passkey provider" actually requires

Android exposes this through the **Credential Manager** framework
(`androidx.credentials` + `androidx.credentials:credentials`). A third-party manager
participates by implementing a **`CredentialProviderService`**
(`androidx.credentials.provider.CredentialProviderService`), declared in the manifest with
the `android.permission.BIND_CREDENTIAL_PROVIDER_SERVICE` permission and a
`<meta-data>` capabilities file. The user must then enable OneVault under
**Settings → Passwords, passkeys & autofill → additional providers** (the same enrollment
surface as the existing autofill service, but a distinct API).

The service must handle three callbacks:

| Callback | What we must do |
|---|---|
| `onBeginCreateCredentialRequest` | A relying party (app/site) asks to **create** a passkey. For a `CreatePublicKeyCredentialRequest` we parse the WebAuthn `PublicKeyCredentialCreationOptions` JSON, generate a new **EC P-256 key pair**, build an **attestation object** + `clientDataJSON`, return a `CreateEntry` that (after user auth) persists the private key and serves the response. |
| `onBeginGetCredentialRequest` | A relying party asks to **authenticate**. We look up stored passkeys matching the `rpId`, present `CredentialEntry` tiles (one per matching credential), and on selection sign the WebAuthn `authenticatorData‖clientDataHash` challenge with the stored private key, returning a `PublicKeyCredential` assertion. |
| `onClearCredentialStateRequest` | Clear any sign-in state. Trivial for us (stateless). |

Each serve/create is gated behind a `BiometricPrompt` — which OneVault already integrates
(`SecureStore` + biometric unlock), so the auth-gate primitive is in hand.

## 2. What we would have to store, and how it fits our crypto

A WebAuthn credential is small and well-defined:

- **Credential ID** (opaque bytes we choose), **rpId** (e.g. `github.com`),
  **user handle**, **EC P-256 private key**, and a **signature counter**.

This maps cleanly onto the existing model: a **new `passkey` item type** whose
`DecryptedFields` carries the PEM/COSE-encoded private key + rpId + handle + counter,
encrypted with the **same v3 AEAD path** as every other field. No new at-rest crypto is
required. The signing itself is standard JCE: `KeyPairGenerator("EC", ...)` over
`secp256r1` and `Signature("SHA256withECDSA")` — **no new dependency** (mirrors how TOTP
reused `javax.crypto.Mac` in OV-14).

**Better option to evaluate first:** generate the P-256 key in the **AndroidKeyStore**
(`KeyProperties.KEY_ALGORITHM_EC`, `setUserAuthenticationRequired(true)`) so the private
key is hardware-backed and never extractable. Trade-off: KeyStore keys **cannot be exported**,
so they would **not** ride the cloud backup (OV-11/12) — a passkey created on one phone could
not be restored on another. That is the core product tension (see §4).

## 3. Feasibility verdict

**Technically feasible, medium effort.** Everything the provider API needs, OneVault already
has a matching primitive for: biometric gate, AES-GCM field encryption, JCE asymmetric crypto.
The genuinely new work is:

1. WebAuthn wire-format correctness — CBOR/COSE encoding of the attestation object and
   authenticator data, `clientDataJSON` hashing, and the assertion signature format. This is
   fiddly and **must be tested against a real relying party**; a single byte wrong = silent
   auth failure. Budget the bulk of the effort here.
2. Credential Manager provider plumbing (manifest, capabilities meta-data, `PendingIntent`
   entries, enrollment UX).
3. A `passkey` item type end-to-end (create sheet, list tile, detail, delete).

**Dependency:** `androidx.credentials:credentials` (+ `credentials-play-services-auth` only if
we also want to be a *client*; as a *provider* the core artifact suffices). This is a **new
dependency not yet authorized** by `plan-onevault-cloud-backup.md` (which allowed only
`play-services-auth` and `sentry-android`) — adding it needs the standing "document new deps
first" step and a line in that plan.

**minSdk note:** the provider API is fully supported on Android 14+ (API 34) and back-ported to
API 28+ via the Jetpack `androidx.credentials` library with Google Play services. On API 24–27
(part of our `minSdk 24` range) provider support is effectively unavailable — the feature would
be **gated to API 28+**, with password/TOTP remaining the fallback on older devices. Acceptable,
but must be stated in the UI.

## 4. The decision to make next cycle (the real question, not the API)

The API is the easy part. The product decision is **key portability**:

- **Extractable software key** → passkey is included in the encrypted cloud backup and
  restores on a new phone (consistent with our whole backup story), **but** the private key
  exists in app memory when in use — weaker than a hardware key.
- **AndroidKeyStore hardware key** → strongest possible protection, **but** the passkey is
  bound to that one device and is lost on device loss (no restore) — which contradicts the
  "restore everything on a new phone" promise OV-12 just built.

Real managers resolve this by syncing an **encrypted** software key (portability wins for a
consumer password manager). Recommendation for next cycle: **software P-256 key, encrypted in
the vault, included in cloud backup** — portability is the whole reason a user picks a
third-party manager over the platform one. Document the weaker-than-hardware trade-off honestly
in-app, exactly as the QuantumCrypto header and THREAT-MODEL do.

## 5. Recommendation

- **Ship next cycle, not this one.** It is feasible and additive, but the WebAuthn wire-format
  work plus a new dependency and a new item type is more than the GP-9 "spike only" budget.
- **Prerequisites before starting:** (a) authorize `androidx.credentials` in the deps plan;
  (b) decide portability = software key (§4); (c) stand up a test relying party (a WebAuthn
  demo site or `webauthn.io`) as the correctness gate — the "done when" is a passkey created in
  OneVault that logs into that site on a second device.
- **Do not** attempt hardware-key + cloud-restore both at once; pick portability and say so.

*No code was written for this feature. This memo is the deliverable.*
