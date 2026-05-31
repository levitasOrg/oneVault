#!/usr/bin/env bash
# smoke.sh — review, test, and crunch the oneVault Android app
# Usage:
#   ./smoke.sh review          — detekt static analysis on src/main/java
#   ./smoke.sh crunch [APK]    — APK size + manifest + security analysis
#   ./smoke.sh test            — run unit tests (requires Android SDK)
#   ./smoke.sh all             — review + crunch (default if no arg)
#
# Run from repo root.

set -euo pipefail

REPO_ROOT="$(cd "$(dirname "$0")/../../.." && pwd)"
DEFAULT_APK="$REPO_ROOT/.build-outputs/app-debug.apk"
DETEKT_JAR="/tmp/detekt.jar"
DETEKT_VERSION="1.23.8"
DETEKT_URL="https://repo1.maven.org/maven2/io/gitlab/arturbosch/detekt/detekt-cli/${DETEKT_VERSION}/detekt-cli-${DETEKT_VERSION}-all.jar"

# ── helpers ────────────────────────────────────────────────────────────────────

hr()  { printf '\n%s\n' "════════════════════════════════════════════════════"; }
hdr() { hr; printf '  %s\n' "$1"; hr; }

require() {
  for cmd in "$@"; do
    command -v "$cmd" &>/dev/null || { echo "❌  Missing: $cmd  (run: apt-get install $cmd)"; exit 1; }
  done
}

ensure_detekt() {
  if [[ ! -f "$DETEKT_JAR" ]]; then
    echo "→ Downloading detekt $DETEKT_VERSION …"
    curl -fsSL "$DETEKT_URL" -o "$DETEKT_JAR"
  fi
  # Sanity: should be a ZIP/JAR, not an HTML error page
  file "$DETEKT_JAR" | grep -q "Zip\|Java archive" || {
    echo "❌  detekt.jar looks wrong ($(file "$DETEKT_JAR")), removing and retrying"
    rm -f "$DETEKT_JAR"
    curl -fsSL "$DETEKT_URL" -o "$DETEKT_JAR"
  }
}

# ── review ─────────────────────────────────────────────────────────────────────

do_review() {
  hdr "STATIC ANALYSIS — detekt $DETEKT_VERSION"
  require java
  ensure_detekt

  SRC="$REPO_ROOT/app/src/main/java"
  REPORT="/tmp/detekt-report.txt"

  set +e
  java -jar "$DETEKT_JAR" --input "$SRC" --report "txt:$REPORT" 2>&1 | tail -3
  set -e

  echo ""
  echo "── Top issue categories ──"
  awk '{print $1}' "$REPORT" | sort | uniq -c | sort -rn | head -10

  echo ""
  echo "── Issue count by file ──"
  grep -oP '(?<=at ).*?(?=:\d+:\d+)' "$REPORT" \
    | sed "s|$REPO_ROOT/||" \
    | sort | uniq -c | sort -rn | head -10

  echo ""
  echo "Full report: $REPORT"
}

# ── crunch ─────────────────────────────────────────────────────────────────────

do_crunch() {
  local apk="${1:-$DEFAULT_APK}"
  [[ -f "$apk" ]] || { echo "❌  APK not found: $apk"; exit 1; }
  require aapt2 apktool unzip

  hdr "APK CRUNCH — $(basename "$apk")  ($(du -sh "$apk" | cut -f1))"

  echo "── Manifest metadata ──"
  aapt2 dump badging "$apk" 2>&1 \
    | grep -E "^package:|sdkVersion|targetSdk|uses-permission:|application-label:'" \
    | head -20

  echo ""
  echo "── Size breakdown ──"
  unzip -l "$apk" | awk 'NR>3 && NF>3 {
    size=$1; file=$NF
    if (file ~ /\.dex$/)          dex+=size
    else if (file ~ /^res\//)     res+=size
    else if (file ~ /^lib\//)     lib+=size
    else if (file ~ /^assets\//)  assets+=size
    else if (file ~ /resources\.arsc/) arsc+=size
    else if (file ~ /^META-INF\//)     meta+=size
    else                          other+=size
  } END {
    total = dex+res+lib+assets+arsc+meta+other
    printf "  DEX bytecode :  %6.1f MB\n", dex/1048576
    printf "  resources.arsc: %6.1f KB\n", arsc/1024
    printf "  Drawables/res:  %6.1f KB\n", res/1024
    printf "  Native libs:    %6.1f KB\n", lib/1024
    printf "  Assets:         %6.1f KB\n", assets/1024
    printf "  META-INF:       %6.1f KB\n", meta/1024
    printf "  Other:          %6.1f KB\n", other/1024
    printf "  ─────────────────────────\n"
    printf "  Uncompressed:   %6.1f MB\n", total/1048576
  }'

  echo ""
  echo "── DEX class count ──"
  DECODED=$(mktemp -d)
  # Decode smali (no -s flag) but skip resources (-r) to be fast
  apktool d -f -r "$apk" -o "$DECODED" 2>/dev/null
  TOTAL=0
  while IFS= read -r -d '' d; do
    count=$(find "$d" -name "*.smali" | wc -l)
    TOTAL=$((TOTAL + count))
    printf "  %-20s %d classes\n" "$(basename "$d")" "$count"
  done < <(find "$DECODED" -maxdepth 1 -name "smali*" -type d -print0 | sort -z)
  echo "  ──────────────────────"
  echo "  Total               $TOTAL classes"
  rm -rf "$DECODED"

  echo ""
  echo "── Security flags ──"
  # Decode without smali for fast manifest access
  DECODED2=$(mktemp -d)
  apktool d -f -s "$apk" -o "$DECODED2" 2>/dev/null
  MF="$DECODED2/AndroidManifest.xml"

  debuggable=$(grep -c 'android:debuggable="true"' "$MF" 2>/dev/null) || debuggable=0
  cleartext=$(grep -c 'usesCleartextTraffic="true"' "$MF" 2>/dev/null) || cleartext=0
  exported=$(grep -c 'android:exported="true"' "$MF" 2>/dev/null) || exported=0
  printf "  debuggable=true:          %s  %s\n" "$debuggable" "$([ "$debuggable" -gt 0 ] && echo '⚠️  (debug build — expected)' || echo '✓')"
  printf "  usesCleartextTraffic:     %s  %s\n" "$cleartext" "$([ "$cleartext" -gt 0 ] && echo '⚠️' || echo '✓')"
  printf "  exported components:      %s\n" "$exported"
  echo ""
  echo "  Declared permissions:"
  grep 'uses-permission' "$MF" | grep -oE 'android\.permission\.[A-Z_]+' | sed 's/^/    /' || echo "    (none)"
  rm -rf "$DECODED2"
}

# ── test ───────────────────────────────────────────────────────────────────────

do_test() {
  hdr "UNIT TESTS — Robolectric"
  require gradle java

  if [[ ! -f "$REPO_ROOT/local.properties" ]]; then
    echo "⚠️  No local.properties — set sdk.dir if Android SDK is available"
  fi

  # ExampleUnitTest (pure JVM, no Android) — skipped because AGP is needed to
  # compile even pure-JVM tests. With a proper SDK set in local.properties and
  # Google Maven available, run:
  echo "Command (requires Android SDK + Google Maven access):"
  echo "  gradle :app:testDebugUnitTest --tests 'com.example.*'"
  echo ""
  echo "Running pure-JVM sanity check instead (no SDK required):"

  # Compile and run ExampleUnitTest standalone with javac/java if possible
  UNIT_TEST="$REPO_ROOT/app/src/test/java/com/example/ExampleUnitTest.kt"
  if command -v kotlinc &>/dev/null; then
    OUT=$(mktemp -d)
    kotlinc "$UNIT_TEST" -include-runtime -d "$OUT/test.jar" 2>&1 | tail -5
    java -cp "$OUT/test.jar" org.junit.runner.JUnitCore com.example.ExampleUnitTest 2>&1 | tail -5 || true
    rm -rf "$OUT"
  else
    echo "  kotlinc not found — skipping pure-JVM compile"
    echo "  Tests defined in app/src/test/java/com/example/:"
    find "$REPO_ROOT/app/src/test" -name "*.kt" | sed "s|$REPO_ROOT/||" | sed 's/^/    /'
    echo ""
    echo "  Screenshot tests use Roborazzi — baseline PNGs live at:"
    find "$REPO_ROOT/app/src/test" -name "*.png" | sed "s|$REPO_ROOT/||" | sed 's/^/    /'
  fi
}

# ── dispatch ───────────────────────────────────────────────────────────────────

CMD="${1:-all}"
APK_ARG="${2:-}"

case "$CMD" in
  review)  do_review ;;
  crunch)  do_crunch "$APK_ARG" ;;
  test)    do_test ;;
  all)
    do_review
    do_crunch "$APK_ARG"
    ;;
  *)
    echo "Usage: $0 [review|crunch [APK]|test|all]"
    exit 1
    ;;
esac
