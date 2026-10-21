# 我的川农 Android 1.0.0

已审核冻结的 **1.0.0** 源码，内部升级序号 **10**。交付分支：`codex/release-1.0.0`。本轮上传源码及配套后台，不构建 JKS 或 APK；用户在自己的电脑使用原签名打包。不包含官网发布或服务器部署。

新电脑接手请读 [START_HERE.md](START_HERE.md)；版本、签名配置和服务依赖见 [1.0.0 源码交付说明](docs/handoff/2026-10-21-release-1.0.0.md)。配套后台：[HEltDeng/my-chuannong-backend](https://github.com/HEltDeng/my-chuannong-backend/tree/codex/release-1.0.0)。

## 冻结的功能

- 首次川农 e / VPN 认证后绑定教务；学校会话分别保存与恢复，网络失败保留课表、成绩和考试缓存。
- 学校服务统一认证、目标跳转和返回；我的川农平台账号独立管理内容服务权限，登录后继续原入口。
- 首页取消下拉刷新，课表保留右上角刷新；密码光标、输入框颜色、高德授权弹窗、VPN 验证过渡和页面流畅度已调整。
- 后台管理的二手好物、失物招领、校外机构与预约、公告和资料接入已保留。运行内容来自后台，不随源码分发示例运营记录。

## 服务与签名配置

正式账号服务 HTTPS 地址和高德 release Key 尚未部署配置。源码版本为 1.0.0 不代表这些服务已上线；学校认证使用学校官方服务。详见交付说明中的环境变量和配置项。

学校凭据与会话保存在设备安全存储，后台不接收学校密码、Cookie、课表或成绩。JKS、签名口令、本地配置、运行库、上传文件和备份不在 Git 中。未来正式 APK 文件名为 `my川农_chuannong_1.0.0.apk`，应用显示名保持“我的川农”。

## 源码检查

Android 使用 JDK 17、Android SDK 和随仓库提供的 Gradle wrapper。可运行 `:app:testReleaseUnitTest` 检查 JVM 测试，无需生成 APK。Web 内容源码位于 `web/lost-found/`，`npm test` 验证数据操作；`npm run build` 更新随包网页资源。二手与服务脚本测试位于 `tools/test_*.cjs`。后台测试在后台仓库运行。

`python tools/verify_checkout.py` 核对冻结源码清单；开发后的有意修改会产生差异，不能为通过检查而撤销开发成果。`docs/handoff/` 和 `docs/audit/` 的旧日期记录仅供追溯，以本页及 1.0.0 交付说明为当前状态。
