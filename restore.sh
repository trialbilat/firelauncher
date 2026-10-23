#!/usr/bin/env bash
# Put the stock Fire TV home screen back.
# Usage: ./restore.sh <tv-ip> [stock-launcher-package-you-disabled]
set -u

IP="${1:-}"
STOCK="${2:-}"
PKG="com.custom.firelauncher"

[ -z "$IP" ] && { echo "Usage: ./restore.sh <tv-ip> [disabled-stock-package]"; exit 1; }
DEV="$IP:5555"

adb connect "$DEV"

if [ -n "$STOCK" ]; then
  echo "==> Re-enabling $STOCK"
  adb -s "$DEV" shell pm enable "$STOCK"
fi

echo "==> Removing the custom launcher"
adb -s "$DEV" uninstall "$PKG"

echo "Done. Press Home on the remote (reboot the TV if the stock screen doesn't return)."
