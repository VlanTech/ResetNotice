<div align="center">
  <img src="app/src/main/res/drawable-nodpi/portrait.png" alt="ResetNotice" width="112" />
  <h1>重置通知 · ResetNotice</h1>
  <p>多平台 AI 额度重置提醒 · 离线 · 自动续排</p>
  <p><a href="releases/ResetNotice-v1.5.0.apk">下载 v1.5.0 APK</a> · <a href="https://github.com/VlanTech/ResetNotice/releases">Releases</a> · <a href="CHANGELOG.md">版本记录</a> · <a href="LICENSE">MIT</a></p>
</div>

## 简介

ResetNotice 是一款轻量级原生 Android 应用，将多个 AI 平台、多个账号的额度重置时间集中管理。只需录入首次重置时间，之后按设定周期自动续排，并在提前提醒或重置到点时发送本地通知。

应用不连接 AI 平台 API，不需要登录账户，也不需要提供密码或 API Key。重置时间以用户录入的额度周期为准，全部数据仅保存在手机本机。

## 功能

- **多平台 / 多账户**：支持独立新增、编辑、删除账户，短时额度可选，周额度独立管理。
- **账户排序**：手动拖动卡片排序，拖动自动收起展开的设置；也可以按账户下一次最早重置实时排序，两种模式的顺序独立保留。
- **实时倒计时**：使用手机系统时钟，重置时间精确对齐至整分钟；折叠后仍显示倒计时和实际日期时间。
- **自动循环**：短时限额默认每 5 小时循环，支持自定义周期；周限额每 7 天循环。关机或短暂离线后直接补算到下一轮。
- **通知管理**：提前提醒、到点提醒独立开关，提前时间可自定义；关闭通知不影响计时器续排。
- **后台计时**：有效计时器自动启动可见的前台服务与常驻状态通知，划掉最近任务后保持运行，同时保留精确闹钟；清除全部计时器自动停止服务。
- **原生体验**：日夜间主题、平滑折叠动画、账户内局部更新，无需联网。
- **界面与语言**：18 种主要 AI 厂商标识自动匹配，简洁的齿轮与展开图标；独立设置页支持跟随系统、日间、夜间主题及 11 种语言（简体中文、繁體中文、English、日本語、한국어、Español、Português、Français、Italiano、Deutsch、Русский）。
- **交互与通知**：小时额度名称随循环周期自动显示；原生预测式返回、可关闭触感反馈，常驻通知以最近重置倒计时为标题，重置提醒以厂商为标题。

## 安装与使用

1. 下载 [ResetNotice-v1.5.0.apk](releases/ResetNotice-v1.5.0.apk) 或从 [Releases](https://github.com/VlanTech/ResetNotice/releases) 获取最新签名 APK，允许系统安装应用。
2. 打开应用，授权通知与精确提醒（Android 系统中的「闹钟和提醒」）。
3. 点击首页「＋ 新增」，填写 AI 名称和账户备注；不需要短时额度时可将其关闭。
4. 展开账户，选择具体重置日期时间，或输入剩余天数、小时、分钟。短时周期可独立修改。
5. 按需开启提前提醒和到点提醒。到期后自动顺延，无需反复录入。

覆盖安装本仓库发布的 APK 可保留现有配置。首次打开升级后的应用，检查首页后台运行状态及通知栏常驻状态。ColorOS 等系统可能限制后台服务及精确闹钟，可点击首页右上角齿轮，在设置页查看电池优化状态、进入后台运行设置，允许后台活动和自启动，并在最近任务中锁定应用（如系统提供该选项）。前台服务在系统回收时请求恢复；最近任务移除时重新确认闹钟，系统硬停止仍可能移除前台服务和通知，需重新打开应用补算下一次重置时间。

## 技术信息

| 项目 | 规格 |
|---|---|
| 当前版本 | v1.5.0 |
| applicationId | `com.vlan.resetnotice` |
| 最低系统 | Android 6.0（API 23） |
| 目标 SDK / 编译 SDK | API 34 / API 35 |
| 实现 | 原生 Java、Android SDK |
| 通知 | AlarmManager、NotificationManager、Foreground Service |
| 数据 | SharedPreferences，本地持久化 |

## 源码与构建

Linux 环境需要 Java 运行时（已使用 OpenJDK 21 验证）、`curl`、`unzip`、`zip`、`keytool`。不依赖 Gradle 或第三方 Android 运行时库。

```bash
bash fetch-toolchain.sh
bash build-apk.sh
```

构建脚本会校验工具链下载文件、编译资源和 Java、执行计时及账户数据测试，然后自动签名并检查 APK。未指定正式签名时输出 `deliverables/ResetNotice-v1.5.0-local.apk`，仅供本地测试，签名与正式发行版不同，不能覆盖安装正式发行版。

正式发布时须使用项目原有的发布签名密钥，并在本机安全设置下列环境变量：

| 变量 | 用途 |
|---|---|
| `RESETNOTICE_KEYSTORE` | 正式签名密钥文件路径 |
| `RESETNOTICE_KEY_ALIAS` | 密钥别名，默认 `resetnotice` |
| `RESETNOTICE_KS_PASS` | 密钥库密码 |
| `RESETNOTICE_KEY_PASS` | 密钥密码，省略时同上 |

设置后运行 `bash build-apk.sh`，输出正式版文件名。密钥和密码均不得提交到仓库；定版 APK 通过 Releases 分发。

### 目录结构

- `app/src/main/` — Android 清单、Java 源码、界面资源和应用图标。
- `tests/` — 账户隔离、持久化、旧版迁移与周期续排测试。
- `build-apk.sh` — 构建、测试、签名与 APK 验证。
- `fetch-toolchain.sh` — 下载并校验固定版本的 Android 编译工具链。
- `CHANGELOG.md` — 版本记录。
- `releases/` — 已验证的正式签名安装包。

## 项目说明

本项目由 Vlan_Tech 维护，用于个人 AI 使用额度管理。应用内时间为本地设置的计划时间，平台实际可用额度仍以各平台自身状态为准。

项目源码与文档采用 [MIT License](LICENSE) 开源。角色头像及应用图标等图像素材不在该许可证的授权范围内，素材权利归其原权利人所有。厂商标识来自 Lobe Icons，详见 [第三方声明](THIRD_PARTY_NOTICES.md)。
