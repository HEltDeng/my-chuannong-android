# 我的川农 Android 内部预览

包名 `com.mychuannong.app`，版本 `0.1.0-preview.1 / 1`，学校 `sicau`，产品 `my-chuannong`。非学校官方应用。

沿用已验收 V1.0.5 的 Kotlin / Compose 工具链和 FunctionSection、FunctionItem、FunctionPalette；保留首页/服务/我的导航结构、四列服务卡片、个人中心与设置分层。学校账号和生产服务绑定较深的页面改为独立服务路由。没有迁入学校抓取库、原账号库、后台任务、小组件课表数据或旧更新器。学校服务入口来自本地 school.json；不依赖后台启动。

## 构建

JDK 17，Android SDK 36.1，Gradle wrapper 已纳入。项目目录包含中文，启用 Android 路径检查兼容选项。创建本地 `local.properties` 指向已有 SDK。

```powershell
$env:MY_CHUANNONG_SIGNING_CONFIG='D:\Codex-Local-Secrets\MyChuannong\signing.properties'
.\gradlew.bat :app:assembleRelease --no-daemon
```

只读取本产品独立签名配置。签名配置示例见 signing.example.properties，真实值不可进入仓库。输出 app/build/outputs/apk/release/app-release.apk；交付副本放在项目 05_Output/preview。不自动上传。

## 本地联调

后端仅监听 127.0.0.1:8791。真机或模拟器连接后，指定序列号执行 `adb -s DEVICE_SERIAL reverse tcp:8791 tcp:8791`，再在 App 设置开启本地后端并刷新配置。不要把电脑 localhost 当作手机可直接连接的地址；未做 reverse 时保持关闭即可。

未连接后台时仍可浏览全部内置服务、打开学校网页/外部浏览器、使用公众号和外部应用引导、阅读隐私与关于页面。失物招领和资料列表需本地后台；当前为空，不代表学校官方服务。

## WebView 边界

只允许 HTTPS 和审核主机名集合，无 JavaScript Bridge、DOM 读取、密码表单自动填充、Cookie 导出或敏感 URL 日志。证书错误取消。第三方和未确认域名必须经用户确认交外部浏览器，未知协议显示说明。下载交浏览器处理，不复制学校 Cookie。会话清理仅本包沙箱。

远程配置仅允许既有服务的名称、显示开关、排序；URL、打开方式与核心入口受客户端和服务端校验。拒绝产品/包名/学校/schema 不匹配及版本倒退，失败保留当前配置；重启使用内置默认。当前不持久化远程缓存，不提供正式更新。

## 预览限制

不是原生川农课表同步、成绩解析或考试导入；没有伪造学号绑定。教务校园网及登录后步骤待真实账号验收。动态配置的服务端测试与模拟器证据不能替代真实手机。详见 ADAPTATION.md、SECURITY_BOUNDARY.md 和 VALIDATION.md。
