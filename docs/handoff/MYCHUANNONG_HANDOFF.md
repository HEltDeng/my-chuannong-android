# 我的川农 Windows 开发交接与校园网验证

核查日期：2026-10-09（Asia/Singapore）。适用产品：MyChuannong / 我的川农，内部预览。

本文件为当前权威交接入口，随 Android 仓库同步。后续关键修复和真实测试须更新本文件。历史报告中的“当前”“待适配”和版本号只代表其记录日期；与本文件不一致时，以本文件所列源码核查及证据为准。

## A. 接手即读

目标：运行现有 Android 产品，以同一授权学校账号和同一学期，对照官方教务页面、正式农屿 App，验证学校登录、本人身份、课表/成绩/考试请求、解析、本机缓存和原生页面。当前最先需要补齐的是**校园网 Android 原生真实账号端到端证据**。

- Android 分支：`codex/restore-v105-adaptation`。
- 已保存的业务源码基线：`8e842db648258cb15c70538ce5a85f1e2db0ef19`。
- 原远端旧基线：`ebff336ca650a1c2b4733774de485835d9dbd9e8`。只拿到旧基线会缺少 10 月 8 日的数据解析及缓存修复。
- 包内版本仍为 `0.1.0-preview.8 / 8`，不能只凭版本号识别本次修复包；按提交与 APK SHA-256 核对。
- 课程、成绩、考试三个 SicauReader 入口已从占位改为实际请求和 Kotlin/Jsoup 解析；独立教务密码输入、Room 11→12 迁移、同账号同学期学校课程替换、失败保留缓存均已实现。
- 2026-10-09 强制重跑 JVM 测试：189 项通过，0 失败/错误/跳过。Debug 构建检查通过。2026-10-08 的 10 项模拟器仪器测试通过；它们使用合成数据。
- 官方 WebVPN 浏览器曾看到本人信息、15 条课表明细、25 条成绩及当期空考试页；这是 10 月 8 日记录，**不是本次重新登录，也不是原生真实同步通过**。
- 原生模拟器真实学校账号同步：未验证 `PENDING_REAL_ACCOUNT`；真机：未验证 `PENDING_REAL_DEVICE`。
- **产品路线仍优先沿用官方 WebVPN；实际原生教务客户端目前直连 jiaowu.sicau.edu.cn，尚无原生 WebVPN 资源代理适配。**校园网直连用于先验证现有业务链路，不代表改变产品路线。
- 新电脑第一步：完整阅读本文件，找到已有工程或取得本节分支，保护本地修改，确认源码包含上述基线，再恢复 Debug 构建。随后分别确认宿主机和模拟器网络，不要先改 UI。
- 保留完整原生首页、课表、个人中心、学校服务、绿白主题及图标分色、原生校历、提醒、小组件和手动/已确认导入课程。个人教务资料只在设备读取和缓存，不上传产品后端。

## B. 仓库与环境恢复

### B1. 当前工程归属及版本

原电脑项目根目录：`D:\Codex\01_App-应用开发\006_MyChuannong-我的川农`。

| 用途 | 当前实际目录 | 远端、分支、源码基线 | 本轮用途 |
|---|---|---|---|
| Android 正式工作副本 | 项目根目录下 `01_Repositories\my-chuannong-android` | `https://github.com/HEltDeng/my-chuannong-android.git`；`codex/restore-v105-adaptation`；`8e842db648258cb15c70538ce5a85f1e2db0ef19` | 必需；本文件位于 `docs/handoff/MYCHUANNONG_HANDOFF.md` |
| Backend/Admin | 项目根目录下 `01_Repositories\my-chuannong-backend` | `https://github.com/HEltDeng/my-chuannong-backend.git`；同名分支；`a6001495ef64a6d95db35980a86c92b342d53743` | 学校真实数据验证不需要启动；仅产品账号、后台和自有内容联调需要 |
| 农屿公开参考源码 | 项目根目录下 `06_Reference\nongyu-system` | `https://github.com/nongzhuan555/nongyu-system`；固定提交 `7265f694bfcfa7aa05f93e9ccafa42ed8142fb40` | 只读参考，不是 Android 构建依赖；工作区干净 |
| 最小协议验证器 | 项目根目录下 `06_Reference\sicau-jiaowu-harness` | 本地独立诊断工程 | 非完整农屿 App；本轮不要求迁移或重新构建 |
| 聊天启动目录 | `C:\Users\HeVoi\Documents\ChatGPT\我的川农` | 核查只见 `.git`，无 Android 工程 | 不是当前源码入口 |

两个产品仓库均只有各自一个工作树；Android 无子模块、无 Git LFS 属性/指针依赖，无必需的本地 AAR/JAR 或仓库外 Maven。`gradle/wrapper/gradle-wrapper.jar` 已跟踪，`CP_Common`、`baselineprofile` 均在同一仓库。settings 中虽保留 `flatDir(app/libs)`，实际不存在需要搬运的本地库，旧 CSUST AAR 引用已注释。

本轮开始 Android 有 23 个已跟踪修改、6 个未跟踪文件、暂存区为空。它们构成 10 月 8 日修复的 29 文件集合，逐一与旧交付源码哈希一致，已经全部保存为上述业务源码提交；没有 reset、clean、覆盖、复制项目或建立 worktree。新增文件为 Room schema 12、SicauBusinessParser、两份 JVM 测试、一份存储仪器测试和合成 HTML fixture。

实际 fetch 与 ls-remote 核查：开始时 Android 已提交 HEAD 与远端均为 `ebff336...`，ahead/behind=0/0，但未提交修复尚未同步；Backend 本地及该分支远端均为 `a600149...`，工作区干净、ahead/behind=0/0。Backend 默认分支 HEAD 是另一个旧提交，恢复时必须明确分支。交接文档在业务源码提交之后另行提交；最终文档提交与远端核验见随交接提供的同步结果。文档不反复写入自身提交号。

被忽略内容仅需区分：`local.properties` 是本机 SDK 路径；`.gradle/.kotlin/**/build` 是可再生成缓存/产物。学校会话与数据库位于设备沙箱，不是可跨机复用的开发配置。项目父目录 `04_Docs`、`05_Output`、`tools` 不随 Android Git 自动取得，必须依本文件恢复，不依赖那些绝对路径脚本。历史源码和参考材料保留原处。

### B2. 已核实环境

| 项目 | 原机实际配置 / 接收端要求 |
|---|---|
| 系统、Shell | Windows 11 amd64、PowerShell；命令使用仓库 Wrapper |
| JDK | Eclipse Adoptium 17.0.19+10；`D:\Codex\90_Shared-公共资源\toolchains\jdk-17\jdk-17.0.19+10`；目标机可用自己的 JDK 17 安装路径 |
| SDK | `D:\Codex\90_Shared-公共资源\toolchains\android-sdk`；compileSdk 36、compileSdkMinor 1，即 `platforms;android-36.1`；targetSdk 36、minSdk 24 |
| Build Tools | Gradle 实际解析为 `36.0.0`；原机还装有 35.0.0/36.1.0，不能把“已安装最新版”误当本构建使用值 |
| Gradle / AGP | Wrapper `9.4.1`；Android Gradle Plugin `9.2.0`；Wrapper 校验和在 properties 内 |
| Kotlin / KSP | 版本目录 Kotlin/Compose 插件 `2.3.21`，KSP `2.3.7`；Gradle 自带 Kotlin 显示 2.3.0 属于 Gradle 运行环境 |
| 模拟器 | 历史 AVD `MyChuannongPreview`，序列号 `emulator-5580`；Android 16 / API 36、Google APIs、x86_64，系统镜像 revision 7；Emulator 37.1.11 |
| 支持 ABI | `arm64-v8a` 和 `x86_64`；推荐目标 Windows 使用 API 36 Google APIs x86_64 AVD，开启硬件虚拟化 |
| 原机 AVD 位置 | `05_Output\avd`；镜像/Emulator 在 `05_Output\emulator-sdk`，是本地测试工具，不整体打包 |
| 本次设备状态 | 10 月 9 日 `adb devices` 无连接设备；本次不重复启动历史 UI 验收，不能标注新增模拟器或真机通过 |

### B3. 获取同一源码

先检查目标电脑已有 `D:\Codex` 项目。已有项目先运行下列只读检查，发现多个疑似副本时先确认归属，不自行选一个覆盖：

```powershell
git rev-parse --show-toplevel
git status --short
git branch -vv
git rev-parse HEAD
git remote -v
git worktree list
```

只有目标机没有该工程时，使用下面的首次恢复命令。项目路径可以换成目标机正式项目目录，无需照搬原盘符；若存在相同项目必须复用。

```powershell
$projectRoot = 'D:\Codex\01_App-应用开发\006_MyChuannong-我的川农'
$repo = Join-Path $projectRoot '01_Repositories\my-chuannong-android'
if (Test-Path -LiteralPath $repo) { throw '已有目录：先核对 Git 和本地修改，不重复克隆。' }
New-Item -ItemType Directory -Path (Split-Path $repo) -Force | Out-Null
git clone --branch codex/restore-v105-adaptation --single-branch https://github.com/HEltDeng/my-chuannong-android.git $repo
if ($LASTEXITCODE -ne 0) { throw 'GitHub 拉取失败，先恢复该仓库访问权限。' }
Set-Location -LiteralPath $repo
git rev-parse HEAD
git status --short
git merge-base --is-ancestor 8e842db648258cb15c70538ce5a85f1e2db0ef19 HEAD
if ($LASTEXITCODE -ne 0) { throw '缺少本次业务源码基线，不继续构建。' }
Get-Content -LiteralPath '.\docs\handoff\MYCHUANNONG_HANDOFF.md' -Raw
```

已有干净工程先 `git fetch origin`，确认当前就是该分支、无本地未推送提交或分叉后再 `git pull --ff-only origin codex/restore-v105-adaptation`。有本地改动或分叉则记录差异并保护，不执行 reset、clean 或强推。收到的最终文档提交应与首次克隆 HEAD 对照；如果远端后来又有新业务提交，记录该差异，不再把本文件 APK 当成后续新代码产物。

### B4. SDK 配置、依赖恢复与构建

以下在 Android 仓库根目录执行。保留目标机原 `local.properties` 中已有配置，不复制原机绝对路径。只在文件不存在时生成 SDK 路径；已存在时人工/脚本只修正 `sdk.dir`，不覆盖其他字段。

```powershell
if (-not $env:JAVA_HOME) { $env:JAVA_HOME = Read-Host '输入本机 JDK 17 根目录' }
if (-not $env:ANDROID_HOME) { $env:ANDROID_HOME = Read-Host '输入本机 Android SDK 根目录' }
if (!(Test-Path "$env:JAVA_HOME\bin\java.exe")) { throw 'JDK 路径无效' }
if (!(Test-Path -LiteralPath $env:ANDROID_HOME)) { throw 'SDK 路径无效' }
$env:ANDROID_SDK_ROOT = $env:ANDROID_HOME
$env:Path = "$env:JAVA_HOME\bin;$env:ANDROID_HOME\platform-tools;$env:Path"
if (!(Test-Path -LiteralPath 'local.properties')) {
    $sdkValue = $env:ANDROID_HOME.Replace('\','/').Replace(':','\:')
    $sdkLine = 'sdk.dir=' + $sdkValue
    # Java properties 中非 ASCII 路径使用 Unicode 转义，避免系统代码页差异。
    $sdkLine = -join ($sdkLine.ToCharArray() | ForEach-Object {
        if ([int]$_ -gt 127) { '\u{0:x4}' -f [int]$_ } else { [string]$_ }
    })
    Set-Content -LiteralPath local.properties -Value $sdkLine -Encoding ASCII
}
& "$env:JAVA_HOME\bin\java.exe" -version
.\gradlew.bat --version
.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug --no-configuration-cache --console=plain
if ($LASTEXITCODE -ne 0) { throw '构建或测试失败：先保留日志并定位。' }
Get-FileHash '.\app\build\outputs\apk\debug\MyChuannong_debug_v0.1.0-preview.8.apk' -Algorithm SHA256
```

尚缺 SDK 组件时，使用 Android Studio SDK Manager 或 SDK 自带命令行工具安装以下**具体组件**。仅缺少者需要安装，SDK Manager 的许可证须按其正常流程接受：

```powershell
& "$env:ANDROID_HOME\cmdline-tools\latest\bin\sdkmanager.bat" 'platform-tools' 'platforms;android-36.1' 'build-tools;36.0.0' 'emulator' 'system-images;android-36;google_apis;x86_64'
```

使用现有 AVD 优先；没有 AVD 时可在 Android Studio Device Manager 创建 API 36 Google APIs x86_64 设备，或运行：

```powershell
& "$env:ANDROID_HOME\emulator\emulator.exe" -list-avds
# 仅无此 AVD 时创建，不覆盖现有设备数据；不加 --force。
'no' | & "$env:ANDROID_HOME\cmdline-tools\latest\bin\avdmanager.bat" create avd -n MyChuannongCampus -k 'system-images;android-36;google_apis;x86_64'
Start-Process -FilePath "$env:ANDROID_HOME\emulator\emulator.exe" -ArgumentList '-avd MyChuannongCampus' -WindowStyle Hidden
```

Debug 无需 `MY_CHUANNONG_SIGNING_CONFIG`，使用目标机自己的调试签名。Release 配置键为 `storeFile/storePassword/keyAlias/keyPassword`；原机受限位置 `D:\Codex-Local-Secrets\MyChuannong\signing.properties`，通过 `MY_CHUANNONG_SIGNING_CONFIG` 指定。它和 JKS 不进入 Git 或补充包。Release 只在确需同签名验证且已安全配置时构建，不是本次 Debug 调试前置条件。不得借用我的长理签名或新建川农正式签名身份。

所有版本锁定以 `gradle/libs.versions.toml`、Wrapper 和 Gradle 配置为准。首次依赖需访问 Google Maven、Maven Central、Gradle distribution、Gradle plugin portal、JitPack（settings 还包含镜像源）。校园网可能无法下载时，在进入校园前于**目标机**先完成上述构建并下载系统镜像，再用同一任务加 `--offline` 验证缓存可用。不默认搬走整个 Gradle/Android/Codex 缓存。Python、Node、农屿 RN 依赖不是本轮 Android 构建必需项。

Windows 中文路径的 JVM 测试资源 staging 已在 app/build.gradle.kts 中实现，包含 `src/test/resources`；不要删除它。既有 Release endpoint gate 有 configuration-cache 警告，交接命令使用 `--no-configuration-cache`。

### B5. Backend 与配置边界

本轮学校认证、教务请求、校历、Room 和原生课表无需 Backend。产品账号与学校绑定分别管理，不要为学校登录失败先搭后端。

仅当额外验证平台账号/后台时，才恢复 Backend 同名分支及上述 SHA，按其 README、requirements.txt 创建自己的 Python 虚拟环境，设置仓库外 `MY_CHUANNONG_LOCAL_CREDENTIALS` 后运行 `run_local.py` / `start-local.ps1`。原机默认私密配置位于 `D:\Codex-Local-Secrets\MyChuannong\backend-local.json`。不搬数据库、上传目录或原账号库。

Android 默认产品服务地址为 `http://127.0.0.1:8792/api/v1/`，平台功能联调可用 `adb -s $serial reverse tcp:8792 tcp:8792`。另一种方式是 Debug 的 `campusTools.apiBaseUrl.debug=http://10.0.2.2:8792/api/v1/`（local.properties）或环境变量 `MY_CHUANNONG_CAMPUS_TOOLS_API_BASE_URL_DEBUG` 后重建。二者任选其一，学校数据请求不经过该地址。其他 `community.*` / `tools.*` 默认为禁用地址，学校验证无需配置。`-PsicauAcademicUrl` 是公开校历测试覆盖地址，本轮保持未设置，不指向个人数据服务。

### B6. APK 身份与可追溯性

最新可用产物仍是 2026-10-08 数据链路修复验收包；不是正式发布。10 月 9 日 Debug 构建检查复用 UP-TO-DATE 产物，哈希完全一致，没有把它重新标成新编译包。

| 字段 | Debug（现场推荐） | Release（历史同签名验收） |
|---|---|---|
| 文件名 | `MyChuannong-jiaowu-20261008-debug-acceptance.apk` | `MyChuannong-jiaowu-20261008-release-acceptance.apk` |
| 原机位置 | 项目根目录 `05_Output/jiaowu-data-pipeline-20261008/` | 同左 |
| applicationId | `com.mychuannong.app.debug` | `com.mychuannong.app` |
| versionName / code | `0.1.0-preview.8 / 8` | 同左 |
| 大小 | 62,693,571 bytes | 32,010,360 bytes |
| SHA-256 | `3186dc4078b711ae8f61b0ee6357e39a00abf427b0759d700bc311d3d7391d80` | `7ec584abc885de032ff56efd73c63347ddfed8bfbb9f74d682de18500b42eadf` |
| 证书 SHA-256 | `b426a9e39ba688e3b08f7b4191ada7214a64eeddfb7e3ec5f637b26c8c6d68d9` | `a8f8eac6ff810cac0b21cbd59a08d5525e1aca9c225f86f516fbd1172becdf6d` |
| 产物文件时间（+08） | 2026-10-08 09:25:18 | 2026-10-08 09:26:04 |
| 验证 | 历史构建、模拟器安装与启动通过；真实账号未验证 | 历史构建、签名、模拟器安装与启动通过；真实账号未验证 |

时间来自实际文件 mtime；历史构建日志记录成功但未记录逐个 APK 编译完成的精确墙钟时间，不能把 mtime 当作独立签名时间证明。

源码对应证明：构建时为 `ebff336...` 加未提交修复；交付保留的 29 个源码 SHA-256 本轮全部匹配，29 文件集合与本轮开始 Git 差异完全一致；这批内容原样提交为 `8e842db648258cb15c70538ce5a85f1e2db0ef19`。Debug 当前构建输出与历史验收包也逐字节同哈希。因此新基线保存了该验收包的源码状态；不能称 APK 内嵌了新提交号或是对新提交重新全量编译。后续文档提交不改变业务源码。目标机重建因调试签名等差异，APK 哈希可能不同，应另记本机产物证据。

## C. 开发进度与证据

下表源码路径除 assets/schema/docs 外，均相对 `app/src/main/java/com/mychuannong/app/`。状态按验证范围标注，不把代码实现等同真实业务通过。

| 模块 | 已完成内容、关键源码 | 验证结果 | 问题及下一步 |
|---|---|---|---|
| 统一认证 | `school/SicauNativeAuth.kt`、`SicauAuthProtocol.kt`：学校 SPA challenge/login，验证码单次提交，响应解码 | 自动化样例通过；历史探针与 WebVPN 浏览器认证通过；原生真实账号未验证 | 在 App 实际完成统一认证；验证码由授权用户正常处理 |
| 官方 WebVPN | 浏览器通过官方资源进入教务；学校网页容器保留 | 浏览器历史通过；原生代理适配未实施 | 直连验证后单独处理官方资源代理与会话衔接，不能把认证 Cookie 当代理配置 |
| 教务独立登录 | `settings/ui/NativeBindingViewModel.kt`、`BindingUserActivity.kt`、`school/SicauJiaowuSession.kt`：动态表单、学生身份、可选独立密码 | 合成会话通过；浏览器历史通过；Android 真实未验证 | 首先确认 GET 登录页、ASP、动态字段、POST、本人页分别到达何处 |
| 本人身份 | `SicauJiaowuProtocol.personalInfo/verifyIdentity`、`SicauJiaowuSession.fetchPersonalInfo`；学校返回学号严格匹配输入 | 样例通过；浏览器历史 MATCH；原生未验证 | 页面结构失败与身份不符分开，禁止用输入值代替学校证据 |
| 课表 | `school/SchoolSyncEngine.kt` 的 `SicauReader.courses`、`SicauBusinessParser.courses`：请求、按表头解析、多时段/教室/单双周、学期验证 | 合成现场结构15记录→25时段通过；原生真实读取未验证 | 校园网逐字段对照并保存；历史学期接口未证明，不能把本期写入其他学期 |
| 成绩 | `SicauBusinessParser.grades`、`school/education/data/remote/EducationHelper.kt`、`feature/common/data/local/mmkv/ScoreCache.kt` | 样例通过；原生真实未验证 | 保留文字成绩/未知学分绩点；成绩明细接口仍明确未验证；总 GPA 口径待确认 |
| 考试 | `SicauBusinessParser.exams`、`school/education/data/remote/services/ExamArrangeService.kt`、`ExamArrangementCache.kt` | 合成日期/空表通过；历史浏览器当期为空；真实有记录未验证 | 当前服务未使用 term 参数过滤，页面学期归属需现场确认；未知日期保留文本、时间字段可空 |
| 缓存与原生界面 | `SchoolSyncEngine.saveSchoolRows`、Room `CoursesDataBase` v12 / `CourseDao`；`feature/schedule/CourseScheduleRepository`、`feature/timetable/viewmodel/TimeTableViewModel`、`overview/data/OverviewRepository` | 模拟器合成 HTML→Room→ViewModel/共用数据源、迁移、回滚通过；真实页面未验证 | 本地 SCHOOL 行按账号学期替换，旧行默认 LOCAL；成绩/考试为当前账号 MMKV 缓存，换账号、冷启动、过期需真实回归 |
| 校历、作息、学期 | `school/SicauAcademicData.kt`、`SicauOfficialSource.kt`、`SicauImageParser.kt`、`VerifiedAcademicCache.kt`；assets `sicau-academic.json`；`feature/timetable/ui/AcademicTermDisplayFormatter.kt` | 历史公开官网读取/模拟器显示/隔离 OCR 更新通过；未来官方新版未验证 | 10小节5讲统一；实际提醒、小组件随真实课程回归；未知入学年显示真实学期短名，非固定“大二上” |
| V1.0.7 通用层 | 首页、导航、ProfileSettingsRoute、提醒/Widget 独立入口、网页菜单、绑定页等已继承；川农主题与身份保留 | 部分历史模拟器视觉通过；全 App/Owner 验收未完成 | 完整手势、通知权限回流、真实提醒到达、桌面 Widget、公告/反馈/资料等真实业务未完整验收；电费宿舍目录/余额/充值、空教室接口仍未完成 |

统一认证旧字段匹配已兼容 `data.user.number`（有 owner id）和受约束的 users 条目；但当前绑定和教务同步的权威身份已经切换为教务本人页。`SicauNativeAuth.identity()` 的异常分支仍可返回 `PARSER_PENDING`，`SyncPhase` 枚举也保留该项；**不能据字符串存在就断言三个业务解析器仍为空实现**。

学校数据失败不清空有效课表的承诺针对同步失败路径。现有 `SchoolSyncEngine.clear()` 是显式解绑/清理，会清课程库；旧 schema 1–9 仍有历史 destructive fallback。本轮验证不通过解绑、卸载、`pm clear` 或旧库覆盖制造“新鲜会话”，需要干净状态时使用独立测试 AVD/实例。

### 已完成与必须保留的 UI / 公共数据

V1.0.7 通用层参考正式长理提交 `e3dd29fef08ed1367433442f7dedb1bdd25a8981`（tag `v1.0.7-release-20261004`）。只继承成熟通用界面/逻辑，未继承长理产品身份、接口、签名、账号库或更新链。

学校服务已有官网原生资讯、统一网页容器、教务原生快捷入口、一网通办公开目录、缴费入口及原生校历。登录后审批提交、支付及其回跳没有完成真实验收，不把容器可打开写成业务通过。

已内置 2026—2027 两学期：第一学期行课起点 2026-09-07，第二学期 2027-03-01。实践周 2027-01-18～24 与寒假 2027-01-18 起同时保留。校历检查周期24小时，官方图变化后本机 OCR、规则验证再写缓存，失败保留旧版；未证明可识别任意未来版式。

| 小节 | 时间 | 讲次 |
|---|---|---|
| 1 / 2 | 08:10–08:55 / 09:05–09:50 | 1 |
| 3 / 4 | 10:10–10:55 / 11:05–11:50 | 2 |
| 5 / 6 | 14:20–15:05 / 15:15–16:00 | 3 |
| 7 / 8 | 16:20–17:05 / 17:15–18:00 | 4 |
| 9 / 10 | 19:30–20:15 / 20:25–21:10 | 5 |

原图来自 `https://www.sicau.edu.cn/ggfw/rlxl.htm`，实际资源 URL 和指纹已存 `app/src/main/assets/sicau-academic.json`；补充材料中两张图与该指纹一致。公开校历与作息不依赖个人学校账号或 Backend。

## D. 登录与数据流的当前实现

1. 原生 `BindingUserActivity` 收集学号、统一认证密码及可选教务密码，`NativeBindingViewModel.begin()` 初始化 `NativeAuthSession`，取得学校验证码。密码只在内存，验证码单次提交，不自动盲试。
2. 统一认证 origin 为 `https://auth.sicau.edu.cn`：`/wui/`、`GET /api/v1/captcha`、`POST /api/v1/cas/login`；正常字段 `appid/number/password/code/uuid`。accepted 检查学校业务结果，不以 HTTP 200 代替成功。
3. `academicPassword.ifBlank { password }`：有独立教务密码则使用其原值，留空才沿用上方密码。不做大小写、前后缀或任何所谓学校通用转换。
4. `SicauJiaowuSession.login` 以独立 OkHttp CookieJar 直连登录页，取得 ASP 会话、动态 `sign/hour_key`，向 `check.asp` 提交 `user/pwd/lb=S/submit/sign/hour_key`，保持 Referer/Origin。动态字段日志只输出存在布尔值。关闭自动跳转和自动网络重试，按 charset/GBK 解码。
5. 302/脚本跳转仅作为候选登录结果。继续 `GET bjiben.asp`，从明确学号字段解析，严格与输入学号匹配；Cookie 存在、输入账号和欢迎页都不是绑定证据。
6. `SchoolSyncEngine.bindJiaowu` 激活统一认证与教务会话。统一认证使用 `SchoolSessionVault`；教务使用 `SicauJiaowuSessionVault`（Android Keystore AES-GCM、`noBackupFilesDir/sicau-jiaowu.enc`）。不跨电脑搬运加密会话。
7. 统一认证 Cookie 的原生↔WebView 桥在 `SicauNativeAuth.exportToWebView/importFromWebView`，只针对已知认证域。**没有通用浏览器→App Cookie 导入，没有已实现的 WebVPN 代理域/路径映射，也没有教务 ASP 会话与 WebView 的完整桥接。**WebView 能登录不能证明独立 OkHttp 业务会话可用。
8. 同一教务 session 请求三类固定业务页面；恢复会话先重新请求本人页，身份匹配后才信任并保存轮换 Cookie。解析器拒绝登录页/跳转/不明确表格/无法确认学期等异常。
9. `SchoolSyncEngine.performLocked/apply`：课程事务写 Room，仅替换同账号同学期 SCHOOL 行；合并同课程时段周次，保护 LOCAL/手动课；成绩/考试更新本地 MMKV；失败保留旧缓存并标注错误。
10. revision 通知课表重读；首页、课表、提醒和 Widget 共享本地课程数据来源。原生“学校账号已绑定”允许 SESSION_VALID，因此**绑定成功仍不能代替课程、成绩、考试三项同步成功**，必须核对各项状态与页面。

官方路径如下；这些是现行代码固定目标及历史浏览器观察地址，现场仍须验证当前可达性：

| 业务 | 地址 |
|---|---|
| WebVPN | `https://webvpn.sicau.edu.cn/` |
| 统一认证 | `https://auth.sicau.edu.cn/wui/` |
| 教务登录页 | `https://jiaowu.sicau.edu.cn/web/web/web/index.asp` |
| 正常登录提交 | `https://jiaowu.sicau.edu.cn/jiaoshi/bangong/check.asp` |
| 本人信息 | `https://jiaowu.sicau.edu.cn/xuesheng/dangan/banji/bjiben.asp` |
| 当前课表 | `https://jiaowu.sicau.edu.cn/xuesheng/gongxuan/gongxuan/kbbanji.asp?title_id1=4` |
| 全部成绩 | `https://jiaowu.sicau.edu.cn/xuesheng/chengji/chengji/sear_ch_all.asp` |
| 考试安排 | `https://jiaowu.sicau.edu.cn/xuesheng/kao/kao/xuesheng.asp?title_id1=01` |

WebVPN 代理资源地址由官方门户实际导航取得，不凭历史代理 URL 猜测，不自建中转。校园网若直连仍失败，继续按网络/认证/业务节点定位，不据此换产品路线。

## E. 已尝试的方法、结果与记录来源

已读取本机五个相关聊天的可访问记录、最终汇报、两份主要项目报告，并以当前源码与产物核对。未将聊天中的未来计划当实现。

| 任务记录 | 本次确认的有效结论 | 不能沿用的旧结论 |
|---|---|---|
| 原生化校历（`01a0fd70-b5fb-7d52-bef9-1b42f1967ff4`） | 官方图→结构化校历/作息、原生 UI、隔离 OCR 更新测试已经存在，入学年按账号配置 | preview.4 不是最新验收 APK；未来官网版本与真实支付/空教室仍未通过 |
| 追平我的川农与长理 V1.0.7（`01a105ad-2180-7813-a757-6ac2bf451290`） | 统一认证曾成功但身份字段匹配失败；不是笼统密码错误。通用 UI 与认证桥接已保存 | 当时“课表解析未实现”已被10月8日修复取代；完整视觉验收仍未完成 |
| 修复川农教务登录会话（`01a10792-daf8-7d13-bf81-b6003c9cba21`） | 旧链路缺教务 ASP 会话和本人页；新增独立 CookieJar、动态表单、严格身份、加密存储 | 当时仍用同一密码且业务 pending；当前已有独立密码与业务解析 |
| 修复川农教务真实绑定失败（`01a10aab-e635-7ba1-8db9-438951c00724`） | 农屿工具包最小 Harness 构建/安装可用，真实校园网 A/B 未验证 | 不是完整农屿 App，也不是 Golden Reference PASS；该任务参考 APK/诊断材料继续仅本地保留 |
| 修复川农教务数据同步（`01a11909-9683-71d2-9a19-d84fabf18a5d`） | 真实浏览器确认四业务页结构；29文件修复、189 JVM、10模拟器测试；原生真实未验证 | 开始时 pending 的根因描述不能当最终代码状态；未推送的事实由本次同步更新 |

主要历史报告：原机 `04_Docs/JIAOWU_DATA_PIPELINE_REPAIR_20261008.md`、`04_Docs/V107_ALIGNMENT_PROGRESS_20261004.md`。脱敏证据索引随仓库保存于 `docs/handoff/evidence/`，原始私人 HTML、账号、验证码及完整日志不进入交接。

农屿已实际查阅的模块位于 `packages/nongyu-tool-jiaowu`：`TECH.md`、`core/login/index.ts`、`core/utils/request.ts`、`fetchJiaowuHtml.ts`、`core/jiaowu/{personalInfo,courseInfo,scoreInfo,examInfo}.ts`、`core/extractor/*Extractor.ts`，以及 RN `apps/nongyu-rn-app/src/modules/course/model/mapJiaowuCourseItems.ts`。借鉴的是动态表单、ASP/GBK、页面字段及课程映射思路，当前产品独立用 Kotlin/Jsoup 实现；未复制农屿 App runtime/UI。许可边界见 `THIRD_PARTY_NOTICES.md`。参考仓库存在含凭据和个人信息输出的历史联网测试，不运行、不打包、不把参考仓库整份混入产品 Git。

尚未排除：模拟器 DNS/TLS/网络差异、真实 ASP 续期/失效差异、未来页面结构、真实考试文本变体、跨组件会话衔接。已排除当前代码“完全不请求业务页”的旧占位原因；仍不能据此宣布当前原生登录已修复成功。

## F. 校园网现场验证手册

### F1. 安装并记录测试起点

先记录 `git rev-parse HEAD`、`git status --short`、APK SHA-256、包名/版本、目标设备版本。优先在目标机用自己的 Debug 包开展连续调试，避免先装旧 Debug 再遇到签名不匹配。

```powershell
$adb = Join-Path $env:ANDROID_HOME 'platform-tools\adb.exe'
& $adb devices -l
$serial = Read-Host '输入上面本轮测试的模拟器序列号'
& $adb -s $serial shell getprop ro.build.version.release
& $adb -s $serial shell getprop ro.build.version.sdk
& $adb -s $serial shell getprop ro.product.cpu.abi
$apk = Join-Path (Get-Location) 'app\build\outputs\apk\debug\MyChuannong_debug_v0.1.0-preview.8.apk'
Get-FileHash -LiteralPath $apk -Algorithm SHA256
& $adb -s $serial install -r $apk
if ($LASTEXITCODE -ne 0) { throw '安装失败，检查签名/版本；不得自动卸载或清数据。' }
& $adb -s $serial shell am start -W -n 'com.mychuannong.app.debug/com.mychuannong.app.core.MainActivity'
```

补充包 Debug 可用于快速观察；其签名是原机调试签名，目标机新生成的 debug.keystore 通常不同。`INSTALL_FAILED_UPDATE_INCOMPATIBLE` 时保留原实例，使用独立测试 AVD 安装目标机包；不迁移调试私钥、不直接卸载清数据。Debug 与 Release applicationId 不同，可并存。Release 只能用既有川农签名覆盖；真机测试前只读确认目标前台 Activity，操作限目标 App，不改手机网络/系统设置。

### F2. 分别检查宿主机和模拟器网络

Windows 浏览器分别打开 D 节 WebVPN、统一认证、教务登录页；记录 DNS/连接/TLS/最终登录页或重定向结果。必要时 `Resolve-DnsName jiaowu.sicau.edu.cn`、`Test-NetConnection jiaowu.sicau.edu.cn -Port 443` 仅辅助定位，不能替代浏览器与 App 请求。

模拟器自身浏览器也分别打开同样三个入口，完成校园门户要求的正常登录；宿主可达不等于模拟器可达，ping 通不等于 TLS/业务通过。记录代理、DNS、TLS、跳转分别在哪一步失败，仅调整实际相关配置，不关闭证书验证。先用浏览器确认网络，再用 App 原生客户端复核。

### F3. 同账号、同学期三方对照

1. 官方教务：经官方 WebVPN 正常进入，校园直连作为对照；确认该账号本人信息、选定学期以及课表/成绩/考试实际数据。
2. 正式农屿 App：按其正常产品账号与学校绑定流程；记录官方来源、版本、包名以及可取得时的 APK 哈希。最小“川农教务验证器”不替代正式农屿。没有可取得的正式农屿则记录该对照缺项，继续官方与我的川农验证。
3. 我的川农：同一授权账号，同一网络/学期；统一认证和教务密码分别使用真实值。现场仅在私密表单输入，切勿在 MD、命令行参数、Git、截图或共享日志中记录。

### F4. 记录前一个成功节点与第一个失败节点

按以下节点逐项填“通过/失败/阻塞/未验证”，附证据类型、时间、脱敏事件和源码函数：网络→统一认证→官方 WebVPN 资源（浏览器与原生分栏）→教务登录页→ASP/dynamic fields→check.asp→本人页→identityMatch→三业务响应→解析→Room/MMKV→原生 UI。

Debug 可实时查看已设计为脱敏的 `JIAOWU` 事件：

```powershell
& $adb -s $serial logcat -v time 'JIAOWU:D' '*:S'
# 停止实时查看后，导出严格白名单事件；不导出原始日志，不清除 logcat。
.\docs\handoff\Collect-JiaowuEvidence.ps1 -Serial $serial -Adb $adb
```

脚本默认写入仓库已忽略的 `logs/campus`；仅保留状态码、固定失败枚举、布尔值、数量。不导出 Cookie、动态字段值、学号、私有 HTML 或全量 AndroidRuntime。输出可能含当前日志缓冲中更早的尝试，应记录本次开始/结束时间并与当前失败对应；没有日志不是成功，Release 不输出这些 Debug 事件。

关键观察事件：`GET_LOGIN_PAGE`、`sessionCookiePresent`、`dynamicFields`、`POST_LOGIN`、`loginClassification`、`PERSONAL_INFO`、`studentIdParsed`、`identityMatch`、`localSessionSaved`、`COURSES/GRADES/EXAMS status`、`COURSES mapped/records/saved/termMatch`、`bindingReady`。统一认证、MMKV 展示等没有全覆盖日志，必要时在现有代码补充同样脱敏的诊断，不记录秘密。

### F5. 完整数据闭环验收

| 项目 | 实际检查与通过条件 |
|---|---|
| 身份 | 学校返回本人字段严格匹配；分享证据只写匹配结果 |
| 课表 | 核对学年学期、课程、星期、周次/单双周、节次、地点、教师；多时段逐项一致，不定时课程无虚构节次；本次请求确实写入对应账号/学期 Room |
| 成绩 | 逐条核对官方确实提供的课程、成绩、学期、学分/绩点；缺项仍未知；不以25条历史数量作为本次硬断言 |
| 考试 | 真实有记录核对课程、日期/时间/地点；当期无记录必须官方同学期对照确认；空表不能证明真实日期解析通过 |
| 原生页面 | 首页今日/明日课程与课表同源一致，成绩和考试页面展示本次读取结果；不以网页中显示或“登录成功”提示替代 |
| 缓存 | 再刷新不重复导入；退出重进后可读；会话正常失效后保留旧有效缓存并提示重登；账号/学期切换不串数据；保留手动和已导入课程 |
| 公共层回归 | 当前周/日期表头/10小节作息一致，提醒和桌面 Widget 读取同一课程；实际到达与桌面显示另给证据 |

首次验证宜使用新的独立测试实例建立空缓存起点，然后真实读取、再冷启动核对，避免将旧缓存、OCR 导入、手填或演示课误认成自动同步。可用 Android Studio Database Inspector 在本机查看 debuggable 数据库；不将原库或真实行导出到交接/Git。仅有模拟器也继续完成；真机保留未验证，不作为模拟器调试的前置条件。

### F6. 修复、回归与回写

只修改已定位的失败节点，保留现有产品界面、学校配置和存储保护。修复后跑相关合成测试与受影响真实链路；构建包记录本次源码、版本、SHA-256、设备/网络和验证范围，再更新本文件。验证码、账号、权限或学校服务阻塞时记录成功到哪一步、恢复入口及需要的输入，继续其他不依赖该阻塞的核查。不绕过学校认证，不自动猜密码/验证码，不默认转向反编译。

## G. 优先级、下一步与完成条件

1. **P0 恢复基线**：Git 包含业务基线和本次文档；Debug 构建/安装/启动成功，安装身份及源码记录完整。
2. **P0 网络分层**：宿主和模拟器分别取得三个官方入口的实际结果；找到首个 DNS/连接/TLS/重定向失败点，或记录通过。
3. **P0 登录与本人身份**：原生统一认证、独立教务 Session、本人页读取、严格 MATCH 和本地会话保存均有真实证据；失败可明确定位到前后节点。
4. **P0 真实课表闭环**：本次官方响应→解析→同账号同学期 Room→原生首页/课表逐项一致，不丢周、不覆盖手动/导入课。
5. **P1 成绩/考试及生命周期**：真实成绩核对、考试真实为空或有记录分别确认；冷启动、重登、失效、账号/学期切换与缓存保护通过。提醒/Widget 实际表现记录；考试无样本保留明确缺项。
6. **P1 官方 WebVPN 原生适配差距**：在直连诊断证据基础上核对官方资源代理上下文；完成前始终标记未实施/未验证，不能宣称校外原生已支持。
7. **交付回写**：更新同一份文档、准确源码提交、Debug APK/校验和与脱敏证据。只同步审查过的正常源码和文档；未经明确发布授权不创建正式 Release、不部署生产、不上架。

## H. 必要材料与接收端启动语

### H1. 随 Git 取得

Android 当前开发分支、`MYCHUANNONG_HANDOFF.md`、同目录日志白名单脚本、`evidence/handoff-audit.json`、历史源码哈希清单、合成测试 fixture、Room schema、校历 JSON、README 与第三方声明均在仓库。正常 Debug 恢复没有必须私下补拷的源码、AAR 或本地 Maven 依赖。

### H2. 最小普通补充材料

原机位置：项目根目录 `05_Output/handoff-20261009/supplement`；压缩包 `05_Output/handoff-20261009/MyChuannong_Windows_Handoff_Supplement_20261009.zip`。不含 Release、Reference APK、原始私人资料或工具缓存。

| 文件 | 用途、接收端位置 | SHA-256 |
|---|---|---|
| `MyChuannong-jiaowu-20261008-debug-acceptance.apk` | 历史已核对候选包/现场对照；放目标项目 `05_Output/handoff-20261009`，不要当目标机重建包 | `3186dc4078b711ae8f61b0ee6357e39a00abf427b0759d700bc311d3d7391d80` |
| `calendar.png` | 官方校历原图，供离线对照；同上 | `63cf49c87caedd6ff18762f1348e8c16141565831d4ff0fc7524b7ad966dbae8` |
| `periods.png` | 官方作息原图，供离线对照；同上 | `95b193158464d448658bc97b4aa87f067a4f1b0cce25078078d9c35428179eca` |
| `README.txt` / `SHA256SUMS.txt` | 放置说明与包内逐项校验，随补充目录提供 | 见包内清单；压缩包整体校验见同级 `SUPPLEMENT_SHA256.txt` |

目标机能从 Git 构建时不依赖带走 APK；保留该包用于同版本行为对照。两张原图用于确认现有配置，运行时已有内置结构化配置，不要求把图复制到源码覆盖资源。

### H3. 单独在目标机配置的私密项与真实缺项

- 能读取现有 GitHub 仓库的本人账号权限；不把 Git 凭据写到文档或 clone URL。
- 本次测试授权的学校账号、统一认证密码、必要时不同的教务密码，以及学校要求的验证码/二次验证；仅本机私密输入。原机桌面账号文件不打包。
- Debug 不需带正式签名、后台管理员凭据或学校 Cookie；确需 Release 时按受限私密渠道配置既有川农签名。Android Keystore 会话与 Windows 私密配置不能仅靠复制文件保证解密。
- 正式农屿 App 的实际版本与校园网可用性由现场核实，目前没有把最小验证器当正式农屿的证据。
- 仍缺校园网原生真实全链路、真机结果及真实已发布考试记录；原生 WebVPN 适配尚未实施。以上均不是构建成功所能补齐的结果。

### H4. 可以直接发给接收端 Codex 的启动语

> 请完整读取现有 Android 仓库 `docs/handoff/MYCHUANNONG_HANDOFF.md`。这是我的川农跨 Windows 交接，GitHub 为 `HEltDeng/my-chuannong-android`，分支 `codex/restore-v105-adaptation`，业务源码基线 `8e842db648258cb15c70538ce5a85f1e2db0ef19`；文档提交在该基线之后。先查本机现有工程、Git 状态和提交，保护现场修改，不重复建项目或退回旧版。按文档恢复 JDK17、SDK36.1、Wrapper 和 Debug 构建，记录实际安装包身份。然后分别验证 Windows 与模拟器网络，用我为本次测试提供的同一学校账号、同一学期，对照官方教务页、正式农屿 App 与我的川农，定位第一个失败节点，完成真实登录、本人匹配、课表/成绩/考试请求解析、本地缓存和原生展示。现行原生教务仍直连，官方 WebVPN 浏览器成功不代表原生代理适配已完成；校园直连用于诊断，产品仍沿用官方 WebVPN 路线。保留绿白主题、已有原生界面、校历作息、提醒、小组件和本地课程，不上传个人学校数据到后台。旧 PARSER_PENDING 结论不能照抄，必须看当前实现。仅模拟器通过就明确写模拟器，真机未测保留未验证。先简要报告源码基线和首个验证动作，然后直接开展工作；完成后更新同一份交接文档、构建 APK、记录校验和与脱敏证据并同步正常源码。验证码、账号或学校服务有真实阻塞时说明成功节点和恢复入口，继续其他独立工作。
