# Android 接手入口

当前工作版本为 1.0.1（11），最新同步说明见 [2026-11-18 iOS 对齐](docs/handoff/2026-11-18-ios-alignment.md)。下方 1.0.0 是原签名及历史交付基线。

从 `codex/release-1.0.0` 开始。先读 AGENTS.md、README.md 和 [交付说明](docs/handoff/2026-10-21-release-1.0.0.md)。这是审核冻结的 1.0.0 源码，不是 preview 版本。历史审计和旧 APK 不作为本次交付。

```text
git clone --branch codex/release-1.0.0 https://github.com/HEltDeng/my-chuannong-android.git
cd my-chuannong-android
git rev-parse HEAD
```

需要 Git、JDK 17、Android SDK `platform-tools`、`platforms;android-36.1`、`build-tools;36.0.0`。Gradle wrapper 与 JAR 已随仓库提供。配置自己的 JAVA_HOME、ANDROID_HOME 或 Android Studio 的 local.properties；这些本机路径不提交。

```powershell
.\gradlew.bat :app:testReleaseUnitTest --no-configuration-cache
python tools/verify_checkout.py
```

用户保管原 JKS 和签名配置，构建通过仓库外 `MY_CHUANNONG_SIGNING_CONFIG` 引用；不能换成新签名或 Debug 签名。原文件已通过用户提供的迁移包恢复至仓库外，私钥可用且与历史证书一致；其他电脑仍须单独恢复原签名，不能从 GitHub 获取密钥。包名 `com.mychuannong.app`，版本 `1.0.0 / 10`，APK 名称 `my川农_chuannong_1.0.0.apk`。使用 `python tools/build_release.py --output-dir <仓库外交付目录>`（Mac 可使用 `python3`）生成包及源码对应凭据，详见交付说明。

当前账号服务使用 `campusTools.apiBaseUrl.release` 指定的本产品 HTTPS API。2026-11-18 已同步移除校园地图与高德 SDK，不再需要地图 Key。协议从仓库外已核准的 `2026-11-17.1/MyChuannongLegal.json` 注入，可用 `-PmyChuannongLegalDir=<私有协议目录>` 指定位置；构建只打包该 JSON，并校验版本与四份正文完整性。学校认证与平台账号分开，禁止将电脑 localhost 当正式手机服务地址。

配套后台使用同名交付分支：[后台仓库](https://github.com/HEltDeng/my-chuannong-backend/tree/codex/release-1.0.0)。只需源码和依赖即可搭建新环境，不依赖旧电脑截图、输出目录或数据库。真实运营数据、上传文件、备份、签名与服务器凭据另行保管和迁移。
