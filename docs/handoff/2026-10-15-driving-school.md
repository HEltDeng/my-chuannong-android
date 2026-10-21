# 驾校页面接入与淡蓝色主题

2026-10-15，用户确认独立原型后授权接入现有 APP，并将驾校页面主题改为淡蓝色。基于本机当前工作区开发，保留已有其他功能与改动。

## 入口与页面

入口为「服务 → 校外园地 → 驾校」。沿用已有 `driving_school` ID、名称、图标、分区和顺序，将原来的「即将上线」目标改为 `FunctionDestination.DrivingSchool`。旧配置缺少入口时沿用现有补齐机制，显式隐藏配置仍生效。驾校入口优先打开本地页面。

`DrivingSchoolActivity` 使用 `WebViewAssetLoader` 读取本包 `assets/driving-school`。图片、页面脚本和样式均在 APK 内，无需运行电脑上的预览服务器。地址固定为 `https://appassets.androidplatform.net/assets/driving-school/index.html?host=android`。

两组驾校共用同一套 HTML/CSS/JS 组件和交互，模拟内容集中在 `data.js`。A/B 均使用淡蓝色页面背景、按钮、选中态、福利、预约弹窗及位置示意图。独立浏览器预览仍提供手机尺寸切换。

## APP 适配

- `host=android` 隐藏桌面外壳及网页主标题栏，使用 APP 原生顶部返回和关闭按钮，避免双标题。
- WebView 使用 `MATCH_PARENT` 与 Compose 剩余空间约束，布局完成后才载入本地页；嵌入模式使用明确的百分比高度，避免网页根容器高度为 0 导致空白。关闭此页的缓存读取，保证 APK 更新后使用当前打包资源。
- 网页 `window.CampusDriving.back()` 仅处理当前弹窗的返回；Android 返回键与顶部返回先关闭弹窗，规则页先回福利页，主页返回直接关闭 Activity 回到服务页。
- 页面不是依赖 localhost 的线上网页，不暴露原生 JavaScript 接口、不接入真实预约或优惠券。
- WebView 只允许当前驾校文档及六个本地资源路径，阻止远程请求、其他 APP 资产、文件访问和内容提供者访问；DOM 存储关闭。
- 所有商家、位置、价格、福利和时段均为模拟内容。领取状态与提交结果只保存在页面内存中；不采集姓名、手机号等联系方式。

## 维护

APP 内维护以 `app/src/main/assets/driving-school` 为准，修改该目录的 `data.js` 可替换两组内容，不需要分别开发页面。图片来源为本次原型的 AI 模拟场景；用户界面标注了 AI 场景示意，原始提示词见原型素材说明。

图片及完整生成提示词随仓库保存，见 [场景图说明](2026-10-15-driving-school-images.md)。

可用仓库自带工具从实际 APP 资产导出独立单文件预览，不依赖旧电脑输出目录：

```text
node tools/export_driving_school_preview.cjs C:/your-output/driving-school-preview.html
```

构建、JVM 与 Lint：

```text
gradlew.bat :app:testDebugUnitTest :app:assembleDebug :app:lintDebug :app:lintRelease --no-configuration-cache
```

隔离设备测试使用 `.drivingschooltest` 后缀，避免覆盖现有 APP 数据。构建设备包后需按项目规范重新运行默认 `:app:assembleDebug`，不要将隔离包当成交付包。

```text
gradlew.bat :app:assembleDebug :app:assembleDebugAndroidTest -PcacheTestSuffix=.drivingschooltest -PdeviceTestBuildType=debug --no-configuration-cache
```

设备测试类为 `com.mychuannong.app.feature.common.ui.DrivingSchoolIntegrationDeviceTest`，验证本包资源、淡蓝主题、组合筛选、空状态、A/B 切换、福利、模拟预约和原生返回。设备资料全部为内置模拟内容。

本次只做本地 APP 接入与内部 Debug 构建，没有创建正式 Release、Tag 或进行生产部署。

最终验证：默认 Debug 构建成功；260 项 JVM 测试通过；Debug / Release Lint 为 0 error（1491 / 1490 条 warning）；隔离模拟器中完整驾校流程通过，并确认 A/B 图片及页面实际绘制。截图与 APK 保存在本次本机交付目录，未提交到源码仓库。四种手机浏览器视口检查均无横向溢出。
