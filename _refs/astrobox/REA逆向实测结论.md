# AstroBox 逆向实测结论（2026-10-23）

> 目的：验证"能不能用 GitHub 的 REA 逆向工具挖出 AstroBox 的手环连接协议，让伴生 App 像它一样独立登录小米账号、不依赖手机 Mi Fitness"。

## 一、工具实测结果

| 工具 | 状态 | 说明 |
|---|---|---|
| REA（github.com/morluto/rea，15万+ star） | ✅ 已装 rea@6.7.0 | 全局安装成功 |
| Rizin 0.9.1 | ✅ 已装 | `C:\android-tools\rizin\...\rizin.exe`，已加入 PATH |
| REA 的 4 个分析引擎 | ❌ 全缺 | Ghidra（免费但需自行下载，550MB+）、IDA（付费注册）、Hopper（仅 macOS）、Kuna（付费） |
| jadx（DEX 反编译） | ❌ 自检失败 | Windows Temp 目录 DACL 权限拒绝访问（Win32 5） |
| `rea analyze AstroBox.apk` | ❌ 失败 | capability_unavailable / unsupported_provider：四个引擎全拒，且都不直接支持 APK（archive）目标，要先解出二进制 |

结论：**REA 本身装好了，但要真正反编译，必须再配 Ghidra 12.1.x（免费）或付费引擎；且 APK 需先解包成 .so 才能喂给引擎。**

## 二、绕开 REA 手动分析的成果（Rizin + Python）

直接对 APK 解包后的 `classes.dex`（2.59MB，明文）和 `lib/arm64-v8a/libapp_lib.so`（169.94MB，Rust 静态链接、字符串压缩）做了底层扫描，确认了 AstroBox 的连接架构：

### 1. 技术栈
- **Tauri (Rust) 壳**：包名 `moe.astralsight.astrobox`，前端打包在 Rust 库内部（tauri.conf.json 的 frontendDist → `../../../dist`，brownfield 模式）
- **Kotlin 自研插件层**：`com.astralsight.astrobox.plugin.*`

### 2. "独立登录小米账号"机制（关键实测确认）
- 插件：`moe/astralsight/astrobox/plugin/inappbrowser/`（WebView 内嵌浏览器）
- 流程：WebView 打开 `https://account.xiaomi.com/fe/service/identity`（小米网页登录）→ 抓取 cookie（字符串 `xiaomiCookieHeader`）→ 用 cookie 走小米云端 API 鉴权
- **完全不经过手机上的 Mi Fitness，这就是它能"单独登录、不和你手机账号冲突"的根本原因**

### 3. 手环连接协议
- **双通道**：自研 **BLE**（系统 `BluetoothGatt`，日志字符串 `Kotlin: BLE connecting to device`、`connectGatt returned null`、`BLE disconnected: status=`、`tryChannel(ch=`）+ **经典蓝牙 SPP**（插件 `com.astralsight.astrobox.plugin.btclassic_spp`，参数结构 ConnectArg / AddressChannelArg / AddressSendPayload，即"地址 + 通道 + 载荷"）
- dex 明文里唯一的 UUID 是标准 CCCD `00002902-0000-1000-8000-00805f9b34fb`——**手环 GATT 服务 UUID 和文件推送包格式在 Rust 库/前端 JS 里，被压缩，必须 Ghidra 级反编译才能挖出**

### 4. 其他
- Android minSdk 24；官方文件关联支持 .rpk（快应用包）、.face/.mwz（表盘）、.abp（插件）
- 前端 JS、协议 UUID、推送格式 → 全部在 169MB Rust 库内，需 Ghidra 反编译

## 三、对"伴生 App 独立登录"的结论

1. 技术上路径明确：照抄「WebView 登录拿 cookie + Android 原生 BLE/SPP 直连手环」即可绕开 Mi Fitness
2. 但协议细节（GATT UUID、推送包格式、鉴权）锁在 169MB Rust 库里，需要：
   - 下载安装 Ghidra 12.1.x（免费，约 550MB，当前网络环境需数小时）
   - 对 libapp_lib.so 做函数级反编译（数天级投入，成功率中等）
3. 现实路线二选一：
   - **短期（推荐）**：继续用官方 Wearable SDK 伴生 App（稳定），接受"推送机 = 已配对 Mi Fitness 的手机"
   - **长期（探索）**：安装 Ghidra 逐步挖协议，挖通后换自研直连，任何手机都能推送

## 四、留档文件位置
- APK：`C:\Users\黄\Downloads\AstroBox_2.2.0(1).apk`
- 解包目录：`band-word-app\_refs\astrobox\`（classes.dex、AndroidManifest.xml、lib/、web/tauri.conf.json）
- 169MB so 已解出：`band-word-app\_refs\astrobox\lib\arm64-v8a\libapp_lib.so`
- REA 诊断输出：`%TEMP%\rea_run1.txt`
- Rizin：`C:\android-tools\rizin\rizin-win-installer-clang_cl-64\bin\rizin.exe`
