# 我的川农 Android · 完整产品恢复预览

`com.mychuannong.app` · `my-chuannong` · `sicau` · `0.1.0-preview.2 / 2`。非学校官方应用，未正式发布。

实际运行主体为已验收 V1.0.5 的 MainScreen / MainBottomBar、OverviewScreen、FeatureFragment、ProfileSettingsRoute，连同原生课表、成绩、考试、作业、平台账号、提醒、小组件和动态配置能力。见 BASELINE.md 的逐文件映射。preview.1 历史保留，不延续简化导航壳。

学校数据通路：绑定页 → 本机学校 WebView → SchoolReader → SchoolSyncEngine → 原 Room/MMKV → 原 ViewModel/首页/原生列表/提醒和小组件。**川农身份与课表、成绩、考试解析规则未完成**，当前明确提示该状态；不把页面打开当作绑定。学期/作息/绩点口径未确认，保留页面结构但不套用长理规则。

## 构建

JDK 17、SDK 36.1、Gradle wrapper。local.properties 指向已有 SDK。设置外部 `MY_CHUANNONG_SIGNING_CONFIG` 为本机独立 signing.properties；运行 `./gradlew.bat :app:assembleRelease --no-configuration-cache`。输出 `app/build/outputs/apk/release/MyChuannong_release_v0.1.0-preview.2.apk`。签名文件不在仓库，不自动上传。

## 本地联调

恢复后的独立后台使用 127.0.0.1:8792；连接手机/模拟器执行 `adb -s DEVICE_SERIAL reverse tcp:8792 tcp:8792`。内置配置无需后台即可进入三个主页面与本地功能/学校网页；平台账号、远端公告和内容服务需电脑后台。没有生产域名，APK 使用回环地址，不能误认为手机可直接连接电脑 localhost。

本地平台测试账号说明位于受限目录的“平台联调账号说明.txt”；这不是学校账号。正常安装不预置账号或假课程。学校真实数据验收需负责人在本人设备与学校网络完成，不提供学校密码给开发方。

产品官网横幅进入筹备页；隐私/协议在 App 内。当前检查更新保留交互，无正式更新包。学校公网入口及微信/外部应用缺口见 ADAPTATION.md。
