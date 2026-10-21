# 我的川农账号密码输入光标修复

## 原因与修改

登录、注册共用 `ImeCompatibleTextField`。旧版每次文字状态更新都调用 `setSingleLine`，再重新设置密码转换器，导致原生编辑缓冲区的光标回到开头。原有直接 `setText` 填表测试没有覆盖逐字符输入，因此没有发现输入倒序。

本次仅修改这个共用输入组件：按原生 View 记录单行配置，只在配置变化时设置；密码转换器正确时不重复设置；配置或文字确需同步时保留光标与选区，并按校验后的长度限制有效位置。普通输入光标停在末尾，手动移动光标与选区替换仍采用原生行为。输入法组合文本也不会因普通重绘被清除。

同时观察原生文字变化，即使上层拒绝超长字符、返回的值与上次相同，也会将原生缓冲区同步回合法值，避免输入框显示与实际提交值不一致。此状态只在内存中使用，没有新增密码日志、缓存或持久化。

默认密文显示、密码长度规则、账号请求与页面布局保持不变。共享组件切换密码/普通文本配置时的光标与选区已加入测试，未新增明文显示按钮。保留 API 24 兼容性，不使用 API 29 才提供的 `isSingleLine` getter。

相关原生 API：[Android TextView 官方文档](https://developer.android.com/reference/android/widget/TextView)。

## 验证

新增 `PasswordCursorDeviceTest`，使用 `.cursorfix` 隔离包，通过原生 `InputConnection` 逐字符输入，并使用原生剪贴板粘贴动作（测试后恢复剪贴板）。测试只输入合成字符串，认证提交使用测试回调捕获，无真实账号、认证会话或网络登录。

- 修复前四项用例全部失败；登录、注册输入第一个字符后光标期望在 1，实际为 0，复现用户问题。
- 最终修复的四项设备用例全部通过，日志为 `final-device-tests-3.log`。
- 两个表单分别验证 `123456` 的实际顺序、密码不足八位时不能提交、删除、粘贴、选区替换、中间连续编辑，以及最后提交的原始密码不变。
- 验证无关提示文字更新、切换密码显示配置、选区保持、输入法组合文本替换。
- 验证 201 字符粘贴后限制为 200、达到上限后继续输入仍受限、清空后可正常重新输入。

本机产物与验证记录位于工作区 `05_Output/password-cursor-20261021/`。没有修改学校认证、账号后台或其他页面流程。

356 项 JVM 测试通过。默认 Debug APK 已生成并通过原签名覆盖安装到 `emulator-5554`，启动成功，保留原账号和数据；本次隔离测试包已移除。交付文件为 `MyChuannong_password_cursor_20261021.apk`，校验值与本次源码文件哈希见同目录 `delivery-manifest.json`。

Debug / Release Lint 均为 0 错误，分别保留 1506 / 1505 条既有警告与各 3 条提示；未关闭检查。整组构建任务成功。

复测命令：

```powershell
.\gradlew.bat :app:assembleDebug :app:assembleDebugAndroidTest '-PcacheTestSuffix=.cursorfix' '-PdeviceTestBuildType=debug' --max-workers=1 --no-daemon --no-configuration-cache
adb shell am instrument -w -e class com.mychuannong.app.core.account.PasswordCursorDeviceTest com.mychuannong.app.cursorfix.test/androidx.test.runner.AndroidJUnitRunner
.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug :app:lintDebug :app:lintRelease --max-workers=1 --no-daemon --no-configuration-cache
```

生成默认 Debug 后再覆盖安装用户原包，不卸载、不清除原包数据。
