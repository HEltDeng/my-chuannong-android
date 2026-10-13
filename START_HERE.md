# 从这份已验收源码继续开发

本仓库的交付基线是 **2026-10-13 审计后、用户确认的 preview.9**。默认开发分支为 `codex/accepted-preview9`。只将最终功能与审计修复合并为本次交付提交；原有 Git 历史保留，但旧分支、旧交接章节和本机历史 APK 不作为开发起点。

## 另一台电脑如何接手

在 GitHub 插件中选择 `HEltDeng/my-chuannong-android`；涉及平台后台时同时选择 `HEltDeng/my-chuannong-backend`。让开发助手先读取根目录 `AGENTS.md`、本文件和 `README.md`，使用默认分支，不依赖此前聊天记录。本仓库包含完整 Android 工程和当前源码，不需要旧电脑的 `05_Output`、截图或缓存。

可直接使用这段接手指令：

> 请读取 HEltDeng/my-chuannong-android 和 HEltDeng/my-chuannong-backend 默认分支的 AGENTS.md、START_HERE.md 与 README.md，从 2026-10-13 已验收的 preview.9 审计版继续开发。保留绿色主题、VPN 优先与校园网备用、已绑定身份和在线会话分离、离线课表提醒以及页内刷新。先核对源码清单并恢复本机开发环境，不回退到旧预览分支；后端缺项按审计报告处理。

GitHub 插件可读取仓库；编译 APK 还需要新电脑上的 JDK/Android SDK。实际构建时克隆整个仓库，不要只下载单个源文件：

```text
git clone https://github.com/HEltDeng/my-chuannong-android.git
cd my-chuannong-android
git branch --show-current
git rev-parse HEAD
```

默认分支应为 `codex/accepted-preview9`。首次验收提交的精确 SHA 记录在版本附件 `delivery-manifest.json`；后续正常开发可以产生新提交。

## 构建环境

需要 Git、JDK 17、Android SDK `platform-tools`、`platforms;android-36.1`、`build-tools;36.0.0`。Gradle 9.4.1 wrapper 和 wrapper JAR 已在仓库中；Android Studio 可选择本机 SDK，也可设置 `ANDROID_HOME`。首次构建需联网解析公开依赖。

Windows PowerShell，在仓库根目录执行（路径替换为新电脑实际位置）：

```powershell
$env:JAVA_HOME = 'C:/Dev/jdk-17'
$env:ANDROID_HOME = 'C:/Dev/android-sdk'
$env:PATH = $env:JAVA_HOME + '/bin;' + $env:PATH
& "$env:ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager.bat" --licenses
& "$env:ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager.bat" 'platform-tools' 'platforms;android-36.1' 'build-tools;36.0.0'
.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug :app:lintDebug :app:lintRelease --no-configuration-cache
```

macOS/Linux 设置本机 `JAVA_HOME` 与 `ANDROID_HOME` 后，使用 `sh ./gradlew` 执行同一组任务。也可用 Android Studio 生成本机 `local.properties`，该文件不提交。

构建产物：`app/build/outputs/apk/debug/MyChuannong_debug_v0.1.0-preview.9.apk`。审计时默认 Debug 的 247 项 JVM 测试通过；Debug/Release Lint 零错误但仍有警告，详见审计报告。学校功能开发不需要启动本产品后台。

## 下载与核对本次 APK

[本次验收 APK 与校验文件](https://github.com/HEltDeng/my-chuannong-android/releases/tag/preview9-audited-20261013)（私有仓库，需要已获访问权限的 GitHub 账号）。版本附件只包含本次最新审计 APK，不放入 Git 源码历史。

- 文件：`MyChuannong_preview9_audited.apk`，64,179,855 字节。
- SHA-256：`e30fe05c437edaf46212fa21de3a01ee8070c16145090f416a8d53cbcd5984ea`。
- 包名 `com.mychuannong.app.debug`，版本 `0.1.0-preview.9 / 9`；它是已验收内部 Debug 预览包，不是正式生产签名包。
- 证书 SHA-256：`a0fee4a511b677d3e2bfc1abba571e6d83fb0e579a8b72a9d185946a3b9626b6`。

另一台电脑首次构建会生成自己的 Debug 签名，代码可继续开发，但新签名通常无法直接覆盖已安装的上述 APK。原签名私钥不放 GitHub；可用隔离应用 ID 调试，正式签名继续通过仓库外 `MY_CHUANNONG_SIGNING_CONFIG` 配置。不要为解决安装冲突清除用户原有数据。

有 Python 3 时，可运行 `python tools/verify_checkout.py` 核对本次交付的构建输入清单。此检查支持跨系统文本换行差异；开始修改代码后出现差异是预期行为，不应为了让检查通过重置开发成果。

## 继续开发时保留的行为

- APP、网络绑定反馈和桌面小组件统一使用川农绿色；网络检查与成功动画沿用现有风格。
- 首次绑定默认先连接官方校园 VPN，再验证本人教务账号；校园网直连作为备用。VPN 和教务会话不是同一次登录。
- 已核验身份持久保存，在线过期不等于未绑定；有缓存时先显示课表。姓名只从已核验的本人资料读取，展示姓氏加“同学”。
- 上课提醒使用本地缓存和 Android 系统调度；课表刷新在页内显示进度/灰色更新时间，不恢复旧图片导入弹窗。
- 学校密码、验证码、Cookie、受保护数据只在本人手机处理，不进入产品后台或 GitHub。

## 证据和待办入口

- [完整源码审计](docs/audit/2026-10-13-source-audit.md)：修复与剩余风险；其中“尚未上传”描述的是审计完成时的历史状态。
- [交付源码清单](docs/delivery/accepted-source-manifest.json)：本次应用、测试、资源、Gradle 构建输入的完整性记录。
- [后端接手入口](https://github.com/HEltDeng/my-chuannong-backend/blob/codex/accepted-preview9/START_HERE.md)：后台已有源码，但保留审计确认的缺项，不宣称已生产验收。
- [详细历史交接](docs/handoff/MYCHUANNONG_HANDOFF.md)：按日期阅读；本文件与默认分支代表本次接手起点。
