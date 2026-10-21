# 绑定页与首次使用流程 · 2026-10-18

本次修改延续当前工作区的校园地图、校外服务、二手与失物招领等功能，未重置此前改动。修改前源文件保存在本机 `05_Output/binding-onboarding-20261018/before/`，未使用真实学校账号或密码。

## 用户流程

1. Launcher 改为 `SchoolEntryActivity`，首次仅显示介绍、隐私政策和协议。拒绝时可查看条款、重新选择或退出。
2. 同意后进入 `BindingUserActivity`；沿用 VPN 优先、校园网直连备用。学号和教务密码均必填，每次显式绑定都由教务登录接口验证密码及返回的本人学号。
3. 成功绑定后，入口重新复核教务本人资料，只在匹配身份时打开首页。已有账号无需重复引导；冷启动和回到前台使用加密会话进行复核，无须保存密码。
4. 明确登录失效后要求重新登录。网络暂不可达时停留在验证重试页，提供重新登录入口，不销毁缓存。
5. 绑定页返回先关闭键盘；未获许可时再次返回退出应用。不会露出首页、底栏或此前功能页。

## 实现边界

- `SchoolAccessController` 的访问许可仅存在于进程内，必须来自匹配学号的学校身份响应；不读取本地绑定布尔值授权。代次检查拒绝解绑/切号后迟到的验证结果。
- `SchoolAccessGate` 在 Activity 创建、恢复与当前会话撤销时统一拦截，覆盖恢复的任务、内部 Intent、通知和小组件入口。仅介绍、条款、绑定及必要的 VPN 认证页面在绑定前可用。MainActivity 不再导出。
- `SchoolSyncEngine.verifyAppAccess` 与绑定、同步、清理共用互斥锁。教务过期、身份不一致、解绑及当前教务通道要求 VPN 重新认证均撤销许可。
- 保留现有 Android Keystore + AES-GCM 的会话密文文件和禁备份策略；教务密码不持久保存，不进入平台后台或日志。密码字段禁止视图状态保存、自动填充与明文切换，认证屏幕启用安全窗口。
- `Theme.MyChuannong.Binding` 显式定义浅色背景、绿色边框和交互色。原有非认证文案与网络操作保留。
- 表单采用独立滚动区域和固定操作区，通过 `WindowInsetsCompat` 同时处理 IME、系统栏及刘海安全区；焦点滚动包含标签和字段错误，收键盘后恢复原滚动位置。VPN WebView 保留 `imePadding` 并明确 `adjustResize`。
- 键盘处理依据：[Android IME 文档](https://developer.android.com/develop/ui/views/layout/sw-keyboard)、[edge-to-edge 文档](https://developer.android.com/develop/ui/views/layout/edge-to-edge)。

## 验证与交付

具体构建、测试数量、设备尺寸和 APK 校验值以本次本地交付目录中的验证结果为准。设备测试仅在 `.bindingtest` 隔离包中运行，学校 HTTP 响应由测试拦截器提供合成数据；生产源码没有测试跳过入口。

真实学校账号登录、学校 VPN 当前可用性和不同厂商键盘仍需本人设备验收。当前仓库没有 iOS 工程，本次不包含 iOS 实现或验证。历史测试中允许未绑定浏览或失效后直接看缓存页面的断言不再代表现行产品要求。
