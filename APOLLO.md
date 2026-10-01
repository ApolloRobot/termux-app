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
| 剪贴板 Termux:API | **仍要同签名安装 `com.termux.api`**（脚本打的是 api 包名） |
| OpenClaw 预置 / OTA | 后续；脚本里 best-effort `sv up` |

## 为什么 ADB 是硬门槛

业务脚本在 Termux 内通过 **ADB** 操控其它 App；没有 USB 调试 = 无法操盘。  
向导检测 `Settings.Global.ADB_ENABLED`；重启后若被关，再次进入会强制回到 ADB 步骤。

## 为什么剪贴板还要单独 API APK

`termux-clipboard-set` 广播目标是 `com.termux.api/.TermuxApiReceiver`。  
主包装进 Clipboard 代码但包名仍是 `com.termux` 时，现有脚本对不上。下一步可选：

1. 同工程再打一个 `com.termux.api` 模块（推荐，签名统一）；或  
2. 改 PREFIX 里 clipboard 脚本指向主包 Receiver。

## 编译

需要 JDK 17+（本机有 openjdk@17）与 Android SDK：

```bash
export JAVA_HOME="$(brew --prefix openjdk@17)/libexec/openjdk.jdk/Contents/Home"
export ANDROID_HOME="$HOME/Library/Android/sdk"
./gradlew :app:assembleDebug
```

APK：`app/build/outputs/apk/debug/`

装到 Pad 前请卸掉其它来源的 Termux（签名不一致会失败）。试验机建议：本 debug 包 + 同 key 的 Termux:API。

## 下一步

1. 能编过 debug APK 并在白牌 Pad 上走通向导  
2. 工程内增加 `termux-api` 精简模块（只留 Clipboard）  
3. 对接现有 `provision-whitepad.sh` → App 内 bootstrap / 激活 SN  
4. 再考虑改 `applicationId`（需重编 bootstrap）
