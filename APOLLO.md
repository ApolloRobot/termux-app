# Apollo Worker（基于 Termux 白标试验版）

仓库：`git@github.com:ApolloRobot/termux-app.git`  
分支：`apollo/worker-v1`  
本地：`/Users/yemeng/mayun/termux-app`

## 试验版范围（v1）

| 能力 | 状态 |
|------|------|
| 展示名「掌玩 Worker」 | 已改（包名仍为 `com.termux`，免重编 bootstrap） |
| 首次向导 | `SetupActivity`，**ADB 硬门槛** |
| 开机跑 `~/.termux/boot` | **已并入主 App**（不再依赖 Termux:Boot APK） |
| 默认 boot 脚本 | `assets/apollo/boot/10-sshd.sh`、`20-openclaw.sh` |
| 剪贴板 | **已并入主 App**，无需 Termux:API APK |
| OpenClaw 预置 / OTA | 后续；脚本里 best-effort `sv up` |

## ADB 是硬门槛

业务脚本在 Termux 内通过 **ADB** 操控其它 App；没有 USB 调试 = 无法操盘。  
向导检测 `Settings.Global.ADB_ENABLED`；重启后若被关，再次进入会强制回到 ADB 步骤。

## 剪贴板怎么做到「一起」

Android 一个 APK 只能有一个 `applicationId`，官方 CLI 默认找 `com.termux.api`。我们的做法：

1. 主包实现 `ClipboardApiReceiver` + `ResultReturner`，并监听 `com.termux.api://listen`（同 UID 可接官方 libexec）
2. bootstrap / 向导后安装 Apollo 版 `$PREFIX/bin/termux-clipboard-set|get`，`am broadcast` 打到主包 Receiver
3. **不必再装 Termux:API APK**；若已装官方 API，脚本仍可优先走官方路径

## 编译

```bash
export JAVA_HOME="$(brew --prefix openjdk@17)/libexec/openjdk.jdk/Contents/Home"
export ANDROID_HOME="$HOME/Library/Android/sdk"
./gradlew :app:assembleDebug
```

APK：`app/build/outputs/apk/debug/termux-app_apt-android-7-debug_arm64-v8a.apk`

装到 Pad 前请卸掉其它来源的 Termux（签名不一致会失败）。**不再需要** Termux:API / Termux:Boot。

## 下一步

1. 白牌 Pad 上验证 `termux-clipboard-set` + 开机 boot  
2. 对接 `provision-whitepad.sh` → App 内激活 SN  
3. 再考虑改 `applicationId`（需重编 bootstrap）
