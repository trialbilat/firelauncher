#!/usr/bin/env bash
# Fire TV telemetry / bloat helper (reversible: uses `pm disable-user`, never uninstalls).
#
#   ./debloat.sh <tv-ip> list      show Amazon/system packages installed on THIS TV
#   ./debloat.sh <tv-ip> dry       show what would be disabled (default, changes nothing)
#   ./debloat.sh <tv-ip> apply     disable the packages in packages.txt that exist on this TV
#   ./debloat.sh <tv-ip> restore   re-enable everything this script disabled
#   ./debloat.sh <tv-ip> status    show which packages are currently disabled
#
# Package names vary between Fire OS versions, so packages.txt is only a starting point.
# Anything not installed on your TV is skipped. Edit packages.txt to taste (one package per line).
set -u

IP="${1:-}"
MODE="${2:-dry}"
HERE="$(cd "$(dirname "$0")" && pwd)"
LIST="$HERE/packages.txt"
LOG="$HERE/disabled-by-script.txt"

if [ -z "$IP" ]; then
  sed -n '2,12p' "$0" | sed 's/^# \{0,1\}//'
  exit 1
fi
command -v adb >/dev/null 2>&1 || { echo "adb not found. Install Android platform-tools first."; exit 1; }

DEV="$IP:5555"
adb connect "$DEV" >/dev/null 2>&1
adb -s "$DEV" get-state >/dev/null 2>&1 || { echo "Can't reach $DEV. Check the IP and that ADB debugging is on."; exit 1; }

sh() { adb -s "$DEV" shell "$@" | tr -d '\r'; }

installed="$(sh pm list packages | sed 's/^package://')"

wanted() { grep -vE '^\s*(#|$)' "$LIST" 2>/dev/null | sed 's/\s*#.*$//; s/\s*$//'; }

case "$MODE" in
  list)
    echo "Amazon packages installed on this TV (the ones you could consider disabling):"
    echo "$installed" | grep -E '^com\.amazon\.' | sort
    ;;

  status)
    echo "Currently disabled packages:"
    sh pm list packages -d | sed 's/^package://' | sort
    ;;

  dry|apply)
    [ -f "$LIST" ] || { echo "packages.txt not found next to this script."; exit 1; }
    found=0
    while read -r pkg; do
      [ -z "$pkg" ] && continue
      if ! echo "$installed" | grep -qx "$pkg"; then
        echo "  skip (not installed): $pkg"
        continue
      fi
      found=$((found + 1))
      if [ "$MODE" = "dry" ]; then
        echo "  would disable: $pkg"
      else
        out="$(sh pm disable-user --user 0 "$pkg" 2>&1)"
        if echo "$out" | grep -qi "disabled"; then
          echo "  disabled: $pkg"
          grep -qx "$pkg" "$LOG" 2>/dev/null || echo "$pkg" >> "$LOG"
        else
          echo "  FAILED: $pkg -> $out"
        fi
      fi
    done < <(wanted)
    echo
    if [ "$MODE" = "dry" ]; then
      echo "$found package(s) would be disabled. Nothing was changed. Run './debloat.sh $IP apply' to do it."
    else
      echo "Done. Log saved to $LOG. Undo with './debloat.sh $IP restore'. Reboot the TV once."
    fi
    ;;

  restore)
    [ -f "$LOG" ] || { echo "No log found ($LOG), so nothing to restore from this script."; exit 0; }
    while read -r pkg; do
      [ -z "$pkg" ] && continue
      echo "  re-enabling: $pkg"
      sh pm enable "$pkg" >/dev/null 2>&1
    done < "$LOG"
    mv "$LOG" "$LOG.restored"
    echo "Done. Reboot the TV once."
    ;;

  *)
    echo "Unknown mode: $MODE (use list, dry, apply, restore or status)"
    exit 1
    ;;
esac
