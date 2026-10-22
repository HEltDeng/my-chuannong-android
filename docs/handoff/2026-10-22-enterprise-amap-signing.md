# 企业高德 Key 与原签名构建

2026-10-22 高德控制台已显示企业认证开发者。经用户当次确认协议及创建 Key，在“我的川农”应用中建立“川农Android正式”，平台为 Android，绑定包名 `com.mychuannong.app` 和原证书 SHA-1 `D7:FD:1B:9E:95:EC:C9:99:A6:96:11:6C:DC:54:D2:41:14:97:75:32`。未添加调试签名。

Key 保存在当前 Mac 仓库外 `~/.local/share/my-chuannong/signing/amap-release.key`，权限 600。开发工作区的 `scripts/env.sh` 读取它并设置 `AMAP_API_KEY_RELEASE`；Gradle 将其写入 Release 的 `BuildConfig.AMAP_API_KEY`。已有 `CampusAmapConsent.initialize` 在用户同意后将该值传给地图与搜索 SDK，无需把 Key 明文提交到源码。

继续使用同目录已有的原 JKS 和 `signing.properties`。未新建或替换签名身份。原证书 SHA-256 必须匹配 `a8f8eac6ff810cac0b21cbd59a08d5525e1aca9c225f86f516fbd1172becdf6d`。版本仍为 1.0.0 / 10。

本次构建清单纳入此前已提交的正式 API、关注通知、平台账号门禁和前台统计修改，以及 5 个新增源码/测试文件；没有撤销这些授权修改。构建前原签名校验与 368 项 Release JVM 测试通过。实际 APK、签名验证及后续运行检查以工作区 `outputs/` 中本次交付记录为准。

用户确认本次仅制作测试用签名包。高德表单说明未购买技术服务许可时，仅供短期、少量测试；企业认证不等于已获得正式运营许可。本次未购买许可、未上架或公开发布 APK。

参考：[高德 Android Key 配置](https://developer.amap.com/api/android-sdk/guide/create-project/get-key)。
