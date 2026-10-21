# 校园服务会话恢复与统一认证

日期：2026-10-18。Android 内部预览；不涉及学校服务器配置或正式发布。

## 行为

- 官网、教务网站、学工、一网通办、校园缴费及 VPN 的现有 WebView 入口共用 `SchoolWebSessions`。进入页面前恢复会话，页面完成及离开页面时保存；保存任务不随 Activity 结束而取消。已有网页的较新 Cookie 优先，不用旧快照覆盖。
- APP 自行保存的浏览器会话快照使用 Android Keystore 的 AES-GCM 密钥和 AtomicFile，位于禁止备份的私有目录；密码不参与恢复、不落盘。网页运行中的 Cookie、站点存储由系统 WebView 管理，APP 不读取页面密码或复制 JavaScript Token 到普通偏好设置。既有备份/迁移排除规则继续生效。
- 保存完整 Set-Cookie 属性，保留 Domain、Path、Secure、HttpOnly、SameSite；Max-Age 转成当次收到时对应的绝对期限。恢复不延长有效期，不新增跨域作用域。无法保留分区键的 Partitioned Cookie 不导出恢复。记录的定位 URL 去掉查询串与 fragment；不把 CAS/OAuth 历史写入 Activity Bundle。
- 统一认证优先走学校页面已有的 `cascallback=choose` 流程，各业务站点自行处理回调。跨页面共享有界重试记录，避免 SPA 回调反复开启同一认证。业务页面明确返回 401 时至多自动打开一次已知官方入口；不重放 POST、支付表单，不把 403 或断网当成全局退出。
- 前台服务页每 30 秒观察一次；仅当学校 Cookie 给出了可见的到期时间且剩余不足两分钟时，才用官方现有 GET 会话检查请求尝试复用。并发检查串行合并，至少间隔一分钟。只接纳服务器真正返回的 Cookie 更新；未续期或网络失败会显示提示，不修改本地期限、不后台无限保活。
- VPN 登录页和统一认证登录页分别提示。VPN 不可用不清除教务绑定。教务原生读取确认自身会话过期时，可再尝试一次同源网页中已有的新教务会话，仍须由学校返回匹配本人身份，成功后才保存。
- 主动清除学校绑定，以及账号中心“退出登录/切换账号”，撤销本机校园会话。退出建立版本屏障、取消原生请求、停止旧网页及弹窗、清空加密会话和 Cookie，并等待清理完成。迟到的响应、保存任务及旧绑定任务不能恢复旧授权。
- AndroidX WebKit 升至 1.13.0，支持时使用 `deleteBrowsingDataForSite` 清除学校站点的 JavaScript 存储和其他浏览数据，保留 APP 离线站点的草稿等记录。旧 WebView 使用原有清理兼容路径；建议更新系统 WebView，以获得完整站点数据清理能力。
- 保留现有首页/服务页面设计，以及先前已确认的“绑定后直接进入首页、冷启动不全屏联网复核”行为。认证提示只在服务容器内出现。

## 能力边界

学校的公开前端已核实存在会话复用 GET 与 `cascallback=choose`，没有确认独立 refresh-token 接口。因此此改动不宣称实现学校未提供的刷新协议。没有 Expires/Max-Age 的会话，无法预知服务器内部的过期时刻，由实际服务响应和登录页引导恢复。

原生教务账号仍使用独立教务协议；未发现官方 SSO 接入依据的系统不强行共享认证。电精灵的外部微信授权也不复制进学校 SSO。退出保证清除本 APP 保存的状态，不宣称能撤销其他浏览器、设备或学校服务端的所有会话。

不承诺永久登录。学校强制有效期、验证码、二次验证和权限限制仍按学校要求处理。真实学校账号的多系统单点登录及服务端到期策略仍需用户本人在设备上验收；本轮不索取或使用真实学校凭据。

## 验证入口

JVM 回归覆盖完整 Cookie 属性、绝对有效期、域/path 隔离、分区 Cookie 拒收、旧快照拒收、退出版本屏障、有限认证重试、VPN/SSO 分类，以及付款 POST 不重放。设备回归仅使用 `.sessiontest` 隔离包和合成资料：

1. `UnifiedWebSessionDeviceTest`：新的浏览器会话优先、加密保存、统一退出。
2. `SchoolWebSessionDeviceTest#logoutClearsWebsiteTokensAndPreservesOfflineAppStorage`：学校 localStorage 清除，APP 离线站点记录保留。
3. `SchoolWebSessionDeviceTest#seedEncryptedSessions`：写入 SSO、学工、缴费、VPN、教务的合成会话并清空浏览器。
4. force-stop 隔离 APP，再运行 `SchoolWebSessionDeviceTest#restoreAfterColdStartAndLogout`：跨进程恢复、过滤过期 Cookie、保留作用域和 SameSite、退出后不恢复旧状态。

构建和设备测试日志、APK 放在工作区 `05_Output/session-management-20261018/`，不提交学校会话、日志或 APK 到源码仓库。最终结果以该目录 `verification-summary.json` 为准。

## 核对来源

- [学校统一认证公开前端](https://auth.sicau.edu.cn/wui/assets/index-DwxyaMpm.js)：2026-10-18 只读核对 `GET /api/v1/cas/login?appid=…` 及 `cascallback=choose`；未调用密码认证接口。
- [学校统一支付入口](https://op.sicau.edu.cn/loginAll.aspx)：提供统一身份认证登录。
- [Android CookieManagerCompat](https://developer.android.com/reference/androidx/webkit/CookieManagerCompat)：获取原始 Cookie 属性，使用前检测 GET_COOKIE_INFO。
- [Android WebStorageCompat](https://developer.android.com/reference/androidx/webkit/WebStorageCompat)：站点级清理 Cookie、缓存和 JavaScript 可读存储，使用前检测 DELETE_BROWSING_DATA。
