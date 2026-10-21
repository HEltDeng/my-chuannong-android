# 非校园网教务绑定与学校安全验证排查（2026-10-19）

用户确认：官网直接输入同一学号和同一个教务密码能够登录；App 选择“非校园网环境”时，显示“学校教务登录结果未确认，请重试”，手机收到要求通过验证码重新登录并及时修改密码的短信。

## 已确认的问题与证据边界

1. 原登录响应分类将所有无法识别的 `alert(...)` 统一归入 `LOGIN_RESPONSE_UNKNOWN`，没有区分学校短信验证、密码安全处理和其他提示。首次绑定页也没有继续完成学校验证的入口，用户只能重新提交密码。
2. WebVPN 原生请求收到 `Set-Cookie` 后，调用无回调的 `CookieManager.setCookie`，随后继续从 Chromium 读取 Cookie、提交登录表单。该 API 是异步写入，原代码没有等待完成；新页面动态字段可能与下一次请求携带的旧会话不一致。已用延迟 Cookie 写入的合成 HTTP 流程验证该顺序要求，并在真实 Android CookieManager 上验证回调后的可见性。
3. 只读取得的学校公开登录页仍是普通 HTML 表单，声明 `gb2312`，字段为 `user/pwd/lb/submit/sign/hour_key`。其 `checkform()` 只做输入检查和短密码提醒，没有在提交前加密或替换密码。原生实现却固定使用 UTF-8 表单编码；非 ASCII 内容可能与浏览器提交不同。对于纯 ASCII 密码，这项编码修正通常不会改变提交字节，不能单凭它解释本次短信。

这些是已确认的实现缺陷，不等于已确定学校发送本次短信的服务端触发规则。用户手机未接入本机调试，没有取得该次登录的学校原始返回；没有用用户学号、密码或短信验证码进行代登录，也没有主动触发真实短信。不能据此断言密码错误、密码被修改、弱密码或账号被盗。

参考：[Android CookieManager 异步回调契约](https://developer.android.com/reference/android/webkit/CookieManager#setCookie(java.lang.String,java.lang.String,android.webkit.ValueCallback))、[HTML 表单提交编码选择](https://html.spec.whatwg.org/multipage/form-control-infrastructure.html#selecting-a-form-submission-encoding)。学校公开页面检查不保存真实动态字段、匿名会话 Cookie 或扫码令牌。

## 修正后的处理

- WebVPN 原生响应逐个等待 Cookie 写入回调，再解析字段或发下一次请求。继续保留来源、路径、Secure、HttpOnly、SameSite 和学校有效期，并在写入前后核对账号会话版本；退出后的旧请求不能继续完成绑定。HTTP 自动重试和凭据 POST 自动跳转仍关闭。
- 表单使用学校实际声明的编码；HTTP charset、HTML meta 和表单 `accept-charset` 共同决定解码/提交方式，GB2312 按浏览器兼容方式映射为 GBK。
- 新增短信验证与密码安全处理两类提示。未知 `alert` 继续拒绝直接标记登录成功；不把出现跳转或 HTTP 200 当成本人身份认证。
- 这些学校验证提示出现后，绑定页提供“打开学校页面继续验证”。它打开同一网络路线上的官方登录页 GET，由用户在学校原页面完成登录、短信和密码处理；不自动填入或重发原生密码，不猜测验证码字段，不自动点击验证按钮。
- 官方页面的“完成验证，继续绑定”只请求返回核验，Activity 返回码不授予访问权限。原生客户端随后仅 GET 本人资料页，要求学校返回学号与本次输入完全一致，且没有未完成的安全验证或过期表单，再沿既有加密绑定流程保存身份。错账号、未登录、页面解析失败和会话版本变化均不放行。
- 验证请求仅留在 ViewModel 内存中，不含密码。更改网络或学号、取消和进程重建后，不能用旧返回码恢复绑定。验证页面在用户已同意条款后允许打开，不能用它绕过首次身份核验。
- 常规原生绑定及已绑定用户的本地缓存、离线启动规则保留。没有新增后台保活、学校密码存储或向产品后台上传学校数据的路径。

涉及主要文件：`SicauJiaowuProtocol.kt`、`SicauJiaowuSession.kt`、`SicauJiaowuCookieJar.kt`、`SicauWebVpn.kt`、`NativeBindingViewModel.kt`、`BindingUserActivity.kt`、新增 `SchoolLoginVerificationActivity.kt`，以及绑定布局、首次使用页面许可和清单声明。

## 验证结果

- 普通 Debug 的 316 项 JVM 测试通过，包含 8 项新增登录安全回归：表单编码、响应字符集、安全提示分类、待验证身份拒收、官方来源限制、延迟 Cookie 写入、短信要求后不重发密码/不抢读资料，以及网页返回后的 GET 身份匹配。
- 新增 3 项 Android 隔离测试最终全部通过：真实 Chromium Cookie 写入回调及属性、无待处理登录时伪造完成返回不能放行、首次绑定前打开验证页面并返回仍不能授予绑定。页面烟测只打开公开入口 GET，随后替换为本地合成页面；截图仅含合成内容，已视觉核对标题、说明和底部按钮。生产页面仍保持 FLAG_SECURE。
- 先前完整运行 `AcademicLoginVerificationDeviceTest` 与 `BindingEntryDeviceTest` 共 14 项，13 通过、1 个原有键盘用例在等待 IME 显示时超时。单独开启模拟器软键盘配置后复测仍超时，未将该项标记为通过，也未修改生产代码或旧测试规避。模拟器的 `show_ime_with_hard_keyboard` 已恢复原值 0。
- 普通 Debug 构建与 Lint 通过，Lint 0 error，既有警告仍保留。最终日志、APK、校验和与本轮源码哈希位于本机 `05_Output/academic-login-investigation-20261019/`。

测试 HTTP、Cookie、学号及页面均为合成资料；这些结果不代表用户真实账号已在手机上完成绑定，也不证明学校之后不会再发送安全短信。

## 手机复测与交付

交付文件：`MyChuannong_academic_login_fix_20261019.apk`，普通内部 Debug，包名 `com.mychuannong.app.debug`，版本 `0.1.0-preview.9 / 9`；沿用本机既有 Debug 签名。精确 SHA-256 与字节数见同目录交付清单。

覆盖安装后选择原来的“非校园网环境”，完成校园 VPN 连接，再提交一次同一学号和教务密码。如果学校仍要求验证，点“打开学校页面继续验证”，按学校页面要求完成，再点“完成验证，继续绑定”。若学校网页内也阻止同一密码登录，应保留页面的提示类型继续判断服务端策略；不循环提交或绕过学校验证。

本轮保留原工作区修改，只对本次文件留存修改前快照；没有提交、推送、发布 Release、公开分发 APK 或修改产品后台。回退应依据本次快照逐文件处理，不能重置整个工作区。

## 2026-10-20 真机复测补充

- 本人确认今天尚未尝试登录。调试中先覆盖安装只增加固定错误类别日志的诊断包，随后用户自行重新安装了昨晚的原包。最终受测包 SHA-256 为 `56fbadf19503c145dce1cee456d485adaf6a8b9d9388f6c435e9311cef4fd451`，不能把其成功归因于本次诊断改动。
- 设备安装记录确认本次为重新安装。用户在手机自行输入并完成一次教务登录；现场日志观察到 `POST_LOGIN status=302`、`REDIRECT_SUCCESS`、`studentIdParsed=true`、`identityMatch=true`，随后进入 MainActivity。用户确认登录成功且没有收到安全短信。未取得或记录密码、验证码、账号标识或 Cookie。
- 成功绑定后 force-stop 冷启动直接进入 MainActivity；观察启动阶段未出现新的教务登录 GET/POST。未执行解绑、清数据或自动重复提交密码。
- 今天未复现学校拦截。昨天官方网页明确显示错误次数超限锁定，证明当时有次数限制；不能据此认定 App 被单独封禁，也不能根据今天一次成功确定昨天最初失败的原因或保证后续永不受安全验证。
- 为后续异常准备了 Debug 固定枚举诊断 `SicauLoginResponseSignals`，只输出页面中出现的锁定、次数限制、短信、校验错误等类别，不输出原始返回、凭据或 URL，不影响登录成功判定。类别仅是页面内容线索，不证明含有相应字面量的脚本分支确实执行。相关 23 项 JVM 回归通过、Debug 构建成功。此诊断包并非本次最终成功受测包。
- 本机证据位于 `05_Output/academic-risk-check-20261020/`。日志环形缓冲区较小，保存的日志只有后段身份核验事件；较早 POST/分类事件的观察记录单独注明来源，未补造原始日志。
