# 失物招领审核与同步（2026-10-19）

寻物和招领均采用待审核、已发布、违规三种审核状态。提交、修改后重新审核，通过后才进入公开列表；驳回和下架原因通过模块内“处理通知”和“我的发布”展示。删除同步后台和手机端，重进不会恢复，在线删除不再提供撤销。

保留实际失物字段：名称、校区、地点、日期、描述、单张照片和微信号/电话。寻物完成标记为“已找回”，招领为“已归还”；完成进度与审核分开，完成后不在公开列表展示。仅已发布记录可完成或重新开放，违规不能绕过审核。详情照片改为原比例展示并可打开大图，长内容可滚动。

前台每 10 秒同步，也支持刷新按钮、返回前台和重新进入。连接失败不展示本机示例公开内容。通知为 APP 内通知，尚未接入系统推送。本地后台需运行，模拟器通过 `adb reverse tcp:8792 tcp:8792` 连接。

## 源码与测试

源码：`web/lost-found/src/App.tsx`、`online.ts`、`types.ts`、`native.css`；原生容器 `LostFoundActivity.kt`。打包目标是 `app/src/main/assets/lost-found/`，不要直接修改生成的 JS/CSS。

2026-10-19：失物网页测试 14 项、二手数据回归 30 项、Android JVM 318 项通过，Debug/Release Lint 零错误（既有警告保留）；后端完整测试 89 项通过。模拟器完整用例通过，覆盖寻物/招领审核、原因通知、修改重新审核、完成/重新开放、三种审核状态删除及重载不恢复。浏览器核对真实字段、组合搜索和长图滚动。

网页构建（已有依赖时）：

```powershell
# web/lost-found 目录，普通开发环境也可使用 npm test / npm run build
node --test tests/*.test.mjs
node node_modules/typescript/bin/tsc
node node_modules/vite/bin/vite.js build
node scripts/package.mjs
```

隔离设备验收（Android 仓库根目录，本地后台先启动）：

```powershell
.\gradlew.bat :app:assembleDebug :app:assembleDebugAndroidTest '-PcacheTestSuffix=.lostreviewtest' '-PdeviceTestBuildType=debug' --no-configuration-cache
adb install -r app/build/outputs/apk/debug/MyChuannong_debug_v0.1.0-preview.9.apk
adb install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb reverse tcp:8792 tcp:8792
# 终端一：仅处理“失物验收-”合成记录；支持 --adb、--serial、--credentials。
python tools/lostfound_review_driver.py --report-dir ../../05_Output/lostfound-retest
# 终端二：
adb shell am instrument -w -e class com.mychuannong.app.feature.lostfound.LostFoundReviewDeviceTest com.mychuannong.app.lostreviewtest.test/androidx.test.runner.AndroidJUnitRunner
```

认证夹具仅位于 androidTest，生产代码没有绕过首次绑定的入口。管理员凭据在仓库外，由电脑上的驱动读取，不传入设备。设备结果 `lost-review-device-result.json` 包含两条待审预览记录 ID，用于浏览器检查后精确清理。此前仅本机演示的设备用例属于历史流程，本次使用新的联网审核用例；离线存储测试仍保留。

交付必须重新构建默认包，不带隔离参数：

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug :app:lintDebug :app:lintRelease --no-configuration-cache
```

确认输出包名为 `com.mychuannong.app.debug` 后覆盖安装。当前为本机内部预览，未正式发布或部署公网；学校凭据和受保护数据仍仅在手机处理。
