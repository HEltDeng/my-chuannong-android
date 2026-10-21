# 2026-10-21 账号颜色、地图弹窗与 VPN 过渡

按用户四张截图完成三个调整。保留此前的首次川农 e → 教务认证、双学校会话与独立平台账号、刷新缓存、密码光标和主标签流畅度功能。

## 账号输入框与地图

账号输入框原本使用 `AppTheme.colors.bgCardColor`。截图中跨出圆角的黄色覆盖与 Android 自动填充高亮一致，因此为 `AccountRequiredActivity`、`AccountCenterActivity` 加入专用 Activity theme，仅在 API 26+ 将 `android:autofilledHighlight` 设为透明。普通背景继续跟随已有浅色主题，保留边框、圆角、文字、图标、输入类型、密码密文、校验、登录/注册及自动填充行为。没有改动共用的 `ImeCompatibleTextField` 光标处理代码。

校园地图 `AlertDialog` 显式使用纯白 `containerColor`、0 tonal elevation 及深色标题/正文；原有条款、隐私链接、遮罩、取消、返回键关闭和同意后初始化 SDK 的流程保留。

## VPN 绑定展示边界

`SicauWebVpnActivity` 默认将 WebView 设为 INVISIBLE、禁用触摸并从无障碍树隐藏。每次导航开始、历史地址变化及开始验证时同步遮住原生 View，不等待 Compose 下一帧。APP 显示“正在验证校园 VPN，请稍候……”；失败显示简短提示及“重新连接”，保留 APP 返回按钮用于取消。

只对受限的官方认证地址，在视觉内容准备完毕后显示认证界面。用户名/密码、验证码、短信和其他学校验证仍由用户在学校页面完成。VPN 根路径若直接包含认证控件，可显示该表单；MutationObserver 在表单被同页资源列表替换时立即将网页置为透明、不可操作、对无障碍隐藏，再由原生状态接管。检测只读取认证控件是否存在及尺寸，不读取任何输入值、Cookie 或密码，也不提交认证表单。

根页面 guard 用 opacity 而非 visibility，保持学校现有资源链接解析所需的 `innerText`。设备用例同时验证隐藏时资源文字仍可被解析，避免遮挡导致连接发现失败。

原有官方 Cookie 恢复、学校资源映射捕获、教务登录页连通性验证、会话持久化和 `RESULT_OK` 后进入下一步保持原流程。成功仍要求真实校内资源校验，不能把 URL、显示状态或本地标记当作连接成功。增加导航/重试版本检查、旧请求错误过滤和 35 秒无交互加载超时；用户正在官方表单输入时停止加载超时。离开或重试会取消旧验证任务。

## 验证

- `WebVpnPagePresentationTest`：认证路径、门户/业务页面不显示、伪域名/用户信息/非标准端口拒绝。
- `LoginMapVpnDeviceTest`：使用隔离 `.uitransition` 包、合成学校身份与拦截的本地 HTML。验证登录/注册自动填充后仍为白底且密码顺序不变；地图弹窗暂不使用、系统返回关闭及同意；门户与 SPA 历史页面隐藏、不能点击；根页面原地替换资源不显示；失败、重新连接和旧错误不盖住新登录。
- `PasswordCursorDeviceTest`：连续输入、删除、粘贴、选区、显示/隐藏和校验截断回归。只扩展其允许的隔离测试包后缀，未增加生产认证绕过入口。
- 最终设备回归 9 项通过。初次地图测试在弹窗尚未显示时按了系统返回，已在测试中等待弹窗可见及 idle 后复测通过。首次隔离构建运行 JVM 全量测试时，唯一失败是原有 AppIdentityTest 固定要求普通 `.debug` 包名；最终普通构建重新执行全量测试，结果见交付清单。

图像检查覆盖登录、注册、地图授权、VPN 验证及失败页。原始合成截图、测试日志、签名、最终全量测试结果及 APK 哈希保存在工作区 `05_Output/login-map-vpn-20261021/delivery.json` 及同目录文件，不提交用户原图、真实密码、学校 Cookie 或数据库。

本次没有使用真实学校账号登录，学校服务器的实际认证及验证码成功链路仍需本人验收；已保持原有协议，并用合成页面验证新增的展示、拦截与重试行为。

## 构建与安装

```powershell
# 配置本项目 JDK/SDK 后，隔离设备测试：
.\gradlew.bat :app:assembleDebug :app:assembleDebugAndroidTest '-PcacheTestSuffix=.uitransition' '-PdeviceTestBuildType=debug' --max-workers=1 --no-daemon --no-configuration-cache
adb install -r app/build/outputs/apk/debug/MyChuannong_debug_v0.1.0-preview.9.apk
adb install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb shell am instrument -w -r -e class com.mychuannong.app.school.LoginMapVpnDeviceTest,com.mychuannong.app.core.account.PasswordCursorDeviceTest com.mychuannong.app.uitransition.test/androidx.test.runner.AndroidJUnitRunner

# 普通交付包及检查，去掉两个隔离测试属性：
.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug :app:lintDebug :app:lintRelease --max-workers=1 --no-daemon --no-configuration-cache
```

安装前核对 `output-metadata.json` 的 applicationId 为 `com.mychuannong.app.debug`，验证原 Debug 签名后使用 `adb install -r`，不清除原应用数据。仅卸载本轮创建的两个 `.uitransition` 测试包。

WebView 展示时机参考 [Android WebViewClient 官方 API](https://developer.android.com/reference/android/webkit/WebViewClient#onPageCommitVisible(android.webkit.WebView,%20java.lang.String))。
