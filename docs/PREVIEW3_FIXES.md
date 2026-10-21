# 我的川农 0.1.0-preview.3 / 3 定点修复

2026-10-02，内部预览，非正式发布。继续 `codex/restore-v105-adaptation`，以前一版 Android `fec01d669bbc6027cc80b8a2809f2ec3a03e6db9` 为修改起点；没有重置、重建仓库或替换整套产品。Backend `a6001495ef64a6d95db35980a86c92b342d53743` 不变。

## 母版与源码映射

只读核验当前长理正式源码目录 `D:/Codex/01_App-应用开发/001_MyChangLi-我的长理/01_Source-当前源码/01_Android`。当前 main HEAD 为 `fd8937626a8fa0096e8534bea3103f93586d9fe6`，包含已验收归档提交 `d56fc5efc8f1584309a7bc348035780e2c816c27`。旧 worktree 路径已经迁移；没有按旧目录名猜母版。下表路径均相对于 app/src/main；母版包 com/mychangli/app 对应川农包 com/mychuannong/app。

| 母版实现 | 川农文件 | 本轮修改 |
|---|---|---|
| feature/timetable/ui/AcademicTermDisplayFormatter.kt | 同名文件 | 保留大一至大四、上/下算法；参数改为有明确来源的入学年份，移除从学号前四位推断 |
| feature/timetable/ui/TimeTableActivity.kt，showWheelDialog/setItemLabelProvider | 同名文件 | 当前标题与列表共用格式化器；选中值、查询参数仍为原始学期 ID；已知入学年份用于生成母版四学年选项 |
| feature/timetable/ui/compose/TimeTableComposeScreen.kt | 同名文件 | 将 rawTerm 与显示 termText 分开；校历查询仍按 rawTerm；长标签限制宽度，避免挤压周次状态 |
| feature/common/ui/adapter/vh/SemesterViewHolder.kt | 同名文件 | 成绩学期分组显示共用规则，原分组键不变 |
| settings/ui/BindingUserActivity.kt 与 res/layout/activity_binding_user.xml | 同名文件 | 恢复在用的原生输入布局、提示切换、非空校验、密码可见切换、键盘处理和加载容器 |
| common/redux/store/UserStore.kt 原学校登录任务 | settings/ui/NativeBindingViewModel.kt、school/SicauNativeAuth.kt、SicauAuthProtocol.kt | 不复制长理认证参数；原生提交连接学校公开机制和川农现有本机同步引擎 |
| 母版本地账号管理 | common/data/local/mmkv/StudentInfoManager.kt、school/SchoolSyncEngine.kt | 仅学校返回身份匹配后关联；换号清理旧学习缓存、入学年份；平台账号保持独立 |

## 学期与入学年份

复用母版显示映射，不把“大二上”等字符串用作请求、Room 查询、缓存键或校历索引。2025 入学的 2026-2027-1 显示大二上；2026 入学同一学期显示大一上。上下学期、四个年级均有单元测试。

川农学号规则及学校入学年份字段未核实，不能照搬长理学号前四位。原生表单增加**可选**入学年份，不影响正常认证；仅在学校身份确认后保存为该账号的本机资料，不声称它是学校认证的年份。换号、解绑会清除；同一账号重新登录时留空不会抹掉已保存年份；会话过期保留本机年份供缓存展示。尚无年份时显示“2026-2027学年·上学期”。非标准/夏季学期不猜，保留原值；超出母版四年范围显示学年。

课表重新进入仍沿用原有当前学期选择规则。本轮未改变教学周、校历和节次算法。

## 原生认证的证据和实现范围

公开依据：https://auth.sicau.edu.cn/wui/ 及其实际加载的 https://auth.sicau.edu.cn/wui/assets/index-DwxyaMpm.js 。2026-10-02 本地核验，无真实账号输入。

- 手机直连 auth.sicau.edu.cn HTTPS，独立 OkHttp 客户端，不使用平台账号 API、网络日志拦截器或后台代理。
- 按学校前端初始化 `/wui/`，获取 `/api/v1/captcha` 的学校实际图形验证码。
- 学校公开密码认证接口 `/api/v1/cas/login`，JSON 字段 `appid/number/password/code/uuid`，appid 为 `sicau-cas`。
- 按公开前端规则解码加密响应：requestId 与 appid 派生 AES 密钥，响应前 16 字节为 IV。实际公开验证码响应已解码并在模拟器显示；不保存验证码或会话到报告。
- 用户完成验证码后才 POST 凭据；学校接受响应还需 st，继而 GET 学校会话接口独立复核。仅在学校返回 users 中的账号与输入一致、且匹配所属用户时才确认身份；多身份不明时保留待确认。
- 认证、身份确认、业务解析分别处理。确认身份后调用现有 SchoolSyncEngine，课程/成绩/考试仍各自返回明确的解析待适配，不能用空数组或成功状态替代。
- 学校密码仅内存使用，不放 SavedStateHandle、Intent、日志、文件或自有后台；输入期间防截屏。认证 Cookie 使用 Android Keystore AES-GCM 加密，保存于 noBackupFilesDir，解绑删除。本机平台账号不受学校解绑影响。

**已经实现且待真实账号验证**：原生提交、学校初始化/验证码、正常密码认证请求、响应解码、会话复核、匹配身份后接入本机同步。模拟器只执行公开初始化 GET，未提交学校账号密码，不把它标成真实登录 PASS。

**确实未实现/未核验**：统一认证到教务系统的实际业务回跳与教务会话、教务身份及课程/成绩/考试页面解析。不能因为统一认证实现就宣称教务同步完成。未遇到的短信/扫码或额外验证流程也不声称已支持；会给出未完成认证提示，不自动跳整页网页或绕过验证。

## 验证边界

- 定点单元测试 9 项通过：母版学期映射与未知/切换年份、保留原 ID；认证响应解码、严格身份判断、学校字段与安全错误文案。全部为合成夹具，不代表真人认证。
- 全量运行 132 项、23 项失败。失败集中于遗留长理包路径/品牌/CSUST AAR 的源码断言和旧校历/课时假设；不为让测试变绿而恢复长理数据或删除测试。详情保留本地 build-test.log。相关 CommonInfo、CourseScheduleEngine 和这些旧测试未修改。
- 模拟器覆盖安装 preview.2 成功，无卸载、无清数据；长理仍共存。实机未测。
- 原生入口空表单、必填禁用、输入、学校验证码实际显示、取消返回已操作验证；未执行学校凭据 POST、真实身份确认或业务同步。
- 学期选择与标题截图使用真实未绑定状态，所以展示准确学年，不预植虚假绑定来截图“大几”。已知年份映射由测试验证。
- 悬浮导航/滑动、首页/服务/个人中心代码未重做；后台、生产配置未修改。模拟器历史缓存中可能保留上一版 preview.2 的本地演示公告，该公告不是本轮认证结果。

## 归档修复

原 `.gitignore` 的非根限定 `data/` 错误忽略了源码包。改为 `/data/`，补入本机已有的必要 Kotlin/Java 数据模型、DAO、Repository 等源码，并审查敏感内容。这不是迁入生产数据库或新增业务；解决远端还原缺失源码的问题。

签名沿用川农专属 JKS，证书 SHA-256：`A8F8EAC6FF810CAC0B21CBD59A08D5525E1ACA9C225F86F516FBD1172BECDF6D`。APK、截图、构建日志、原始公开网页和本机凭据均不提交本仓库。不创建 Tag、Release 或公开下载。
