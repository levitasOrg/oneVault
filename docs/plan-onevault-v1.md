# Plan: OneVault v1.0 — Finalize, Harden, Ship (+ theme audits across all apps)

> Decision-complete implementation plan, written for an Opus agent to execute without
> user input. Same contract style as `commuter-platform/docs/plan-reyy-live-voice.md`.
> Goal: OneVault feature-complete, real-use ready, distributable (signed APK for friends
> now; Play-Store-ready checklist done). Plus theme finalization here and theme audits in
> the two commuter apps.
>
> Read `docs/ARCHITECTURE.md` and the repo README first. Build must be green after every
> numbered step: `JAVA_HOME=/opt/homebrew/opt/openjdk@17 ./gradlew :app:assembleDebug
> --no-configuration-cache` and `./gradlew :app:testDebugUnitTest` (separate invocations —
> assembleDebug rejects `--tests`).

## 0. Ground truth (as of 2026-07-04, main = c5e2a9d)

- Security passes done: Keystore-backed SecureStore, PBKDF2 (210k), SQLCipher DB,
  ML-KEM-768 (BouncyCastle 1.81) with v3 payloads (v1/v2 still decrypt), domain-ranked
  autofill + onSaveRequest, clipboard auto-clear, FLAG_SECURE, auto-lock, root warning.
- MainActivity split into `ui/{auth,dashboard,backup,item,components,theme}`.
- **Uncommitted change exists**: `app/src/main/java/com/example/ui/VaultViewModel.kt` is
  modified. FIRST TASK: `git diff` it; if it's a stray/partial edit, discard; if
  coherent, commit alone with an honest message. Do not build anything on top of an
  unreviewed diff.
- Toolchain gotchas (all bite): Gradle 9.3.1 wrapper (AGP 9.1.1 needs ≥9.3.1); JDK 17;
  `--no-configuration-cache` on assemble; debug keystore decoded from
  `debug.keystore.base64` **via python** (macOS `base64 -d` mangles it), pass `android`;
  Robolectric on SDK 36 needs Java 21 → tests pin `@Config(sdk=[35])`.
- Remote: `git@github.com:levitasOrg/oneVault.git` — push allowed for this repo (already
  the norm), but **ask the user before the first push of a release tag**.

## OV-1 Hygiene baseline
1. Resolve the stray VaultViewModel diff (above). 2. Fix `ExampleRobolectricTest` (pin
   sdk 35 or delete it — it's scaffold). 3. Full build + tests green. Commit.

## OV-2 Hilt DI (#26 — deferred earlier as risky; do it now, carefully)
- **Gate first**: verify Hilt ↔ Kotlin 2.2.10 / AGP 9.1.1 / KSP compat (check Hilt
  release notes ≥2.56). If incompatible, STOP this step, record why here, skip to OV-3 —
  DI is internal quality, not a ship blocker.
- Order (build between each): app class `@HiltAndroidApp` → modules providing
  AppDatabase/VaultDao/VaultRepository/SecureStore (SQLCipher passphrase via
  SecureStore stays lazy) → `@HiltViewModel` VaultViewModel → `@AndroidEntryPoint`
  MainActivity → `viewModel()`→`hiltViewModel()` at every call site. Services
  (Autofill/FloatingHover) keep manual wiring unless trivially injectable.

## OV-3 Navigation-Compose (#27)
Replace `activeTab: Int` in DashboardScreen with a typed NavHost (`@Serializable`
routes; dep already in libs.versions.toml). Routes: Vault, Generator, Import, Settings.
Back button behavior: back from any tab → Vault; back from Vault → lock + finish.
State (search text, selected vault) survives tab switches via the shared VaultViewModel.

## OV-4 Feature-completion audit (finalize = every advertised flow works end-to-end)
Audit each; implement what's missing; each gets a manual device-pass line in the commit:
1. **Onboarding/first-run**: create master password (strength meter, confirm twice,
   explicit "cannot be recovered — export backups" warning), optional biometric enroll.
   If no coherent first-run flow exists, build it in `ui/auth`.
2. **Master-password change**: re-encrypt all items (decrypt with old, re-encrypt v3
   with new, single transaction, progress UI, rollback on any failure). If absent, build
   it (Settings). This is the hardest missing piece — test with 100+ items.
3. **Biometric**: enroll/disable in Settings; after OS biometric re-enrollment the
   Keystore key may invalidate → catch `KeyPermanentlyInvalidatedException`, fall back
   to master password, re-enroll silently after next unlock.
4. **Backup/restore round-trip**: export (encrypted file, v3, Storage Access Framework
   — not app-private dir) → wipe app → import → all items identical. Automate the
   crypto part as a unit test; do the SAF part on device.
5. **Import**: CSV (Chrome/Bitwarden headers) + previous OneVault export; malformed
   rows are skipped with a per-row report, never a crash; user picks target vault (#23 done).
6. **Autofill**: fill + save on 3 real apps/sites; ranked by domain (#11 done); verify
   locked-vault behavior = an "Unlock OneVault" dataset that deep-links to unlock.
7. **Hover service**: permission rationale + graceful denial; pagination (#33 done);
   kill/restart survives.
8. **Auto-lock timeout setting** (1/5/15 min + on-screen-off). Session cleared on lock.
9. **Password generator**: length/symbols/digits toggles; strength meter shared with
   onboarding; "history of generated" is explicitly OUT (never persist generated pw).
10. **Search** across decrypted fields (#34 done) — verify perf with 500 items (must
    stay <200 ms; if not, memoize decrypted index while unlocked).

## OV-5 OneVault theme — "Paper Ledger" (USER-SELECTED 2026-07-04, replaces steel&brass)
Current: stock M3 blue (`GeoPrimary #0061A4`). Replace with the private-banker's-ledger
system — **no purple, no green** (standing user preference across all apps):
- Light: bg `#FAF6EE` (warm paper), surface `#FFFFFF`, onBg `#241F1A` (ledger ink),
  primary `#8C2F23` (oxblood), primaryContainer `#F3E4E0`, tertiary/accent `#C9A227`
  (old gold — reserved for unlock/success moments), secondaryContainer `#EFE8D8`,
  muted text `#7A7264`, error `#B3402E`, outline `#E5DCC9`.
- Dark ("midnight ledger"): bg `#171310`, surface `#201B16`, onBg `#EDE6D9`,
  primary `#C05A4B` (lifted oxblood), primaryContainer `#3A231E`, accent `#D9B34A`,
  muted `#9A9184`, outline `#33291F`.
- **Wordmark** (code component like Reyy's, in `ui/components/`): "One" roman 800 ink +
  "Vault" italic 400 oxblood + an outlined padlock glyph with an oxblood keyhole dot;
  used on lock screen (large, centered) and dashboard top bar (small).
- `dynamicColor = false`; both themes wired through `ui/theme/{Color,Theme}.kt`; every
  screen checked in BOTH modes (grep `Color(0x` outside ui/theme, fix hits).
- Typography: system fonts; titles 700–800; the italicized "Vault" is styled spans, not
  a font asset (custom display font optional later via Compose FontFamily).
- **App icon**: ledger-book motif — warm-paper rounded square, ink-outlined book/lock
  hybrid with oxblood spine and gold keyhole dot. SVG → 1024 PNG via
  `qlmanage -t -s 1024 -o . icon.svg`, adaptive foreground + monochrome layer.
- Lock screen brand moment: the gold keyhole dot turns like a key on successful unlock
  (≤300 ms, skipped under reduced-motion).

## OV-6 Hardening for real users
1. `android:allowBackup="false"` + `dataExtractionRules` excluding everything (Keystore
   keys don't transfer; a restored DB would be undecryptable — better to force export/import).
2. R8 on for release; keep rules for BouncyCastle (`-keep class org.bouncycastle.**`),
   SQLCipher, Moshi (generated adapters), Room. Release build must pass the OV-4 device
   list — R8 breakage is the #1 release risk here.
3. Manifest audit: autofill + overlay + biometric permissions only; no INTERNET
   permission unless something truly needs it — **the app's pitch is local-only; if
   nothing needs INTERNET, remove it and say so in the Play data-safety answers.**
4. Dependency review: `./gradlew :app:dependencies` — no snapshot/alpha in release path
   (AGP 9.1.1 itself is bleeding-edge: acceptable, documented).
5. Lint: `./gradlew :app:lintDebug` clean or each suppression justified inline.
6. StrictMode in debug builds only.
7. Threat-model doc paragraph in README: what OneVault protects against (device theft
   locked, offline bruteforce via PBKDF2+ML-KEM), what not (compromised OS, evil maid
   unlocked, forgotten master password = data gone by design).

## OV-7 Release engineering
1. `versionCode 1`, `versionName "1.0.0"`; release signing config reading from
   `keystore.properties` (gitignored). **User action**: generate the release keystore —
   put the exact `keytool` command in the README and stop for the user to run it (never
   generate or store their release key yourself).
2. Outputs: signed `app-release.apk` (sideload/friends) + `.aab` (Play).
3. Play readiness checklist (do the artifacts, user does the console): privacy policy
   markdown in `docs/PRIVACY.md` (local-only storage, no analytics, no network, autofill
   data never leaves device), data-safety answers pre-written, screenshots list (unlock,
   vault, generator, autofill, dark mode), content rating notes.
4. Distribution note for "give to others" now: the signed release APK + a one-paragraph
   install guide (unknown-sources) appended to README.
5. Tag `v1.0.0` — ask the user before pushing the tag.

## OV-8 Tests (gate for done)
- Unit: crypto round-trip v1/v2/v3 decrypt + tamper rejection (exists, extend for
  master-password-change re-encryption); DAO CRUD under SQLCipher (Robolectric sdk 35);
  import parser malformed-row matrix; generator charset/length properties.
- Device pass (record in the release commit): OV-4 items 1–10 on one physical device,
  light + dark, plus process-death-while-unlocked (relock, no crash, no data loss).

## Edge cases (implement/verify every row)
biometric key invalidated after new fingerprint · device without biometrics ·
process death mid-add (Room txn = no partial item) · rotation during unlock dialog ·
import of own export re-import (dupes: skip-if-identical, else keep-both) ·
export to Downloads then app uninstall/reinstall (import must restore fully — the whole
recovery story) · SQLCipher passphrase lost via Keystore wipe (factory reset): DB
unreadable → detect open failure → offer wipe+restore-from-export screen, never crashloop ·
clipboard cleared even if app killed within 30 s (WorkManager or accept-and-document) ·
autofill in own app suppressed · hover bubble over keyboard · 2000-item vault perf ·
screenshots blocked (FLAG_SECURE) but Play screenshots need debug flavor with flag off ·
root warning shown once per unlock, not nagging.

## CT — Commuter apps theme audit (same session or after OneVault)
- **CT-1 Slowbeat (USER-SELECTED 2026-07-04: "Porcelain & Tide+", option A)**: keep the
  shipped palette, add craft + dark mode. (a) Wordmark code component (like Reyy's
  `Wordmark.tsx`): "Slow" 800 ink + "beat" 300 tide blue + terracotta pulse dot, plus a
  thin underline carrying one heartbeat blip; used on auth (large) and as the Routes
  header brand. (b) Dark variant, same tokens: porcelain→`#15171A`, card `#1E2126`,
  tide blue lifts to `#5E9BC8`, terracotta stays `#DB6B4F`, ochre `#C99B4A`, borders
  `#2A2E33`. (c) Theme switch identical to Reyy's (Auto/Light/Dark pill — port
  `ThemeContext.tsx`). Verify every screen both modes; typecheck; commit.
- **CT-2 Reyy**: Pop Radio is committed; audit only — contrast of `#6b6b6b` on cream
  (borderline; bump to `#5E5E5E` if <4.5:1), the muted color in dark (`#A5A099` ok),
  and confirm `usesCleartextTraffic` + `BUILD.devAuth` are OFF in any store build
  (they're test-only; `scripts/build-apk.sh` handles variants).
- **CT-3 Cross-app**: icons consistent in tone (Slowbeat calm-line, Reyy on-air block,
  OneVault brass dial); no purple/green anywhere; document all three palettes in one
  `~/Projects/DESIGN.md` for future work.

## Instructions for the implementing agent (Opus)
1. Work order: OV-1 → OV-4 → OV-5 → OV-6 → OV-7 → OV-8, with OV-2/OV-3 slotted after
   OV-1 **only if** the Hilt compat gate passes; CT-1..3 last. Commit per OV step,
   repo message style, `Co-Authored-By: Claude <model> <noreply@anthropic.com>`.
2. Build + unit tests green before every commit (two separate gradle invocations).
3. Never weaken crypto or remove the legacy v1/v2 decrypt paths; never log secrets,
   passwords, or payloads; FLAG_SECURE and auto-lock are non-negotiable.
4. No new dependencies beyond Hilt (OV-2) and WorkManager (only if chosen for the
   clipboard edge case) without documenting here first.
5. Machine facts: JAVA_HOME `/opt/homebrew/opt/openjdk@17`; Android SDK
   `~/Library/Android/sdk`; Docker via colima (irrelevant here); commuter repos are
   LOCAL-ONLY (no push); oneVault pushes to its SSH remote after asking once.
6. This file is the contract — a found gap gets added here with its resolution in the
   same commit. If a step is impossible (toolchain), record why here and move on;
   never fake completion.
7. Related plans: `commuter-platform/docs/plan-reyy-live-voice.md` (Reyy live-voice
   backend, phases LV-1..6) and `commuter-platform/docs/plan-slowbeat-reyy.md` (original
   platform plan). This document + those two are the complete work queue.
