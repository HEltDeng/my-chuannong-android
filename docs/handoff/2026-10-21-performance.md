# 2026-10-21 流畅度优化

用户反馈实体手机滑动及切换页面有卡顿。本次在现有布局与账号体系上减少重复创建、重组和主线程缓存处理，没有修改学校认证协议、平台会话、密码输入框或业务导航目标。

## 变更

- `MainScreen` 将每帧 pager 偏移读取收敛到 `PagerBottomBar`，避免整个页面宿主随动画每帧失效。保持原有 220 ms 过渡、滑动与触觉反馈。
- 第一次完成切页后保留首页、服务、我的三个原生页面，后续切换复用组合及滚动位置。启动时不额外保留全部页面；最多三个原生主页面，详情页和 WebView 不随此策略缓存。代价是三个主页面的驻留内存，不能无上限扩大到业务页面。
- 只有当前可见的“我的”页注册有效返回处理和显示弹窗，防止离屏子页吞掉首页返回。服务动态离屏时停止自动轮播。
- 服务页改为带稳定 key/contentType 的 `LazyColumn`，按可见范围创建服务区块，保留卡片样式、间距及入口。快捷项目由配置内容记忆，配置变化时仍正确更新。
- 服务页自动进入检查复用最近 30 秒成功的公开配置/动态。手动下拉仍立即刷新；失败保留内容。请求互斥继续保留，取消不吞掉。
- 公开配置与动态的编码、校验及 MMKV 写入在 IO dispatcher 完成；首页 MOOC 缓存解析与本地课程读取一并在后台完成，通过身份/版本检查后一次发布页面状态。
- 删除应用常驻每 2 秒查询进程内存的诊断线程。保留轻量启动时间记录。

## 可复现性能对照

`MainInteractionPerformanceDeviceTest` 使用隔离包 `.perftest`、合成学校身份及拦截器，不提交学校账号。使用真实 Choreographer 时钟与同一导航处理器，避免 Compose 测试时钟跳过动画。两轮预热后测量 12 次切换及 8 次服务列表滑动，以 Android Window FrameMetrics 采样，排除 FIRST_DRAW_FRAME。对照期间没有同时运行 Gradle 等重负载任务。

环境：相同 API 36 模拟器、软件渲染/ARM 转译、Debug 构建。下表为 TOTAL_DURATION 的 P95，单位 ms。每份原始 JSON 同时记录帧数、P50、绘制/布局 P95 和长帧数。

| 场景 | 修改前第一轮 | 修改后第一轮 | 修改后复测 | 修改前复测 |
| --- | ---: | ---: | ---: | ---: |
| 三标签切换 P95 | 84.02 | 33.13 | 33.90 | 84.40 |
| 切换超过 100 ms 帧数 | 7 | 0 | 0 | 7 |
| 服务滚动 P95 | 39.59 | 40.44 | 40.70 | 40.35 |

复测调换了前后包的测量顺序。切页结果稳定改善，服务滚动没有测得明确提升，不能宣称真机已达到固定帧率或全部卡顿已消除。当前没有连接用户实体手机；仍需在其手机上用本次包检查首页、服务长列表、三个标签连续切换，以及发热/低电量条件。若滚动仍卡顿，应针对该机型使用 profile/release 构建和系统帧跟踪定位 GPU 合成或其他瓶颈。

中间方案只局部化重组、尚未保留标签页时，切页 P95 仍为 100.50 ms；随后增加页面复用才获得上述改善。失败的测量脚本尝试和中间结果也保留在输出目录，不作为最终性能结论。

## 验证入口

- `RefreshAccountDeviceTest`：增加主标签滚动状态保留、离屏个人子页不拦截返回；同时回归平台账号门禁、注册登录后继续、退出后的独立权限、首页无下拉、考试空卡片及课表刷新失败保留内容。
- `ServiceFlowDeviceTest`：适配服务 LazyColumn 滚动定位，回归八个学校服务的系统返回、原生返回、网页返回/关闭及中间页跳转。
- `ServiceRefreshDeviceTest`：验证连续自动进入复用近期结果，手动刷新仍执行。
- 所有测试使用隔离安装，原应用数据不清除。正常安装包使用原 Debug 签名覆盖升级。

隔离测试构建（PowerShell，已配置本项目 JDK/SDK 后）：

```powershell
.\gradlew.bat :app:assembleDebug :app:assembleDebugAndroidTest '-PcacheTestSuffix=.perftest' '-PdeviceTestBuildType=debug' --max-workers=1 --no-daemon --no-configuration-cache
adb install -r app/build/outputs/apk/debug/MyChuannong_debug_v0.1.0-preview.9.apk
adb install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb reverse tcp:8792 tcp:8792
adb shell am instrument -w -r -e class com.mychuannong.app.school.MainInteractionPerformanceDeviceTest -e performanceLabel sample com.mychuannong.app.perftest.test/androidx.test.runner.AndroidJUnitRunner
```

平台账号回归需运行已有本地预览后台。最终普通安装必须去掉两个测试属性重新构建，并确认 `output-metadata.json` 的 applicationId 为 `com.mychuannong.app.debug`，否则同名 APK 仍是隔离测试包。

最终构建、测试计数、签名和安装记录见工作区 `05_Output/performance-20261021/delivery.json`；此输出目录包含本轮原始测量 JSON、构建日志、合成数据截图及交付 APK，不提交真实账号/截图/数据库。

本次结果：356 项 JVM 测试及 18 项设备回归通过；Debug/Release lint 均 0 错误，仍分别有 1505/1504 个 Warning 和 3 个 Hint。交付 APK 已使用 `install -r` 覆盖安装到 `emulator-5554`，正常进入 MainActivity，已校验设备中 APK 与交付文件 SHA-256 一致，未清除原应用数据。隔离测试包已卸载。

参考：[Compose 性能最佳实践](https://developer.android.com/develop/ui/compose/performance/bestpractices)、[Android FrameMetrics 指标定义](https://developer.android.com/reference/android/view/FrameMetrics)。
