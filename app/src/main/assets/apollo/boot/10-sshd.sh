#!/data/data/com.termux/files/usr/bin/bash
# Apollo worker — start sshd after boot (Termux path)
set -e
PREFIX="${PREFIX:-/data/data/com.termux/files/usr}"
export PATH="$PREFIX/bin:$PATH"
export HOME="${HOME:-/data/data/com.termux/files/home}"

mkdir -p "$HOME/.apollo-logs"
LOG="$HOME/.apollo-logs/boot-sshd.log"
echo "[$(date -Iseconds)] boot: sshd" >>"$LOG"

if ! command -v sshd >/dev/null 2>&1; then
  echo "sshd not installed" >>"$LOG"
  exit 0
fi

# Termux sshd typically listens on 8022
if pgrep -f 'sshd' >/dev/null 2>&1; then
  echo "sshd already running" >>"$LOG"
else
  sshd >>"$LOG" 2>&1 || true
fi
