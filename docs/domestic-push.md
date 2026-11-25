# 国内厂商推送调研与接入方案

## 结论

目标是应用进程未运行时，仍能显示额度重置的真实通知，且国内用户无需国际网络。国内厂商的系统通知通道能够在系统支持且权限允许时直接展示通知。它们依赖国内联网，并非让普通第三方应用取得永久后台进程白名单。完全断网时仍要使用本地闹钟；对用户主动执行的系统强行停止不承诺送达。

v1.7.0 的增强闹钟模式和一次性后台测试已经实现；以下厂商云推送方案尚未部署，不能将候选通道识别视为实际注册成功。

## 参考过的开放源码

| 项目 | 实际查阅的实现 | 对本项目的启示 |
|---|---|---|
| [WxPusher](https://github.com/wxpusher/wxpusher-app) | [PushPlatformResolver](https://github.com/wxpusher/wxpusher-app/blob/master/androidApp/src/androidMain/kotlin/com/smjcco/wxpusher/push/PushPlatformResolver.kt)、[PushManager](https://github.com/wxpusher/wxpusher-app/blob/master/androidApp/src/androidMain/kotlin/com/smjcco/wxpusher/push/PushManager.kt)、[PushChannelCoordinator](https://github.com/wxpusher/wxpusher-app/blob/master/androidApp/src/androidMain/kotlin/com/smjcco/wxpusher/push/PushChannelCoordinator.kt)、各厂商注册类 | 按运行设备选择适用厂商 SDK；统一处理成功、超时、失败和 token 更新；将生效通道与 token 同步后端，而不是把品牌名称直接当作连接成功。 |
| [MixPush](https://github.com/taoweiji/MixPush) | [MixPushClient](https://github.com/taoweiji/MixPush/blob/master/client/mixpush-core/src/main/java/com/mixpush/core/MixPushClient.java)、[OPPO 注册](https://github.com/taoweiji/MixPush/blob/master/client/mixpush-oppo/src/main/java/com/mixpush/oppo/OppoPushProvider.java)、[vivo 注册](https://github.com/taoweiji/MixPush/blob/master/client/mixpush-vivo/src/main/java/com/mixpush/vivo/VivoPushProvider.java)、[服务端发送模块](https://github.com/taoweiji/MixPush/tree/master/server/mixpush-sender) | 客户端 SDK 管理与服务端发送者分离；在各厂商系统通道中优先用通知栏消息。独立荣耀通道需单独补足；旧适配器不应直接替代新 SDK 验证。 |
| [Fossify Clock](https://github.com/FossifyOrg/Clock) | [本地闹钟调度](https://github.com/FossifyOrg/Clock/blob/main/app/src/main/kotlin/org/fossify/clock/extensions/Context.kt)、[AlarmReceiver](https://github.com/FossifyOrg/Clock/blob/main/app/src/main/kotlin/org/fossify/clock/receivers/AlarmReceiver.kt) | 用户可见的实际闹钟采用 `setAlarmClock`，其他提醒按需求安排普通精确闹钟，广播负责实际触发。ResetNotice 采用相同的 Android 公开 API，未复制 GPL 项目代码。 |

第三方应用提供的注册信息、签名和推送权限归其自身所有，不能直接移植到 `com.vlan.resetnotice`。本项目使用自己的应用包名、签名及授权。

## 国内通道矩阵

| 目标设备 | 原生通道 | 客户端 | 权限和限制 |
|---|---|---|---|
| OPPO、realme、一加 | [OPPO PUSH](https://open.oppomobile.com/newservice/msp) | Heytap SDK、注册 ID、设备能力查询 | 正式通道需厂商开放平台开通；腾讯云资料列出企业开发者和应用上架要求。 |
| vivo、iQOO | [vivo Push](https://developers.vivo.com/doc/d/6b683b474cf64fdab1a0738035c8868e) | vivo SDK、注册 ID | 正式推送通常要求企业开发者与应用上架；未上架的测试设备受限。 |
| 小米、Redmi、POCO | [Mi Push](https://dev.mi.com/xiaomihyperos/ability/mipush) | 中国大陆版 SDK、AppID/AppKey、RegID | 官方流程要求开发者认证、应用审核、通知分类审核。发送密钥仅限服务端。 |
| 荣耀 | [HONOR Push](https://developer.honor.com/cn/docs/11002/guides/sdk-overview) | HonorPush SDK、PushToken、SDK 能力检查 | 独立荣耀平台申请；旧荣耀机型需按实际 SDK 支持判断是否回落 HMS。 |
| 华为及部分旧荣耀 | [Huawei Push Kit](https://developer.huawei.com/consumer/cn/doc/HMSCore-Guides-V5/service-introduction-0000001050040060-V5/) | HMS Android SDK、PushToken | AppGallery Connect 开通服务，按签名和支持设备验证；原生 HarmonyOS NEXT 不是本 APK 的运行环境。 |
| 魅族 | Flyme Push | 魅族 SDK、PushID | 厂商开放平台授权；测试前核对最新 SDK 和应用条件。 |
| 其他 Android | 本地闹钟优先 | 不默认集成 GMS | 如后续明确需要海外兼容，可选择 FCM，不作为大陆使用前提。 |

资格参考：[腾讯云厂商推送常见问题](https://cloud.tencent.com/document/faq/548/36674)、[小米官方启用流程](https://dev.mi.com/xiaomihyperos/documentation/detail?pId=1542)、[vivo 正式与测试资格](https://cloud.tencent.com.cn/document/product/269/132305)、[荣耀推送术语](https://developer.honor.com/cn/kitdoc?category=%E5%9F%BA%E7%A1%80%E6%9C%8D%E5%8A%A1&docId=noun-explanation.md&kitId=11002&navigation=guides)。正式资格和消息分类要以各平台实际审核为准；并非所有聚合服务都绕开厂商账号要求。

应用包名：`com.vlan.resetnotice`。现有签名证书 SHA-256：`441c929f4f6d55bc1f759ce71b9cb170972f7bed5ad8b5070663d529700be27b`。只提供公开证书指纹，不公开私钥。

## 计划架构

1. 保留现有本地闹钟与 Direct Boot；原有离线模式无需上传任何账户数据。
2. 用户选择开启厂商云端提醒后，初始化与设备匹配的官方 SDK，实际检查能力，完成注册并保存通道与 token。失败时保留本地提醒，显示原因。
3. 可信国内后端使用认证的设备关联标识处理最小计划数据：通道、token、计划版本、周期、首次重置时间、已启用的提前/到点提醒及用户选择的通知名称。无需 AI 账号密码或 API 密钥。用户可以撤回云端同步并删除计划。
4. 后端持久化计划和任务的唯一事件键，从固定重置边界计算后续周期，在真实提醒时刻调用相应厂商的通知栏接口。版本检查、短有效期、编辑/删除撤销、有限重试避免旧通知重复发送。正式消息分类应选择厂商允许的真实用户订阅提醒。
5. 厂商系统消息中心直接展示通知；本地闹钟仍兜底。在支持自定义通知 ID 的通道中使用一致的事件身份，其他通道可能出现重复，由用户选择是否启用云端提醒。
6. 每一通道均须验证 SDK 注册、实际通知、关闭常驻服务、移除最近任务、系统进程回收、重启、修改/删除计划、断网、重连、通知权限以及生产消息分类。普通任务移除与系统强行停止分别测试。

后续搭建需要项目所有者授权相关开放平台的开发者账号和应用注册，确定可信国内托管位置及费用上限。各厂商服务端发送密钥只存后端安全配置；不会嵌入 APK 或提交源码。待权限申请完成再集成真实 SDK、服务端适配器与端到端测试。

## 本地模式验证

Android [闹钟调度指南](https://developer.android.com/develop/background-work/services/alarms)说明系统闹钟 API 会提供更强的低功耗状态送达保障，但有资源成本。v1.7 支持显式启用，并提供一次性 60 秒后台测试。如果测试尚无系统回调，报告这一事实，不以打开应用后补算冒充后台送达。系统硬停止仍可能取消待触发的 PendingIntent。
