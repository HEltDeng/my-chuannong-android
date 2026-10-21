# 首次认证与学校服务跳转统一

本次更新优先于旧文档中的校园网/非校园网选择规则。仅修改 Android，未发布、上传 APK 或修改后台。

## 交互

- 首次流程固定为川农 e 认证、验证校内资源可访问、教务账号密码登录、初始化数据。删除网络选择、切换入口、绑定页顶部隐私小字及 Keystore 技术说明。保留原输入框、按钮、未默认勾选的自动登录授权、学校验证和删除凭据功能。
- 继续复用双认证实现：统一认证与教务会话分别维护；优先学校会话，教务密码仅明确授权后 Keystore 加密存储，学校验证码/短信/二次认证仍在官方页面完成。冷启动只读缓存；主动刷新失败保留数据与成功更新时间。
- `SchoolServices` 统一配置服务名称、官方目标地址、认证类型。原有 8 个指定服务使用 `SchoolServiceNavigation` 和 `SchoolWebActivity`。新增网页服务可增加配置；未单列的服务自动使用学校服务区的默认图标。教务旧入口兼容到已登录的本人资料页。
- 自动认证与官方回调在同一容器完成，目标页准备好后展示。统一认证与一网通办保留学校 CAS 协议；不会改写学校 service/ticket 参数。学工回到大厅后自动继续原模块，最多一次；失败显示错误，不停留大厅让用户找入口。
- 延迟加载的密码、验证码和二次认证控件会显示出来，供本人在学校页面完成验证；不会被自动跳转遮住。设置中的校园网络状态页也移除了跳过川农 e 的入口。
- 原生返回、系统返回、更多菜单关闭、网页返回/关闭、`history.back/go(-n)` 和 `window.close` 均关闭指定服务。复用已有 MainActivity 并选择服务页，不重建页面或重置滚动位置。官网、校历、校外园地、电费等未指定功能保留其原行为。

## 安全与兼容

没有新增凭据采集、服务器上传、日志、明文密码或伪造 Cookie。网页返回适配仅提供关闭动作，不暴露账号或缓存读取接口。只在官方 HTTPS 学校页面执行，且脚本仅在顶层框架生效。学校会话的保存、恢复、撤销、冷却和版本屏障沿用双认证实现。

使用 AndroidX WebKit 支持检测安装文档起始脚本，旧 WebView 在页面加载完毕时补装；原生和系统返回始终可用。API 依据：[Android WebViewCompat 官方文档](https://developer.android.com/reference/androidx/webkit/WebViewCompat#addDocumentStartJavaScript(android.webkit.WebView,java.lang.String,java.util.Set%3Cjava.lang.String%3E))。

## 验证

- 356 项 JVM 测试通过，包括新增 10 项服务目标、认证回调、回首页、循环跳转及 URL 参数测试。
- 默认 Debug APK 构建成功；Debug / Release Lint 均为 0 错误，分别有 1506 / 1505 条警告及 3 条提示，没有关闭检查来放行。
- 模拟器共 18 项独立用例通过：固定首次流程 5 项、服务返回与延迟验证 6 项、双认证/缓存 6 项、网络状态页 1 项。凭据跨进程持久化另以 seed / verify 两次启动验证通过。完整日志中默认跳过的两个跨进程阶段由这两次单独运行补齐。
- 已对照用户图片及视频检查，绑定页的登录顺序说明、隐私小字、Keystore 说明和网络选择均已移除；原输入框、按钮和默认未勾选的授权保留。截图仅为隔离测试生成的空表单。
- 设备用例使用独立 `.serviceflowtest` 包、合成身份与拦截后的本地 HTML。八个标题共用的返回容器、同一 MainActivity 和服务滚动位置已验证；真实目标地址及回调逻辑由 JVM 测试覆盖。这些结果不代表已登录真实八个学校服务。学校撤销会话、验证码规则及各服务实际页面仍需本人账号验收。

本机产物与验证日志位于工作区 `05_Output/service-flow-20261021/`；APK 为 `MyChuannong_service_flow_20261021.apk`，默认包名 `com.mychuannong.app.debug`，版本 `0.1.0-preview.9`。已使用覆盖安装保留模拟器原包数据，没有卸载或清除原包数据。

复测命令（JDK 17 / SDK 36.1）：

```text
gradlew :app:testDebugUnitTest :app:assembleDebug :app:lintDebug :app:lintRelease --no-configuration-cache
gradlew :app:assembleDebug :app:assembleDebugAndroidTest -PcacheTestSuffix=.serviceflowtest -PdeviceTestBuildType=debug --no-configuration-cache
adb shell am instrument -w -e class com.mychuannong.app.settings.ui.BindingNetworkDeviceTest,com.mychuannong.app.school.ServiceFlowDeviceTest,com.mychuannong.app.school.DualAuthDeviceTest,com.mychuannong.app.school.CampusNetworkScreenDeviceTest com.mychuannong.app.serviceflowtest.test/androidx.test.runner.AndroidJUnitRunner
```

隔离构建后须重新构建默认 Debug 才可覆盖用户安装；禁止卸载或清数据解决签名问题。
