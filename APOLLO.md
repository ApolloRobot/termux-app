# Apollo Worker — 产品策略备忘

## 客户可见文案

向导里**不出现** OpenClaw / Termux:API / npm 包名。统一说「工作环境 / 后台服务 / 初始化」。

## 核心运行时版本（钉死）

- Gateway：`openclaw@2026.7.1-2`（**禁止 @latest**）
- 飞书插件：`@openclaw/feishu@2026.7.1`
- Skill：随 APK 打包的 `android-worker-node`（可 OTA，与 gateway 解耦）

原因：新版 OpenClaw 在 Termux 上多次因 koffi/cmake、配置 schema 变更导致 **gateway 起不来**。出货只装钉死版；已装则**不自动升级**。

## 大模型 vs Gateway 版本

| 需求 | 改什么 |
|------|--------|
| 换/上新模型 | `openclaw.json` 的 `models.providers` + 中转站 / Token，**不必**升 OpenClaw |
| 新通道协议、新 CLI | 才可能要升 OpenClaw（需在白牌机验证后再改 PIN） |

钉死 `2026.7.1-2` **一般不影响**接最新大模型：模型走 HTTP API，由配置与中转站决定。

## 升级原则

1. **Skill OTA**：可以常更（业务脚本）
2. **OpenClaw gateway**：默认不升；要升必须改 App 内 `OPENCLAW_PIN_VERSION` 并在试验机验证通过
3. 安装脚本若发现已装非 pin 版本：**保持现状，不强升**
