> 历史准备记录，已被 [1.0.0 源码交付](2026-10-21-release-1.0.0.md) 取代；不再要求在本机构建或接收 JKS。

# Release 发布前审核包

本文件记录首次 release 准备时的检查与阻塞。用户随后明确授权找不到旧 JKS 时新建，并在后台/高德配置尚未部署时先交付本地审核 release；最新签名与交付状态以 [1.0.0 交付记录](2026-10-21-release-1.0.0.md) 为准，下方“必须找回旧密钥”的要求已被这次授权替代。

用户要求：所有既有修改完成后，只交付真正的 release 构建，不再交付 QA/debug。签名材料保存在本地且延续首次 release 身份。本次不发布官网，不创建公开 Release/Tag，不部署生产服务。

后续冻结指令：版本为 `1.0.0`，Android 升级序号为 10，审核 APK 统一命名 `my川农_chuannong_1.0.0.apk`。用户授权本地提交 Android 和后台的已完成源码，但在确认审核包前不推送 GitHub。业务功能冻结，本轮仅处理版本/产物名、交付记录和签名恢复。

## 当前状态

尚未生成可交付的签名 release APK。2026-10-21 检查本机 C/D/E 盘可访问的 JKS、keystore、PKCS12、签名配置以及项目历史交付 ZIP，未发现原 release 私钥。现有 Android debug.keystore 和 Gradle 自用 keystore 均不是原 release 身份。项目补充 ZIP 不含正式签名材料。

原交接记录中的位置是 `D:\Codex-Local-Secrets\MyChuannong\signing.properties`，此路径在本机不存在；环境变量 `MY_CHUANNONG_SIGNING_CONFIG` 未配置。须由用户找回原电脑/备份中的配置、JKS 和原口令，不能从 APK 中恢复私钥，不能生成新身份替代。

按用户“先打开位置”要求，已新建并打开 `D:\Codex-Local-Secrets\MyChuannong`，只允许当前用户和 SYSTEM 访问。建立时文件数为 0；此操作仅准备恢复位置，没有找回或生成私钥，`signing.properties` 仍不存在。

历史 release 记录（见 `MYCHUANNONG_HANDOFF.md` B6）：

- 包名：`com.mychuannong.app`。
- 证书 SHA-256：`a8f8eac6ff810cac0b21cbd59a08d5525e1aca9c225f86f516fbd1172becdf6d`。
- 历史 APK：`MyChuannong-jiaowu-20261008-release-acceptance.apk`，版本 `0.1.0-preview.8 / 8`。
- 历史 APK SHA-256：`7ec584abc885de032ff56efd73c63347ddfed8bfbb9f74d682de18500b42eadf`。

本机目前仅有 Debug 高德配置，平台账号服务也只有 `127.0.0.1:8792` 本机联调。Release 不允许使用此地址处理账号登录。需要已授权可用的 HTTPS 服务，以及绑定正式包名/原证书的高德 Android Key；不自动部署、不借用 Debug Key。

## 本轮构建调整

- Release 明确关闭 `isDebuggable`，保留原 R8 混淆和资源压缩。
- 增加 `campusTools.apiBaseUrl.release`（本机 `local.properties`）或 `MY_CHUANNONG_CAMPUS_TOOLS_API_BASE_URL_RELEASE` 配置入口；必须为 HTTPS，路径为 `/api/v1/`，不含账号密码、查询或片段。
- 未配置时使用禁用域名哨兵，允许离线源码测试和 lint，但拒绝交付构建，不再把电脑回环地址写入默认 release。
- `validateReleaseSigning` 读取仓库外配置/JKS，验证私钥可用、证书有效期和历史证书 SHA-256。不会输出密码或私钥。
- `validateReleaseConfiguration` 额外检查账号服务及独立高德 Key；`assembleRelease`、`bundleRelease` 和 `installRelease` 必须经过检查。
- 将既有 Windows 中文路径测试类暂存兼容处理扩展到 Release，使 release JVM 测试使用自己的编译产物运行，不依赖 Debug 测试结果。

## 已完成的验证

- `:app:testReleaseUnitTest :app:lintRelease --no-configuration-cache` 成功；359 项 release JVM 测试通过，0 失败、0 错误、0 跳过。
- Release lint：0 错误、1506 条警告、3 条提示；不代表警告已全部清除或真实服务已验证。
- 缺失签名配置、误用 Debug 证书、误用本机 HTTP 账号地址三个负向检查均按预期拒绝。
- 工作区 `05_Output/release-review-20261021/` 保存只含非密钥信息的 readiness JSON、检查日志和 lint 报告。`apkProduced=false`、`originalJksBackedUp=false`、`websitePublished=false`；不能将这份准备记录称为已交付安装包。

## 找回配置后的交付步骤

1. 原签名配置与 JKS 放到仓库外受限目录。核对原文件哈希，再在另一处受限本地目录备份两者，校验副本一致；口令不得写入聊天、日志、普通交付文件或 Git。
2. 配置 `MY_CHUANNONG_SIGNING_CONFIG`。配置中的 `storeFile/storePassword/keyAlias/keyPassword` 必须对应原签名；修改迁移后的文件路径不应修改密钥内容。
3. 设置账号服务 HTTPS 地址和 `amap.apiKey.release` / `AMAP_API_KEY_RELEASE`。配置文件里的 Key 不进入 Git；通过正式签名包验证地图授权及账号服务，不能仅据 Key 格式宣称服务正常。
4. 先执行 `:app:validateReleaseConfiguration --no-configuration-cache`，通过后执行 `:app:testReleaseUnitTest :app:lintRelease :app:assembleRelease --no-configuration-cache`。
5. 使用 `apksigner verify --verbose --print-certs` 检查最终 APK 与历史证书一致，检查最终 Manifest 包名、版本、非调试属性和应用名称；保存 SHA-256、R8 mapping 与构建证据。
6. 安装验证时保留用户已有数据，不通过卸载/清数据解决签名差异。审核 APK 与签名备份分开保存，不将签名配置或 JKS 放进审核包目录。
7. 仅交付本地审核 APK 与必要说明，不上传官网或公开下载渠道。

当前源码版本为 `1.0.0 / 10`，高于已记录的 preview.8 / preview.9。原签名恢复后仍需检查待覆盖安装版本；本轮没有用重命名方式将 Debug 转成 Release，也没有生成替代 JKS。
