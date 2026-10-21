# Android 1.0.0 接手入口

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

用户保管原 JKS 和签名配置，构建通过仓库外 `MY_CHUANNONG_SIGNING_CONFIG` 引用；不能换成新签名或 Debug 签名。当前尚未取得原签名，因此尚无本次已签名 APK。包名 `com.mychuannong.app`，版本 `1.0.0 / 10`，APK 名称 `my川农_chuannong_1.0.0.apk`。使用 `python tools/build_release.py --output-dir <仓库外交付目录>` 生成包及源码对应凭据，详见交付说明。

服务配置尚待用户部署：`campusTools.apiBaseUrl.release` 为本产品 HTTPS API；`amap.apiKey.release` 为原签名与包名对应的高德 Android Key。学校认证与平台账号分开，禁止将电脑 localhost 当正式手机服务地址。

配套后台使用同名交付分支：[后台仓库](https://github.com/HEltDeng/my-chuannong-backend/tree/codex/release-1.0.0)。只需源码和依赖即可搭建新环境，不依赖旧电脑截图、输出目录或数据库。真实运营数据、上传文件、备份、签名与服务器凭据另行保管和迁移。
