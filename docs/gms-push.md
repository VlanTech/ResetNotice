# GMS 云端提醒接入设计

当前 v1.6.0 仅查询 Google Play 服务和商店是否安装且启用。没有 Firebase 项目、远端消息发送者或网络权限，因此实际提醒依旧由 Android 系统本地闹钟提供。

## 目标

接入 Firebase Cloud Messaging（FCM）为用户已启用的提前／到点提醒增加云端送达路径。特定的高优先级、用户可见通知可以由 Google Play 服务代理显示，不需要启动应用界面；不使用虚假消息、保活心跳或试图越过用户的系统强行停止。

## 需要配置

- 项目所有者授权使用 Firebase 项目，并注册 Android 应用 `com.vlan.resetnotice`。
- 提供该 Android 应用的 Firebase 客户端配置文件 `google-services.json`，确定项目所有权和消息发送权限。
- 由项目所有者选择并配置可信调度后端，例如 Cloud Functions 与调度服务。FCM HTTP v1 的服务账户凭据由后端安全保管，不嵌入 APK、不提交至代码仓库。
- 用户主动开启云同步后才能将所需的设备推送标识及提醒时间发送至后端；账户密码、AI 平台令牌不参与此功能。

## 实现方案

1. 客户端获得 FCM 注册标识并处理标识更新，向认证后的调度后端同步当前已启用的计划。
2. 服务端保存最少必要信息（推送标识、计划版本、通知事件、到期时间与周期），按用户更改或删除计划撤销旧任务。
3. 仅在真实提醒事件发生时发送相应的高优先级可见通知，使用有限的有效期，防止离线很久后收到过期事件。
4. 客户端保留本地闹钟为离线备用路径；推送与本地事件采用相同逻辑事件标识、过期检查和通知替换策略，尽可能防止重复。用户关闭云端功能时撤销服务端计划。
5. 在实际 GMS 可用设备进行重启、休眠、普通任务移除、断网、计划编辑和通知权限关闭测试。Android 或厂商的系统强行停止不承诺送达。

## 官方资料

- [FCM Android 客户端设置](https://firebase.google.com/docs/cloud-messaging/android/client)
- [FCM Android 消息优先级及通知代理](https://firebase.google.com/docs/cloud-messaging/android/message-priority)
- [FCM 服务器端发送](https://firebase.google.com/docs/cloud-messaging/send-message)
- [Android Direct Boot](https://developer.android.com/privacy-and-security/direct-boot)
- [Android 15 行为变化：应用停止状态](https://developer.android.com/about/versions/15/behavior-changes-all#package-stopped-state)
