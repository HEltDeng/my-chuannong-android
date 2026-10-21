# 失物招领重做与接入 · 2026-10-16

用户先要求删除旧失物招领功能并提供简洁效果图，随后验收交互原型，并明确要求加入 APP。本次替换原 `LostFoundActivity` 的 Compose 页面为已确认的绿色单列版本。原 APP 的服务入口和兼容路由继续打开该 Activity；其他模块保持原有实现。

## 行为

- 默认招领列表，支持寻物/招领切换、多个关键词搜索、全部/成都/雅安/都江堰校区联动筛选。
- 列表展示照片、物品名称、校区地点和时间；详情显示照片、描述和联系方式面板，支持实际复制。
- 发布支持类型、照片、名称、校区、地点、日期、描述和联系方式。必填项、有效日期与图片格式/大小均检查。
- “我的”仅管理本机新增的发布；支持编辑、标记完成/重新开放、确认删除、短时撤销。完成的信息从普通列表隐藏，在“已完成”保留。
- 图片通过 Android 文档选择器获取，发布后与记录一同保存；重建页面仍可读取。
- Android 系统返回依次关闭面板、保护未保存的编辑、返回列表、退出到原服务页；页面自己的返回按钮行为一致。
- 使用实际 Android 状态栏、底部安全区域和输入法，未加入原型设备外框、假键盘或重复标题栏。

## 数据边界

当前为本机保存，未接入跨设备内容服务。表单明确说明“信息保存在本机，暂未向其他设备同步”。初始 7 条内容及联系人为示例，列表和详情明确标识。没有对外发布或发送联系消息。

数据库 `mychuannong-lost-found-v2` 独立于旧实现；旧 MMKV 草稿、旧模型与历史缓存未清除或静默迁移。保留仍被其他组件引用的旧数据层代码，旧失物招领页面、收藏等操作入口已从该功能移除。既有旧详情 ID 若在新版中不存在，显示可返回列表的缺失状态。

写入在 IndexedDB 事务提交完成后才反馈成功。损坏数据不会自动替换为示例；显示读取错误并允许重试。编辑、完成和删除均校验记录归属；用户输入由 React 作为文本显示。照片仅接受 PNG/JPEG/WebP，最大 10 MB。原生容器仅加载本功能离线资源，保持网络禁用、禁止文件访问和混合内容，不新增通用 JavaScript 桥。

## 开发入口与验证

- `web/lost-found/`：可维护的 React/TypeScript 源码、依赖锁文件和存储测试。
- `app/src/main/assets/lost-found/`：可随 APK 独立使用的资源与许可证。
- `LostFoundActivity.kt` / `LostFoundWebPolicy.kt`：容器、路径限制、图片选择、原生返回。
- `LostFoundGuestAccessDeviceTest.kt`：使用 `.lostfoundtest` 隔离包，覆盖两种入口、筛选、联系复制、系统选图、发布管理、重建、返回和缺失详情；仅使用合成资料。

```powershell
# web/lost-found 目录
npm ci
npm test
npm run build

# Android 根目录；设备测试与默认应用分开构建
.\gradlew.bat :app:assembleDebug :app:assembleDebugAndroidTest '-PcacheTestSuffix=.lostfoundtest' '-PdeviceTestBuildType=debug'
.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug :app:lintDebug :app:lintRelease --no-configuration-cache
```

普通 JVM 包名断言只在默认 Debug 构建执行。隔离包名会按预期触发该断言，不能作为默认包的失败结论。设备测试的剪贴板与选图使用真实触摸事件，以符合 Android 的用户手势要求。

本地验收截图、日志、QA 和 APK 位于工作区 `05_Output/lost-found-integrated-20261016/`。构建保持 `0.1.0-preview.9` 内部预览版本，不包含正式发布、Tag 或生产部署。

最终验收：8 项网页存储测试、267 项默认 Debug JVM 测试、6 项隔离设备回归和 1 项默认安装包只读检查通过；Debug/Release Lint 无错误。交付 APK 与模拟器实际安装包 SHA-256 一致。视觉验收见 `web/lost-found/design-qa.md`，完整底部软键盘与其他厂商真机仍需后续复核；本轮实际 IME 为 Gboard 浮动工具栏。
