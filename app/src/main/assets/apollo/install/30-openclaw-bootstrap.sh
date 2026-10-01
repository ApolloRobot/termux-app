#!/data/data/com.termux/files/usr/bin/bash
# Apollo Worker — install OpenClaw + skill + services (runs inside Termux)
# Args via env:
#   APOLLO_SN          device SN (required)
#   APOLLO_ASSET_DIR   dir with openclaw.json + skill tar (required)
set -euo pipefail

PREFIX="${PREFIX:-/data/data/com.termux/files/usr}"
HOME="${HOME:-/data/data/com.termux/files/home}"
export PATH="$PREFIX/bin:$PATH"
export HOME
export GYP_DEFINES="android_ndk_path=''"
export NPM_CONFIG_REGISTRY="${NPM_CONFIG_REGISTRY:-https://registry.npmmirror.com}"
NPM_MIRROR="${NPM_CONFIG_REGISTRY}"
NPM_FALLBACK="${NPM_FALLBACK:-https://registry.npmjs.org}"
OPENCLAW_PKG="${OPENCLAW_PKG:-openclaw@2026.7.1-2}"
FEISHU_PKG="${FEISHU_PKG:-@openclaw/feishu@2026.7.1}"
SKILL_VERSION="${SKILL_VERSION:-1.2.174}"

LOG_DIR="$HOME/.apollo-logs"
mkdir -p "$LOG_DIR"
LOG="$LOG_DIR/openclaw-install.log"
STATUS="$LOG_DIR/openclaw-install.status"
exec > >(tee -a "$LOG") 2>&1

mark() {
  echo "$1" >"$STATUS"
  echo "[$(date -Iseconds)] $1 — ${2:-}"
}

fail() {
  mark "FAILED" "$1"
  echo "❌ $1"
  exit 1
}

mark "RUNNING" "start"

SN="${APOLLO_SN:-}"
ASSET_DIR="${APOLLO_ASSET_DIR:-$HOME/.apollo-assets}"
[[ -n "$SN" ]] || fail "APOLLO_SN empty"
[[ -d "$ASSET_DIR" ]] || fail "asset dir missing: $ASSET_DIR"
[[ -f "$ASSET_DIR/openclaw.json" ]] || fail "openclaw.json missing"
SKILL_TGZ="$(ls "$ASSET_DIR"/android-worker-node-*.tar.gz 2>/dev/null | head -1 || true)"
[[ -n "$SKILL_TGZ" && -f "$SKILL_TGZ" ]] || fail "skill tarball missing in $ASSET_DIR"

mark "RUNNING" "pkg/base"
mkdir -p ~/.gyp ~/.termux/boot ~/.openclaw/agents/main/sessions ~/openclaw/workspace "$PREFIX/tmp"
cat > ~/.gyp/include.gypi << 'EOF'
{
  "variables": {
    "android_ndk_path": ""
  }
}
EOF
cat > ~/.bashrc << 'EOF'
export PATH=$PREFIX/bin:$PATH
export GYP_DEFINES="android_ndk_path=''"
export NPM_CONFIG_REGISTRY=https://registry.npmmirror.com
[ -f "$PREFIX/etc/profile.d/start-services.sh" ] && . "$PREFIX/etc/profile.d/start-services.sh"
EOF

pkg update -y >/dev/null 2>&1 || true
pkg install -y android-tools nodejs-lts openssh git tmux termux-services \
  clang make python pkg-config imagemagick ghostscript 2>&1 | tail -20 || true

if ! command -v node >/dev/null || ! command -v npm >/dev/null; then
  pkg install -y nodejs-lts || fail "nodejs-lts install failed"
fi
command -v node >/dev/null && command -v npm >/dev/null || fail "node/npm missing"
echo "node $(node -v)  npm $(npm -v)"

npm config set registry "$NPM_MIRROR"
npm config set fetch-retries 5
npm config set fetch-timeout 600000
npm config set fetch-retry-mintimeout 20000
npm config set fetch-retry-maxtimeout 120000
echo "npm registry=$(npm config get registry)"

# clipboard + boot scripts may already be installed by App; ensure sshd boot bit
if [[ ! -x "$HOME/.termux/boot/10-sshd.sh" ]]; then
  cat > "$HOME/.termux/boot/10-sshd.sh" << 'EOF'
#!/data/data/com.termux/files/usr/bin/bash
export PREFIX=/data/data/com.termux/files/usr
export HOME=/data/data/com.termux/files/home
export PATH=$PREFIX/bin:$PATH
mkdir -p "$HOME/.apollo-logs"
sshd >>"$HOME/.apollo-logs/boot-sshd.log" 2>&1 || true
EOF
  chmod +x "$HOME/.termux/boot/10-sshd.sh"
fi

mark "RUNNING" "openclaw npm"
if openclaw --version 2>/dev/null | grep -qi openclaw; then
  echo "openclaw already: $(openclaw --version 2>/dev/null | head -1)"
else
  npm install "$OPENCLAW_PKG" --force -g \
    || npm install "$OPENCLAW_PKG" --force -g --registry "$NPM_FALLBACK" \
    || fail "npm install $OPENCLAW_PKG failed"
  node "$PREFIX/lib/node_modules/openclaw/scripts/postinstall-bundled-plugins.mjs" 2>/dev/null || true
fi
openclaw --version || fail "openclaw not runnable"

mark "RUNNING" "config+sn"
mkdir -p ~/.openclaw/agents/main/sessions
cp -f "$ASSET_DIR/openclaw.json" ~/.openclaw/openclaw.json
chmod 700 ~/.openclaw
cat > ~/.openclaw/mno_device.json << EOF
{"sn":"$SN","assignedAt":"$(date -u +%Y-%m-%dT%H:%M:%S.000Z)","source":"apollo-app"}
EOF
openclaw config validate 2>&1 | tail -5 || true

mark "RUNNING" "feishu plugin"
NPM_CONFIG_REGISTRY="$NPM_MIRROR" openclaw plugins install "$FEISHU_PKG" --force 2>&1 | tail -20 || true
openclaw plugins list 2>&1 | grep -i feishu || true

mark "RUNNING" "skill $SKILL_VERSION"
SKILL="$HOME/openclaw/workspace/skills/android-worker-node"
mkdir -p "$SKILL"
cd "$SKILL"
tar xzf "$SKILL_TGZ"
echo "$SKILL_VERSION" > .version
mkdir -p tracks
rm -f tracks/*.yaml tracks/*.yml 2>/dev/null || true
npm install --omit=dev 2>&1 | tail -15 \
  || npm install --omit=dev --registry "$NPM_FALLBACK" 2>&1 | tail -15 \
  || fail "skill npm install failed"
cp -f scripts/device-provisioner.js scripts/device-provisioner.cjs 2>/dev/null || true

mark "RUNNING" "termux-services"
# shellcheck disable=SC1091
source "$PREFIX/etc/profile.d/start-services.sh" 2>/dev/null || true
export SVDIR="$PREFIX/var/service" LOGDIR="$PREFIX/var/log"
mkdir -p "$PREFIX/var/service/openclaw" "$PREFIX/var/service/mqtt-reporter" "$PREFIX/var/service/device-provisioner"

cat > "$PREFIX/var/service/openclaw/run" << 'EOF'
#!/data/data/com.termux/files/usr/bin/sh
export PREFIX=/data/data/com.termux/files/usr
export HOME=/data/data/com.termux/files/home
export PATH=$PREFIX/bin:$PATH
exec node $PREFIX/lib/node_modules/openclaw/openclaw.mjs gateway 2>&1
EOF
cat > "$PREFIX/var/service/mqtt-reporter/run" << 'EOF'
#!/data/data/com.termux/files/usr/bin/sh
cd $HOME/openclaw/workspace/skills/android-worker-node
exec node scripts/mqtt-reporter.js 2>&1
EOF
cat > "$PREFIX/var/service/device-provisioner/run" << 'EOF'
#!/data/data/com.termux/files/usr/bin/sh
cd $HOME/openclaw/workspace/skills/android-worker-node
exec node scripts/device-provisioner.cjs 2>&1
EOF
chmod +x "$PREFIX/var/service/openclaw/run" \
         "$PREFIX/var/service/mqtt-reporter/run" \
         "$PREFIX/var/service/device-provisioner/run"

sv-enable openclaw || true
sv-enable mqtt-reporter || true
sv-enable device-provisioner || true
sv-enable sshd 2>/dev/null || true
service-daemon start 2>/dev/null || true
sleep 2
sv restart openclaw || sv start openclaw || true
sv restart mqtt-reporter || sv start mqtt-reporter || true
sv restart device-provisioner || sv start device-provisioner || true
pgrep -x sshd >/dev/null || sshd || true
termux-wake-lock 2>/dev/null || true
sleep 3
sv status openclaw || true
sv status mqtt-reporter || true
sv status device-provisioner || true

# refresh boot openclaw script
cat > "$HOME/.termux/boot/20-openclaw.sh" << 'EOF'
#!/data/data/com.termux/files/usr/bin/bash
export PREFIX=/data/data/com.termux/files/usr
export HOME=/data/data/com.termux/files/home
export PATH=$PREFIX/bin:$PATH
export SVDIR=$PREFIX/var/service
export LOGDIR=$PREFIX/var/log
mkdir -p "$HOME/.apollo-logs"
LOG="$HOME/.apollo-logs/boot-openclaw.log"
echo "[$(date -Iseconds)] boot openclaw" >>"$LOG"
sleep 8
termux-wake-lock >>"$LOG" 2>&1 || true
[ -f "$PREFIX/etc/profile.d/start-services.sh" ] && . "$PREFIX/etc/profile.d/start-services.sh"
service-daemon start >>"$LOG" 2>&1 || true
for svc in openclaw mqtt-reporter device-provisioner; do
  sv up "$svc" >>"$LOG" 2>&1 || sv start "$svc" >>"$LOG" 2>&1 || true
done
pgrep -x sshd >/dev/null || sshd >>"$LOG" 2>&1 || true
echo "[$(date -Iseconds)] boot openclaw done" >>"$LOG"
EOF
chmod +x "$HOME/.termux/boot/20-openclaw.sh"

mark "OK" "sn=$SN skill=$SKILL_VERSION"
echo "✅ OpenClaw install complete for SN=$SN"
