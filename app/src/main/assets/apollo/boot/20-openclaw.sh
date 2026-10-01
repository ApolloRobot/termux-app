#!/data/data/com.termux/files/usr/bin/bash
# Apollo worker — start OpenClaw / runit services after boot
set -e
PREFIX="${PREFIX:-/data/data/com.termux/files/usr}"
export PATH="$PREFIX/bin:$PATH"
export HOME="${HOME:-/data/data/com.termux/files/home}"

mkdir -p "$HOME/.apollo-logs"
LOG="$HOME/.apollo-logs/boot-openclaw.log"
echo "[$(date -Iseconds)] boot: openclaw" >>"$LOG"

# Give network / storage a moment after BOOT_COMPLETED
sleep 5

if command -v sv >/dev/null 2>&1; then
  if [ -d "$HOME/.openclaw" ] || [ -d "$PREFIX/var/service/openclaw" ] || [ -d "$HOME/.termux/boot" ]; then
    # Common layouts: runit under ~/service or prefix var/service
    for svc in openclaw mqtt-reporter; do
      if [ -d "$HOME/service/$svc" ] || [ -d "$PREFIX/var/service/$svc" ]; then
        sv up "$svc" >>"$LOG" 2>&1 || true
      fi
    done
  fi
fi

if command -v openclaw >/dev/null 2>&1; then
  # If gateway is managed outside sv, best-effort nudge
  openclaw gateway status >>"$LOG" 2>&1 || true
fi

echo "[$(date -Iseconds)] boot: openclaw done" >>"$LOG"
