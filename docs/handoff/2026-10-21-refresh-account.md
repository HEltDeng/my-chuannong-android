# 首页、课表刷新与平台账号访问边界

本次用户规则优先于历史文档中首页/课表下拉刷新和平台退出清除学校绑定的描述。Android 内部预览；未修改后端、发布或推送。

## 行为

- 首页移除下拉刷新容器与主动联网刷新方法，滑动仅滚动内容。启动、回前台和其他页面同步后的更新继续读取本地缓存。
- 课表移除下拉刷新容器；右上角蓝色“刷新”按钮保留，处理中只有按钮内的进度图标，按钮暂时禁用。原课表保持可见，成功沿用原同步逻辑更新内容与时间，失败保留原缓存和成功更新时间。成绩、考试等页面独立刷新逻辑不变。
- 资料库、二手好物、失物招领、驾校、雅思托福、考公考研、健身房使用同一平台账号检查。“校外园地”在现有设计中是分区标题，其四个子入口全部受保护，不新增中间页。
- 普通卡片、兼容路由和页面直接启动均检查 `AccountAccess`，只认可有效的我的川农 PHONE 账号，不将游客设备身份、学校绑定、VPN 或教务登录当作平台登录。
- `AccountRequiredActivity` 保留原目标 Intent 及模块/帖子参数，注册或登录成功后直接打开目标，取消返回原页面。保留重建恢复，清除可能重置任务栈的目标 flags，并防止重复跳转。
- `AccountProtectedActivity` 在创建、回到前台和可见期间账号变动时检查账号。已登录直接显示原页面；已退出时关闭受保护页并进入登录。WebView 未创建就被拦截的情况可安全销毁。
- 延续现有设备密钥加密 Token 和账号恢复逻辑，不因普通页面刷新清除平台会话。平台退出、切换和注销只处理平台会话；删除了同时清空学校身份和学习数据的耦合。学校主动解绑、学校认证恢复和学习数据按学号隔离继续沿用既有实现。

## 验证与边界

`RefreshAccountDeviceTest` 使用独立 `.accountflowtest` 包、合成学校身份和被拦截的学校请求，平台注册/登录连接本机真实开发 API（8792）；临时手机号与密码只用于测试，结束后删除测试账号。没有使用真实学校账号，也没有在生产代码中加入认证跳过开关。

七项设备用例覆盖：全部入口与直接启动的未登录拦截、取消返回；登录页重建后注册直达原模块、已登录各入口直达；退出后错误密码/正确密码及帖子参数恢复；切换/注销平台账号保留学校缓存；可见页面退出后重新拦截；首页上下滑动无请求和进度；课表下拉无请求、按钮点击请求、加载只在右上角、失败保留课程和时间。

- 356 项 JVM 测试通过，无失败或跳过。
- 最终源码的 7 项设备用例全部通过，包含本机实际注册与登录接口。
- Debug / Release Lint 均为 0 错误，分别保留 1506 / 1505 条警告与各 3 条提示。未关闭检查。整组任务构建成功；既有 `verifyReleaseEndpointSafety` 任务仍不能写入 Gradle 配置缓存，因此本轮配置缓存被丢弃，不影响 APK 或检查结果。
- 默认 Debug APK 构建成功，签名与原安装一致，已覆盖安装至 `emulator-5554` 并成功打开 MainActivity，原包没有卸载或清除数据。
- 已查看首页滑动后及课表刷新中的模拟器截图：首页没有刷新组件，课表内容与时间保持可见，进度只在右上角按钮内。截图为合成数据，只保存在仓库外，不随源码提交。

本机交付目录为工作区 `05_Output/refresh-account-20261021/`；APK 为 `MyChuannong_refresh_account_20261021.apk`。详细构建日志、校验值与本次文件哈希记录在该目录。

验证结束后已卸载本次 `.accountflowtest` 测试包及测试运行器，模拟器保留默认 `.debug` 的覆盖更新。

本地调试账号服务沿用既有配置：Debug 对应本机 `http://127.0.0.1:8792/api/v1/`，模拟器需 `adb reverse tcp:8792 tcp:8792`。正式版本仍需配置真实 HTTPS 账号服务。本次没有把本机服务当作生产部署。

历史 `LostFoundGuestAccessDeviceTest` 针对允许游客进入的旧规则，不能作为本次行为的验收依据；对应入口边界由本次设备用例覆盖。

## 复测

```powershell
.\gradlew.bat :app:assembleDebug :app:assembleDebugAndroidTest '-PcacheTestSuffix=.accountflowtest' '-PdeviceTestBuildType=debug' --max-workers=1 --no-daemon
adb reverse tcp:8792 tcp:8792
adb shell am instrument -w -e class com.mychuannong.app.school.RefreshAccountDeviceTest com.mychuannong.app.accountflowtest.test/androidx.test.runner.AndroidJUnitRunner
.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug :app:lintDebug :app:lintRelease --max-workers=1 --no-daemon
```

隔离构建后须重新生成默认 Debug APK，再以 `adb install -r` 覆盖原预览安装，保留原数据。不要卸载或清除用户原包数据。
