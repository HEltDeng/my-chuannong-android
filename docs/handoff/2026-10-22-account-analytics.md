# 平台账号门禁与日活接入

已接入自有后台 `activity/foreground`：版本3隐私同意、已登录 PHONE 账号、应用前台三个条件同时满足才上报。请求在会话互斥锁内再次核对账号，切换账号不串发。通常每5分钟最多一次，跨北京时间日期/切换账号可重新上报，网络失败没有历史补传队列。

主界面复用现有 AccountGateScreen，恢复会话时显示加载页，无平台账号则先登录/注册；不改变原学校认证流程、导航壳与绿色主题。隐私政策与信息收集清单补充厂商、型号、Android及APP版本、前台活跃用途与180天明细保留期，不读取SIM卡号码或新增电话权限。手机号仍来自用户注册输入，后台尚未接入短信核验。

上报使用 ProcessLifecycleOwner 的前台状态，不在关闭APP后启动统计任务。此功能不改变已实现的按板块关注通知，也不等于厂商离线推送。

原签名、高德配置与发布门禁保持有效。本轮只完成源码编译和JVM测试，未生成或发布 APK；高德企业认证完成后再用原JKS构建正式包。后端现已具备新接口，实际前台上报覆盖以新版安装后的收到数据为准。

参考：[Android 生命周期](https://developer.android.com/reference/androidx/lifecycle/ProcessLifecycleOwner)、[Android 标识选择](https://developer.android.com/identity/user-data-ids)。只发送本次任务披露的自有服务字段，不新增第三方统计SDK。
