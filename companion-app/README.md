# 池鱼手环同步（伴生 Android App）

「池鱼Study」与手环端「池鱼单词」之间的桥梁：通过蓝牙（小米穿戴 SDK）连接手环，**推送词表**、**收回学习记录**。

完整链路：**池鱼Study 生成词表 → 本 App 推送 → 手环背单词（遗忘曲线智能选词）→ 本 App 收回记录 → 池鱼Study 导入合并 SRS**

## 功能

| 功能 | 说明 |
|---|---|
| 连接手环 | 自动检测小米手环（需小米运动健康后台运行），握手建立互连 |
| 推送词表 | 读取池鱼Study 导出的 JSON 词表，按 `word_push` 协议分块（20词/块）推送，显示进度 + 手环确认 |
| 收回记录 | 发 `record_pull` 取回手环学习记录，保存到 `Download/Chiyu/池鱼手环记录_*.json`，引导导入池鱼Study |

## 使用流程（本地直连：推荐，免传文件）

1. 手环端已安装「池鱼单词」应用（.rpk）
2. 打开「池鱼Study」→ 我的 → 手环背单词 → **🔗启动本地同步**（出现"服务运行中"）
3. **一键推送**：点「🚀一键推送」→ 自动拉起本 App → 自动从池鱼Study 拉词表 → 自动推到手环（全程无需选文件）
4. **收回记录**：在本 App 点"收回学习记录" → 手环记录**自动回传**池鱼Study（显示合并统计）

> 兜底方案：本地直连不可用时，本 App 仍支持手动选文件推送、收回记录保存到 `Download/Chiyu/` 后由池鱼Study 导入。

## 使用流程（文件兜底）

1. 手环端已安装「池鱼单词」应用（.rpk）
2. 打开本 App → 点击"手环连接中"卡片 → 连接成功（需先确认小米运动健康连接着手环）
3. **推送**：打开「池鱼Study」→ 我的 → 手环背单词 → 📤生成词表 → 保存 JSON → 在本 App 选择该文件 → 推送到手环
4. **收回**：在本 App 点"收回学习记录" → 手环记录保存到 Download/Chiyu → 打开「池鱼Study」→ 📥导入记录 → 选择该文件 → 自动合并（时间戳去重、更新 SRS、连续错词提醒）

## 目录结构

```
companion-app/
├── app/
│   ├── libs/xms-wearable-lib_1.4_release.aar   # 小米穿戴 SDK
│   └── src/main/java/com/chiyu/sync/
│       ├── MainActivity.kt          # 主界面：连接 + 选词表 + 收回记录 + chiyu-sync:// 一键拉起
│       ├── WordPushActivity.kt      # 词表分块推送（word_push 协议，支持本地直连拉词表）
│       ├── RecordPullActivity.kt    # 收回记录（record_pull/record_back，自动回传池鱼Study）
│       ├── HttpBridge.kt            # 本地直连桥（127.0.0.1:8765 拉词表/回传记录）
│       └── logic/
│           ├── Interconn.kt         # 小米穿戴 API 封装
│           └── InterHandshake.kt    # 握手协议（__hs__）
├── build.gradle.kts
└── settings.gradle.kts
```

## 构建

**GitHub Actions**（推荐）：推送到仓库后自动构建，在 Actions 页面下载 APK。
**本地**：用 Android Studio 打开 `companion-app/` 目录，等待 Gradle 同步后 Run。

> 说明：release 签名通过根目录 `keystore.properties` + `release.keystore` 配置（可选）；没有配置时用默认 debug 签名。

## 与手环端协议

详见 [`docs/同步协议.md`](../docs/同步协议.md)：
- 握手 `__hs__`（count 0→1→2）
- 推送 `word_push`（分块：batch/total + words）
- 确认 `push_ack`（count/total）
- 收回 `record_pull` / `record_back`（records: word/action/t）

## 版权

本项目衍生自 [喵喵电子书同步器](https://github.com/BandBBS-Vela-Dev/com.bandbbs.ebook-android)（AGPL-3.0），沿用 AGPL-3.0 协议开源（见 LICENSE）。
