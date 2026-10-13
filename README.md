# 我的川农 Android · 完整产品恢复预览

**另一台电脑接手开发请先读 [START_HERE.md](START_HERE.md)。默认分支 `codex/accepted-preview9` 为 2026-10-13 用户确认的最新审计版。** [下载本次 APK / 校验文件 / 源码提交对应清单](https://github.com/HEltDeng/my-chuannong-android/releases/tag/preview9-audited-20261013)。旧章节仅作为历史记录。

## 当前状态与源码审计（2026-10-13）

当前版本为 `0.1.0-preview.9 / 9`，仅供内部预览。默认先连接校园 WebVPN，再验证本人教务账号，校园网直连为备用。手机直接读取学校数据；课表、成绩、考试在本机缓存。离线课表、提醒和小组件不依赖 VPN 在线。学校服务端会话到期仍需要重新认证。

课表刷新采用页内进度与更新时间，旧图片课表导入已移除；官方校历图片解析仍保留。主页显示已绑定同学的姓氏称呼，APP 与小组件采用绿色主题。

2026-10-13 成绩界面调整：成绩查询返回按钮复用现有绿色圆形按钮；首页“成绩汇总”独立展示大标题，右下角眼睛按钮整体折叠/恢复全部详情，并沿用本机可见性偏好。成绩数据与其他首页模块保持原有来源和逻辑。

完整审计、已修复问题及未解决项见 [2026-10-13 源码审计报告](docs/audit/2026-10-13-source-audit.md)。独立后端源码已存在于 `my-chuannong-backend`，但尚未通过完整测试及生产验收。当前 APK 未配置可用的 HTTPS 平台账号服务，学校功能不依赖后台。

JDK 17、SDK `platforms;android-36.1`、build-tools 36.0.0；本机 SDK 路径写入不提交的 `local.properties`。Windows 验证命令：

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug :app:lintDebug :app:lintRelease --no-configuration-cache
```

macOS/Linux 使用 `sh ./gradlew`。默认 Debug 包名 `com.mychuannong.app.debug`，产物 `app/build/outputs/apk/debug/MyChuannong_debug_v0.1.0-preview.9.apk`。Lint 错误会中断构建。

设备回归另用 `:app:assembleDebug :app:assembleDebugAndroidTest '-PcacheTestSuffix=.bindingtest' '-PdeviceTestBuildType=debug'` 创建隔离包。普通 JVM 包标识测试使用默认 Debug 包名，不能混用上述隔离参数。隔离构建后重跑普通 `assembleDebug` 才是交付包。

Release 仅从仓库外 `MY_CHUANNONG_SIGNING_CONFIG` 读取本产品独立签名。不得提交学校账号、Cookie、私钥、JKS、数据库、用户截图、原始个人页面、APK 或构建日志。本轮没有正式发布、Tag 或生产部署。

**下方为历史恢复记录，版本号、解析进度、测试数量和平台联调状态均不代表当前版本。**

## 当前 Windows 交接入口（2026-10-09）

请先阅读 [MYCHUANNONG_HANDOFF.md](docs/handoff/MYCHUANNONG_HANDOFF.md)。当前包内版本为 `0.1.0-preview.8 / 8`，业务源码基线为 `8e842db648258cb15c70538ce5a85f1e2db0ef19`。课表、成绩、考试请求与解析，以及独立教务密码、Room 缓存保护已经实现；189 项 JVM 测试通过，但校园网 Android 真实账号端到端仍未验证，原生 WebVPN 代理适配尚未实施。学校数据验证无需启动 Backend，Debug 无需正式签名。

以下 preview.3 及 Mac 恢复内容保留为历史说明，不能据其中“解析仍未完成”判断现行实现。恢复与校园网验证以交接文档为准。

`com.mychuannong.app` · `my-chuannong` · `sicau` · `0.1.0-preview.3 / 3`。非学校官方应用，未正式发布。

实际运行主体为已验收 V1.0.5 的 MainScreen / MainBottomBar、OverviewScreen、FeatureFragment、ProfileSettingsRoute，连同原生课表、成绩、考试、作业、平台账号、提醒、小组件和动态配置能力。见 BASELINE.md 的逐文件映射。preview.1 历史保留，不延续简化导航壳。

学校数据通路：原生绑定表单 → 手机直连川农认证/学校验证码 → 会话复核与本人身份确认 → SchoolReader / SchoolSyncEngine → 原 Room/MMKV 和原生页面。公开验证码初始化已在模拟器验证，密码认证与身份确认待真实账号测试；**教务业务回跳及课表、成绩、考试解析仍未完成**。学期名称按明确入学年份复用母版“大几上/下”，未知则显示准确学年；作息、校历、绩点口径不套用长理规则。见 [本轮源码映射与验证边界](docs/PREVIEW3_FIXES.md)。

## 构建

JDK 17、SDK 36.1、Gradle wrapper。local.properties 指向已有 SDK。设置外部 `MY_CHUANNONG_SIGNING_CONFIG` 为本机独立 signing.properties；运行 `./gradlew.bat :app:assembleRelease --no-configuration-cache`。输出 `app/build/outputs/apk/release/MyChuannong_release_v0.1.0-preview.3.apk`。签名文件不在仓库，不自动上传。

## 本地联调

恢复后的独立后台使用 127.0.0.1:8792；连接手机/模拟器执行 `adb -s DEVICE_SERIAL reverse tcp:8792 tcp:8792`。内置配置无需后台即可进入三个主页面与本地功能/学校网页；平台账号、远端公告和内容服务需电脑后台。没有生产域名，APK 使用回环地址，不能误认为手机可直接连接电脑 localhost。

本地平台测试账号说明位于受限目录的“平台联调账号说明.txt”；这不是学校账号。正常安装不预置账号或假课程。学校真实数据验收需负责人在本人设备与学校网络完成，不提供学校密码给开发方。

产品官网横幅进入筹备页；隐私/协议在 App 内。当前检查更新保留交互，无正式更新包。学校公网入口及微信/外部应用缺口见 ADAPTATION.md。

## Mac 开发恢复（当前开发分支）

仓库 https://github.com/HEltDeng/my-chuannong-android ，分支 `codex/restore-v105-adaptation`。
本次归档保留现有 preview.8 工作状态，包括教务会话实现、课表导入、提醒和页面资源；前文 preview.3/4 是历史说明。真实校园账号/设备验收仍单独进行，不等同于正式发布。

```sh
git clone --branch codex/restore-v105-adaptation https://github.com/HEltDeng/my-chuannong-android.git
cd my-chuannong-android
export JAVA_HOME=$(/usr/libexec/java_home -v 17)
export ANDROID_HOME="$HOME/Library/Android/sdk"
printf 'sdk.dir=%s\n' "$ANDROID_HOME" > local.properties
sh ./gradlew :app:testValidationUnitTest :app:assembleDebug --no-configuration-cache
```

先安装 Android SDK `platforms;android-36.1` 与对应 build-tools；Gradle 使用仓库 wrapper。首次构建需联网下载依赖。Debug 不需要正式签名。正式签名配置通过外部 `MY_CHUANNONG_SIGNING_CONFIG` 指定，JKS 与口令不在 Git 中；不得新建签名代替原身份。实际输出版本以 app/build.gradle.kts 为准。
Session/Cookie 的实现源码属于仓库；真实会话、账号、验证码图片、日志、APK、设备截图均不归档。学校数据由本人设备登录取得。后端源码从独立 my-chuannong-backend 恢复，不复用长理数据库或认证值。
