#!/usr/bin/env bash
# Print size metrics for the release APK.
# Usage: bash scripts/measure-apk.sh [path/to.apk]
set -euo pipefail

cd "$(dirname "$0")/.."

APK="${1:-}"
if [ -z "$APK" ]; then
  APK=$(ls -t app/build/outputs/apk/release/*.apk 2>/dev/null | head -1 || true)
fi
if [ -z "$APK" ] || [ ! -f "$APK" ]; then
  echo "No APK found. Run: ./gradlew :app:assembleRelease" >&2
  exit 1
fi

BYTES=$(stat -c '%s' "$APK")
MB=$(awk "BEGIN { printf \"%.2f\", $BYTES/1048576 }")
echo "apk:      $APK"
echo "bytes:    $BYTES"
echo "size_mb:  $MB"

APKANALYZER=""
if command -v apkanalyzer >/dev/null 2>&1; then
  APKANALYZER=apkanalyzer
elif [ -n "${ANDROID_HOME:-}" ] && [ -x "$ANDROID_HOME/cmdline-tools/latest/bin/apkanalyzer" ]; then
  APKANALYZER="$ANDROID_HOME/cmdline-tools/latest/bin/apkanalyzer"
elif [ -x "$HOME/Android/Sdk/cmdline-tools/latest/bin/apkanalyzer" ]; then
  APKANALYZER="$HOME/Android/Sdk/cmdline-tools/latest/bin/apkanalyzer"
fi

if [ -n "$APKANALYZER" ]; then
  echo "--- apkanalyzer file-size ---"
  "$APKANALYZER" apk file-size "$APK"
  echo "--- apkanalyzer download-size ---"
  "$APKANALYZER" apk download-size "$APK"
else
  echo "apkanalyzer: not found (stat-only mode)"
fi

echo "--- native libraries (lib/) ---"
unzip -l "$APK" 'lib/*' | awk 'NR>3 && $4 ~ /^lib\// { printf "%10d  %s\n", $1, $4 }' | sort -nr
echo "--- largest entries (dex/res/other) ---"
unzip -l "$APK" | awk 'NR>3 && NF>=4 && $4 !~ /^lib\// { printf "%10d  %s\n", $1, $4 }' | sort -nr | awk 'NR<=20'
echo "--- compressed totals by top-level dir ---"
unzip -l "$APK" | awk 'NR>3 && NF>=4 { n=split($4,p,"/"); k=(n>1?p[1]:"(root)"); s[k]+=$1 } END { for (i in s) printf "%10d  %s\n", s[i], i }' | sort -nr
