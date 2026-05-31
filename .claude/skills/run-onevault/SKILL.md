---
name: run-onevault
description: >
  Review, test, and crunch the oneVault Android app.
  Use when asked to: run static analysis, check code quality, review Kotlin,
  analyse APK, crunch APK size, check security flags, count DEX classes,
  run tests, build, or analyse the Android app.
---

# oneVault Android — review · test · crunch

oneVault is a Kotlin + Jetpack Compose Android password-manager app (AGP 9.1.1, minSdk 24, compileSdk 36).
The driver is `.claude/skills/run-onevault/smoke.sh`.

## Prerequisites

```bash
# Install on Ubuntu (one time)
apt-get install -y apktool aapt java
# detekt is downloaded automatically on first run from Maven Central
```

`apktool`, `aapt`, and `aapt2` must be on `PATH`.  
Java 11+ required (`java -version`).  
Note: `dl.google.com` and `maven.google.com` are blocked in the cloud execution environment, so the app **cannot be compiled** — use the pre-built APK at `.build-outputs/app-debug.apk`.

## Agent path — run the driver

Run from the repo root (`/home/user/oneVault` or wherever the repo is checked out).

```bash
# Static code review (detekt on src/main/java)
bash .claude/skills/run-onevault/smoke.sh review

# APK crunch — size, manifest, security, DEX class count (uses .build-outputs/app-debug.apk)
bash .claude/skills/run-onevault/smoke.sh crunch

# APK crunch on a custom APK
bash .claude/skills/run-onevault/smoke.sh crunch /path/to/custom.apk

# Both review + crunch (default)
bash .claude/skills/run-onevault/smoke.sh all

# Test info (explains what's there; full run needs Android SDK)
bash .claude/skills/run-onevault/smoke.sh test
```

### What each mode outputs

**review**
- Top detekt issue categories (MagicNumber, MaxLineLength, LongMethod, CyclomaticComplexMethod, …)
- Issue count per file
- Full report written to `/tmp/detekt-report.txt`
- Tool: detekt 1.23.8 (auto-downloaded to `/tmp/detekt.jar`)

**crunch**
- Package name, minSdk, targetSdk, permissions (via `aapt2 dump badging`)
- Uncompressed size breakdown: DEX / resources.arsc / drawables / native libs / assets / META-INF
- DEX class count per dex file and total (via `apktool d -r`)
- Security flags: `debuggable`, `usesCleartextTraffic`, exported component count, declared permissions

**test**
- Prints the `gradle :app:testDebugUnitTest` command for when an SDK is available
- Lists the three test files: `ExampleUnitTest`, `ExampleRobolectricTest`, `GreetingScreenshotTest`
- Notes the Roborazzi baseline PNG at `app/src/test/screenshots/greeting.png`

## Tests (when Android SDK is available)

The project uses **Robolectric** (JVM Android tests, no device) and **Roborazzi** (screenshot tests).

```bash
# Requires: local.properties with sdk.dir=<path> + Google Maven access
gradle :app:testDebugUnitTest
gradle :app:testDebugUnitTest --tests 'com.example.ExampleUnitTest'
gradle :app:testDebugUnitTest --tests 'com.example.GreetingScreenshotTest'
```

Screenshot baselines live at `app/src/test/screenshots/greeting.png`.  
To record new baselines: `gradle :app:recordRoborazziDebug`.

## Build (when Android SDK is available)

```bash
echo "sdk.dir=/path/to/android-sdk" > local.properties
# Decode the debug keystore (already in repo as base64)
base64 -d debug.keystore.base64 > debug.keystore
gradle :app:assembleDebug
# APK lands in app/build/outputs/apk/debug/
```

`GEMINI_API_KEY` must be set in `.env` (see `.env.example`).

## Gotchas

- **`dl.google.com` / `maven.google.com` are blocked** in this cloud environment. AGP 9.1.1 and the Android SDK platform 36 cannot be downloaded. Building is impossible without a pre-populated Gradle cache or a local SDK mirror.
- **`apktool d -s` skips smali disassembly** — use it only for manifest inspection, not class counting. For DEX class counts, use `-r` (skip resources) instead to keep smali generation.
- **`grep -c` exits with status 1 when there are 0 matches** — pattern `$(grep -c ... file) || count=0` is safer than `$(grep -c ... || echo 0)` (the latter appends a second 0).
- **`google-android-build-tools-34.0.0-installer` is broken** in this container — it tries to download from `dl.google.com` which is blocked. Force-remove it before installing `apktool`:
  ```bash
  dpkg --remove --force-all google-android-build-tools-34.0.0-installer
  apt-get install -y apktool
  ```
- **The Debian `aapt2` binary** is at `/usr/lib/android-sdk/build-tools/debian/aapt2`, not on `PATH`. The script calls it by full path.
- **detekt 1.23.8** on this codebase reports 379 weighted issues; the biggest contributors are `MainActivity.kt` (171) and `FloatingHoverService.kt` (71). Top categories: MaxLineLength, TooGenericExceptionCaught, FunctionNaming, LongMethod.

## Troubleshooting

| Symptom | Fix |
|---|---|
| `apktool: command not found` | `apt-get install -y apktool` (after force-removing the broken build-tools installer) |
| `detekt.jar looks wrong (HTML document)` | Maven Central 404 — check the version in the script matches an existing release |
| `find: '/tmp/.../smali*': No such file or directory` | You used `apktool d -s` which skips smali generation — remove `-s` or use `-r` instead |
| `gradle: Plugin com.android.application not found` | Google Maven is blocked; can't compile. Use the pre-built APK at `.build-outputs/app-debug.apk` |
| `E: Sub-process /usr/bin/dpkg returned an error code (1)` when installing apktool | Force-remove the broken `google-android-build-tools-34.0.0-installer` first (see Gotchas) |
