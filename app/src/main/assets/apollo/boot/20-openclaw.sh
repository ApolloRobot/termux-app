#!/data/data/com.termux/files/usr/bin/bash
# Apollo worker — start OpenClaw / runit services after boot
export PREFIX="${PREFIX:-/data/data/com.termux/files/usr}"
export HOME="${HOME:-/data/data/com.termux/files/home}"
export PATH="$PREFIX/bin:$PATH"
export SVDIR="$PREFIX/var/service"
export LOGDIR="$PREFIX/var/log"

mkdir -p "$HOME/.apollo-logs"
LOG="$HOME/.apollo-logs/boot-openclaw.log"
echo "[$(date -Iseconds)] boot: openclaw" >>"$LOG"

sleep 8
termux-wake-lock >>"$LOG" 2>&1 || true
[ -f "$PREFIX/etc/profile.d/start-services.sh" ] && . "$PREFIX/etc/profile.d/start-services.sh"
service-daemon start >>"$LOG" 2>&1 || true

for svc in openclaw mqtt-reporter device-provisioner; do
  if [ -d "$PREFIX/var/service/$svc" ]; then
    sv up "$svc" >>"$LOG" 2>&1 || sv start "$svc" >>"$LOG" 2>&1 || true
  fi
done

pgrep -x sshd >/dev/null || sshd >>"$LOG" 2>&1 || true
echo "[$(date -Iseconds)] boot: openclaw done" >>"$LOG"
