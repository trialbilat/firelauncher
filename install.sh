#!/usr/bin/env bash
# Install the launcher on a Fire TV over ADB and try to make it the default home app.
# Usage: ./install.sh <tv-ip> [path-to-apk]
set -u

IP="${1:-}"
APK="${2:-app/build/outputs/apk/debug/app-debug.apk}"
PKG="com.custom.firelauncher"
ACTIVITY="$PKG/.MainActivity"

if [ -z "$IP" ]; then
  echo "Usage: ./install.sh <tv-ip> [path-to-apk]"
  echo "Find the IP on the TV: Settings > My Fire TV > About > Network"
  exit 1
fi
command -v adb >/dev/null 2>&1 || { echo "adb not found. Install Android platform-tools first."; exit 1; }
[ -f "$APK" ] || { echo "APK not found at: $APK (build it in Android Studio first)"; exit 1; }

DEV="$IP:5555"

echo "==> Connecting to $DEV (accept the prompt on the TV if one appears)"
adb connect "$DEV"
adb -s "$DEV" get-state >/dev/null 2>&1 || { echo "Could not reach the TV. Check the IP, Wi-Fi, and that ADB debugging is on."; exit 1; }

echo "==> Installing $APK"
adb -s "$DEV" install -r "$APK" || { echo "Install failed."; exit 1; }

echo "==> Setting as default home app"
OUT="$(adb -s "$DEV" shell cmd package set-home-activity --user 0 "$ACTIVITY" 2>&1)"
echo "$OUT"
if echo "$OUT" | grep -qi "success"; then
  echo "Done. Press Home on the remote."
else
  echo
  echo "Fire OS refused to set the default (common). Options:"
  echo "  1. Press Home on the remote; if a chooser appears, pick 'Home' and choose Always."
  echo "  2. See README.md, 'If it won't become the default', for the disable-stock-launcher route."
fi

echo "==> Launcher-related packages on this device (useful for step 2 above):"
adb -s "$DEV" shell pm list packages 2>/dev/null | grep -i launcher
