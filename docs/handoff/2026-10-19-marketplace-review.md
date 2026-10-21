# 二手好物：出售与求购审核（2026-10-19）

本次将出售、求购接入统一的待审核、已发布、违规流程。提交或编辑后必须重新审核，只有管理员通过的信息在公开区域展示。管理后台驳回和下架必须填写原因，APP「处理通知」与「我的」显示标题、结果和原因。删除以服务器为准，刷新及重新进入后不恢复，正在查看的大图也会随删除关闭。

后台详情按手机阅读顺序展示真实发布内容。双方图片按原比例展示，支持全部图片、大图切换及长内容滚动；求购也支持参考图片。旧网页演示数据不上传到服务端，原生桥连接失败会显示重试入口，不以本机演示内容冒充已发布数据。

同日分类调整：移除首页筛选及出售、求购发布面板的「高速材料」选项，保留其余 7 个分类。旧本地记录及服务端返回的旧分类统一归到「其他」，保留内容、图片、联系方式和审核状态；标题、描述里的具体关键词仍可搜索。Node 相关用例现为 32 项通过，Debug 构建成功，已覆盖安装到正常模拟器 APP，并实测两个发布面板的 7 项分类和选择交互。证据保存在工作区 `05_Output/secondhand-remove-category-20261019/`。

## 本机使用

1. 在后台仓库启动 `start-local.ps1`，管理页 `http://127.0.0.1:8792/admin/`。
2. 执行 `adb reverse tcp:8792 tcp:8792`，打开正常 Debug APP「服务 → 二手好物」。
3. 出售或求购提交后在「我的」显示待审核，后台通过后才在公开列表显示。
4. 管理员处理后，模块前台每 10 秒同步；主动刷新、恢复前台和重新进入也同步。「处理通知」显示处理对象、结果、原因，并支持已读标记。

通知为模块内持久通知，尚未接入 APP 关闭时的系统推送。当前本地服务器需持续运行；没有公网部署。本次不改变学校首次绑定、会话与缓存规则，学校凭据及受保护数据不上送平台后台。

## 主要源码

- `app/src/main/assets/secondhand/online.js`：服务端状态、分页同步、通知、串行写入、错误处理和删除缓存清理。
- 同目录 `app.js`、`styles.css`：发布审核、本人状态与原因、处理通知、全图预览和刷新。
- `SecondhandActivity.kt`：恢复前台同步。
- 后台仓库 `marketplace.py`、`admin/marketplace.js`、`admin/marketplace.css` 及 `tests/test_marketplace_review.py`。

## 验证与复测

2026-10-19：后端 81 项、Node 30 项、Android JVM 318 项通过；Debug/Release Lint 零错误（既有警告保留）。真实模拟器集成用例通过：出售和求购分别覆盖待审核隐藏、通过公开、驳回和下架原因通知，以及三种状态删除、重新载入无记录、大图打开时删除。后台浏览器验证原因必填、保存后刷新、组合筛选、全图及长正文滚动。测试数据与截图均为合成内容。

常规构建及数据层测试：

```powershell
node --test tools/test_secondhand.cjs tools/test_campus_online.cjs
.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug :app:lintDebug :app:lintRelease --no-configuration-cache
```

设备用例必须使用隔离包，不能在用户实际包中执行：

```powershell
.\gradlew.bat :app:assembleDebug :app:assembleDebugAndroidTest '-PcacheTestSuffix=.marketreviewtest' '-PdeviceTestBuildType=debug' --no-configuration-cache
adb install -r app/build/outputs/apk/debug/MyChuannong_debug_v0.1.0-preview.9.apk
adb install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb reverse tcp:8792 tcp:8792
# 终端一：主机驱动读取仓库外本地管理员配置，仅处理“联调验收-”测试记录。
python tools/marketplace_review_driver.py --report-dir ../../05_Output/marketplace-retest
# 终端二：运行真实 WebView / 原生桥 / API 场景。
adb shell am instrument -w -e class com.mychuannong.app.feature.secondhand.MarketplaceReviewDeviceTest com.mychuannong.app.marketreviewtest.test/androidx.test.runner.AndroidJUnitRunner
```

驱动支持 `--adb`、`--serial`、`--credentials`；只允许本机后台。学校认证由 androidTest 内的 HTTP 合成夹具完成，生产代码没有新增绕过首次绑定的入口。设备不接收管理员密钥。成功结果保存在隔离包外部 files 下 `market-review-device-result.json`，其中两条待审核详情预览记录用于浏览器检查，检查后按精确 ID 清理测试记录及隔离包。

交付前重新执行不带隔离参数的常规构建，核对 `output-metadata.json` 包名是 `com.mychuannong.app.debug` 后安装到用户模拟器。APK 未公开发布，工作区原有改动保留。
