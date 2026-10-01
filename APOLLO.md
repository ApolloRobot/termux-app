# Apollo Worker（基于 Termux 白标试验版）

分支：`apollo/worker-v1`

## 已集成

| 能力 | 状态 |
|------|------|
| 展示名「掌玩 Worker」 | ✅（包名仍 `com.termux`） |
| ADB 强制向导 | ✅ |
| Boot（`~/.termux/boot`） | ✅ 内置，无需 Termux:Boot |
| 剪贴板 | ✅ 内置，无需 Termux:API |
| **OpenClaw** | ✅ 向导填 SN → 联网安装 openclaw@2026.7.1-2 + 飞书插件 + skill 1.2.174 + sv 服务 |

## OpenClaw 内置方式

- APK 内带：`openclaw.json` 模板、`android-worker-node-1.2.174.tar.gz`、安装脚本
- 首次向导输入 SN 后，后台跑 `30-openclaw-bootstrap.sh`（pkg + npm，需联网 npmmirror）
- 开机脚本拉起 `openclaw` / `mqtt-reporter` / `device-provisioner` / `sshd`

> OpenClaw npm 包体积大，不直接打进 APK；由 App 内脚本首次安装，对用户仍是「装一个 App 就齐」。

## 编译

```bash
export JAVA_HOME="$(brew --prefix openjdk@17)/libexec/openjdk.jdk/Contents/Home"
export ANDROID_HOME="$HOME/Library/Android/sdk"
./gradlew :app:assembleDebug
```

APK：`app/build/outputs/apk/debug/termux-app_apt-android-7-debug_arm64-v8a.apk`
