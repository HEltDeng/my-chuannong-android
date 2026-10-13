# 我的川农 Windows 开发交接与校园网验证

核查日期：2026-10-09。原机记录使用 Asia/Singapore；接收端现场记录使用 Asia/Shanghai。适用产品：MyChuannong / 我的川农，内部预览。

**最新功能与审计状态见文末 2026-10-13 各节及 [源码审计报告](../audit/2026-10-13-source-audit.md)。I 节记录 2026-10-09 的学校真实账号验证，不代表本轮重复验证；其他旧版目录、版本号和待办保留作历史背景。**

本文件为当前权威交接入口，随 Android 仓库同步。后续关键修复和真实测试须更新本文件。历史报告中的“当前”“待适配”和版本号只代表其记录日期；与本文件不一致时，以本文件所列源码核查及证据为准。

## A. 接手即读

目标：运行现有 Android 产品，以同一授权学校账号和同一学期，对照官方教务页面、正式农屿 App，验证学校登录、本人身份、课表/成绩/考试请求、解析、本机缓存和原生页面。接收端已补齐 Google 模拟器上的核心直连证据，后续缺项见 I 节。

- Android 分支：`codex/restore-v105-adaptation`。
- 已保存的业务源码基线：`8e842db648258cb15c70538ce5a85f1e2db0ef19`。
- 原远端旧基线：`ebff336ca650a1c2b4733774de485835d9dbd9e8`。只拿到旧基线会缺少 10 月 8 日的数据解析及缓存修复。
- 包内版本仍为 `0.1.0-preview.8 / 8`，不能只凭版本号识别本次修复包；按提交与 APK SHA-256 核对。
- 课程、成绩、考试三个 SicauReader 入口已从占位改为实际请求和 Kotlin/Jsoup 解析；独立教务密码输入、Room 11→12 迁移、同账号同学期学校课程替换、失败保留缓存均已实现。
- 接收端最终 Debug 构建通过，JVM 测试 190 项通过、0 失败/错误；Google 模拟器上 9 项 Room/会话合成仪器测试通过。原机历史为 189 项 JVM、10 项仪器测试，不能混作本轮重跑数量。
- 官方 WebVPN 浏览器曾看到本人信息、15 条课表明细、25 条成绩及当期空考试页；这是 10 月 8 日记录，**不是本次重新登录，也不是原生真实同步通过**。
- 原生 Google 模拟器核心真实同步：已取得通过证据，详细范围见 I 节；真机仍为 `PENDING_REAL_DEVICE`。
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
- 原机缺项的接收端更新见 I 节；真机结果、真实已发布考试记录和原生 WebVPN 适配仍未补齐。

### H4. 可以直接发给接收端 Codex 的启动语

> 请完整读取现有 Android 仓库 `docs/handoff/MYCHUANNONG_HANDOFF.md`。这是我的川农跨 Windows 交接，GitHub 为 `HEltDeng/my-chuannong-android`，分支 `codex/restore-v105-adaptation`，业务源码基线 `8e842db648258cb15c70538ce5a85f1e2db0ef19`；文档提交在该基线之后。先查本机现有工程、Git 状态和提交，保护现场修改，不重复建项目或退回旧版。按文档恢复 JDK17、SDK36.1、Wrapper 和 Debug 构建，记录实际安装包身份。然后分别验证 Windows 与模拟器网络，用我为本次测试提供的同一学校账号、同一学期，对照官方教务页、正式农屿 App 与我的川农，定位第一个失败节点，完成真实登录、本人匹配、课表/成绩/考试请求解析、本地缓存和原生展示。现行原生教务仍直连，官方 WebVPN 浏览器成功不代表原生代理适配已完成；校园直连用于诊断，产品仍沿用官方 WebVPN 路线。保留绿白主题、已有原生界面、校历作息、提醒、小组件和本地课程，不上传个人学校数据到后台。旧 PARSER_PENDING 结论不能照抄，必须看当前实现。仅模拟器通过就明确写模拟器，真机未测保留未验证。先简要报告源码基线和首个验证动作，然后直接开展工作；完成后更新同一份交接文档、构建 APK、记录校验和与脱敏证据并同步正常源码。验证码、账号或学校服务有真实阻塞时说明成功节点和恢复入口，继续其他独立工作。

## I. 接收端校园网现场记录（2026-10-09，Asia/Shanghai）

### I1. 接收版本与恢复结果

- 本机实际工作副本：`C:\Users\Administrator\Documents\ChatGPT\我的川农\01_Repositories\my-chuannong-android`。`E:\CODEX\mychuannong-workspace` 是同目录的 ASCII junction，不是第二份工程。
- 接收参照与拉取时远端 HEAD：`c8f17c89653274615bec235b36e5d0abf8b98ba0`；已确认包含业务基线 `8e842db648258cb15c70538ce5a85f1e2db0ef19`。没有回滚到业务基线，也未覆盖旧工程成果。
- 本次最终 APK 对应修复源码提交：`b4f9cae4d66aeaad1c85b11959c628c91f6a0a97`。其后的交接提交只添加文档/脱敏证据，不改变 APK 源码。接收端提交使用 Codex 工具身份，未改用户全局 Git 身份。
- 完整阅读正式 MD。补充 ZIP 的 SHA-256 与用户给定值及校验文件一致：`4ab9eceb5fa33aac4a99261e2293836f1322f3d50a61275d465c9eee3194fcd1`；包内清单四项全部通过，原 ZIP 与历史 APK 均保留。
- 本机 JDK：Temurin 17.0.19+10，位于 `E:\CODEX\toolchains\jdk17\jdk-17.0.19+10`；SDK：`E:\CODEX\toolchains\android-sdk`，platform 36.1、build-tools 36.0.0。现有 Gradle Wrapper 9.4.1 及仓库校验值未修改。
- Debug 最终构建及 `testDebugUnitTest` 通过：190 tests，0 failures/errors。最终考试页面修复在 Google 模拟器上实测。另用隔离的 `.validation` 包执行 7 项 `SicauPipelineStorageTest`、1 项 `SicauJiaowuVaultDeviceTest`、1 项 `SchoolSessionVaultDeviceTest`，合计 9 项通过；使用合成数据，不接触 `.debug` 的真实缓存。validation 默认未签名，接收端仅为其输出副本使用本机标准 debug.keystore 签名，未要求正式 Release 密钥。

### I2. 模拟器与安装包

按用户要求切换到 Google 官方 Android Emulator；雷电中的原有应用及数据保留，先前雷电表单漏填/焦点错误不作为学校拒绝凭据的证据。

| 项目 | 本轮实际值 |
|---|---|
| AVD / serial | `MyChuannongCampus` / `emulator-5580` |
| 系统 | Android 16 / API 36，Google APIs x86_64 revision 7，支持 arm64-v8a 转译 |
| Emulator / 加速 | 37.2.12，AEHD 2.2，SwiftShader |
| 屏幕 / 时区 | 1080×2400，density 420；关闭该测试 AVD 自动时区并设为 Asia/Shanghai |
| Debug applicationId | `com.mychuannong.app.debug` |
| versionName / code | `0.1.0-preview.8` / `8` |
| 最终 APK 文件 | `MyChuannong-campus-20261009-b4f9cae-debug.apk`，60,306,051 bytes |
| APK SHA-256 | `c171d75a595c190e81fc8317f782c5c0097a7d06365cda92f2fd3cc7da40bced` |
| 本机 Debug 证书 SHA-256 | `a0fee4a511b677d3e2bfc1abba571e6d83fb0e579a8b72a9d185946a3b9626b6` |
| 农屿 | 用户 Downloads 中已有 APK，`com.nongyu.app`，4.0.4 / 8；并非最小验证器 |
| 农屿 APK SHA-256 | `7de720f8ab6799dc8110dbae9b6f82faf413c6954b856588f7920f736d36a951` |

Google 模拟器曾在首次绑定成功后发生一次宿主进程意外退出，原因未确定。未 wipe-data，按原 AVD 冷启动后课程和成绩仍可读；本次未复现。已停止闲置 Gradle daemon 释放内存。不能将模拟器进程退出写成 App 崩溃根因已确定。

### I3. 网络与真实验证矩阵

用户确认 Windows 已完成校园网认证。Windows 仍启用 `127.0.0.1:7897` 系统代理，学校域名出现 `198.18.0.x` DNS 映射，因此本记录仅证明**本机当前校园环境下原生直连 URL 链路可用**，不证明实际路由完全绕开代理，也不证明校外可用。

| 节点 | 结果与证据边界 |
|---|---|
| Windows 三入口 | WebVPN、统一认证、教务入口正常 TLS/HTTP 200 或重定向至官方登录页，浏览器可见登录页面 |
| Google 模拟器自身网络 | 原生统一认证验证码、认证及教务请求成功，教务登录页/本人页/三业务均有真实 HTTP 日志；不是用宿主可达推断模拟器可达 |
| 模拟器浏览器三入口 | 未单独完成三入口浏览器验证；不可把原生请求证据写成该项通过 |
| 统一认证 | 使用本机独立 E 认证文件，完整填写学号和密码并完成学校验证码；进入本人教务匹配流程并绑定成功 |
| 独立教务登录 | 相同学号、独立教务密码。GET 200、ASP cookie 存在、sign/hour_key 存在、POST 302、REDIRECT_SUCCESS |
| 本人身份 | PERSONAL_INFO 200，studentIdParsed=true，identityMatch=true，localSessionSaved=true，bindingReady=true |
| 课表 | 本次官方响应确认 `2026-2027-1`；15 条学校记录映射 25 条课程并保存 Room，termMatch=true；原生首页今日 4 个时段与第 5 周课表对应 |
| 农屿课表对照 | 同账号成功登录。农屿默认第 1 周；我的川农切换第 1 周后，对照可见课程的星期/节次/教室排列一致。农屿初次日期设置与当前周不一致，未据此改变我的川农官方校历。未穷举全部教学周 |
| 成绩 | 官方响应解析 25 条：2025-2026-1 为 13 条、2025-2026-2 为 12 条。我的川农滚动页面的 25 条课程名称、成绩、学分、单科绩点与本次官方响应解析结果逐项匹配（25/25）；农屿两学期数量一致，已查看的首屏成绩字段一致，农屿全量逐条对照未完成 |
| 考试 | 当前正考入口 HTTP 200、现行解析得到 0 条；最终原生页明确显示“查询成功，学校当前未返回考试安排”。该响应未确认显式学期，不能进一步声称特定学期考试全部核对通过；真实有记录的日期/时间/地点仍无样本 |
| 农屿考试 | 本次进入正考页以及重试均显示“教务数据获取失败”。其成功节点为登录/课表/成绩，首个观察失败节点为考试获取；无其内部日志，不能判定具体原因 |
| 冷启动和 MMKV/Room | 模拟器意外重启后仍能展示首页及 25 门成绩；最终包覆盖安装后数据保留。另主动关闭测试 AVD Wi-Fi/移动数据，force-stop 后冷启动，首页 4 个时段与 25 门成绩仍可读；主动刷新提示网络不可达，关闭提示后成绩可见文本与刷新前完全一致。随后恢复两项网络开关 |
| 重复同步 | 登录和多次原生刷新均记录 15 条/25 条映射及成功保存，原生页面未观察重复；Room 替换/事务/保护规则另由合成测试覆盖。未导出真实数据库作全量行级比较 |
| 真机 | `NOT_TESTED`；没有以模拟器替代真机结果 |

成绩逐项比对在 Windows 本地进行：读取本机私密文件登录官方站点、在内存解析响应，再对照 Android 当前 UI 文本；只把数量和匹配布尔值写入共享证据。它验证显示与响应一致，不能作为解析器的完全独立实现证明。未保存/提交私有 HTML、Cookie、账号密码或真实数据库。

### I4. 已确认问题及针对性修复

1. **本人页说明误判会话失效**：前一成功节点是 check.asp 重定向成功且本人页 HTTP 200；首个失败点是 `personalInfo()` 在有效身份表之前匹配到普通说明中的“重新登录”。仅排除现场确认的完整说明片段“提交后重新登录便可正常访问教务系统”；继续严格解析身份表和匹配本人，真正的超时/重登提示、密码表单、hour_key 均继续拒绝。合成回归覆盖正常说明、缺身份、身份不符以及真实过期信号；Google 实际完整绑定通过。
2. **考试成功空列表页面空白**：请求/解析成功，但 Activity 仅在非空时更新 adapter，也没有空状态。现在空列表也更新 adapter，并按同步状态显示成功空结果或失败原因；未登录提示不再错误声称解析待适配。最终 Debug 实机模拟器页面复核通过。未改主题、课表布局、课程保护或提醒/Widget 读取路径。

### I5. 交付、证据与下一轮明确缺项

本机交付目录：工作区 `05_Output\campus-delivery-20261009`，提供最终 Debug APK、SHA256SUMS.txt、本 MD 副本、脱敏结果及无个人字段的考试状态截图。APK 仅本机交付，不创建 GitHub Release、Tag 或公开发布。

仓库脱敏证据：`docs/handoff/evidence/receiver-campus-20261009.json`、`receiver-native-events-20261009.txt`、`receiver-host-check-20261009.txt`。事件可能包含本次缓冲中的多次刷新；初次绑定窗口约 10:01，最终包回归约 10:15–10:25。真实截图/控件 XML 与本机账号文件不纳入 Git。原机 10 月 8 日数据只作背景，I 节结果来自本轮重新登录。

仍待验证或实施：真机；模拟器浏览器三入口；全部周次及农屿全部成绩逐条核对；官方考试响应明确学期和真实有记录样本；真实账号切换/学校正常会话过期（本轮验证的是断网失败保护，不能等同会话过期）；提醒实际到达、桌面 Widget 实际显示。手动/导入课程、跨账号/学期保护目前有合成测试证据，未用第二个真实账号测试。**官方 WebVPN 原生代理适配仍未实施，校外原生支持仍未验证。**

## J. 官方 WebVPN 原生接入现场记录（2026-10-09 下午）

本节追加并更新 I5 的现状，不覆盖上午证据。用户随后纠正：当前仍是校园网，要求先构建，再到真实校外测试。因此本轮已经证明「校园环境下实际经过官方 WebVPN 的原生链路」，**未证明脱离校园网可用，不满足最终校外验收**。

### J1. 版本与环境

- 起始提交 `fcbee8c396fb0a85dd820eb3c51be0494d91064b`，包含上午业务 `b4f9cae4d66aeaad1c85b11959c628c91f6a0a97`。起始工作区干净，未回滚。
- 本轮业务源码提交：`c86e46bf409a2f2abc0045dcf7b5cbd36d77cbd4`，原分支 `codex/restore-v105-adaptation`。后续交接提交不改变 APK 源码。
- 工作目录及 junction、远端、JDK/SDK 沿用 I1。测试设备改为 Google AVD `MyChuannongTest` / `emulator-5554`，Android 16 API 36 x86_64；没有真机。此 AVD logcat 使用 UTC，08:00–08:25 对应北京时间约 16:00–16:25。
- 用户确认校园网络；Windows 系统代理启用 `127.0.0.1:7897`，模拟器 global http_proxy 为 null。没有将网络开关模拟写成真实校外切换；宿主代理的具体分流未独立证明。
- Debug 包 `com.mychuannong.app.debug`，`0.1.0-preview.9` / `9`，APK `MyChuannong_debug_v0.1.0-preview.9.apk`。
- APK SHA-256：`c5540b026a448ee82eaf785be2fe837b67a51a7a779bb59f6548e429f78cc35b`。
- Debug 证书 SHA-256：`a0fee4a511b677d3e2bfc1abba571e6d83fb0e579a8b72a9d185946a3b9626b6`。覆盖安装成功；未卸载、未清除真实 App 数据。无 Release、Tag 或公开 APK。

### J2. 实际官方链路与证据等级

1. 官方起点 `https://webvpn.sicau.edu.cn/` 跳到同一 WebVPN 域下的 `/https/<官方资源标识>/wui/` 统一认证页面，service 返回 WebVPN 的 CAS 登录回调。来源：当日未登录页面跳转及 App WebView 实际行为。未把标识或加密算法当成可自行生成代理地址的依据。
2. 官方页面提供密码/SMS 入口；本次使用密码入口，提交统一认证学号、统一认证密码、图形验证码。首次浏览器页和 App 新 WebView 会话都可见验证码；本次实际填写并通过一次。没有证据证明每次都要求、验证码策略或有效期固定。
3. 成功到 `/m/portal`，标题「WebVPN资源访问控制系统登录页面 - 资源站点」。官方卡片实际显示学校门户 `ehall.sicau.edu.cn` 和教务处 `jiaowu.sicau.edu.cn`，其 href 为 WebVPN HTTPS 代理路径。来源：实际门户链接属性，只读取 URL 与标签，不读取密码字段。
4. 点教务处后出现教务页面/登录 iframe。App 从包含 sign/hour_key/pwd 的实际同源 iframe 发现资源前缀，再以原生 OkHttp GET 登录页；`WEBVPN_NATIVE_LOGIN_PAGE verified=true`。网页到达不算本人已登录。
5. 原生本人页初次 302，需要独立教务登录；本次另外输入教务密码。登录页 GET 200、动态 sign/hour_key 和会话存在，POST 302，随后本人页 200，学号解析/严格匹配均 true，再保存本机绑定。页面可见统一认证登录选项，但未点击验证该 SSO，不声称学校不存在 SSO。
6. 课表、成绩、考试及本人页请求均记录 `transport=WEBVPN`，集中路由只准许当前官方前缀；代理请求不会回落到直连。所有业务仍用原有解析器、GBK 处理和本机事务/缓存规则。
7. 新增通用官方资源入口及学习门户映射。实际门户 href 自动发现并保存后，从 App「学校门户」入口打开标题「一网通办」，当前外层 host 仍为 `webvpn.sicau.edu.cn`。只证明目标页面可达，不声明该系统业务登录、权限或全部功能已通过。

官方背景来源（与现场证据区分）：
- 信息与教育技术中心 2023 年 VPN 更换公告：https://ietc.sicau.edu.cn/info/1021/3141.htm 。其链接指南当日返回无权限，不能据此推断实现细节。
- 图书馆 WebVPN 说明：https://lib.sicau.edu.cn/LibSicau/LanMu/neirong.asp?d=76&m=1 。
- EOL 官方校外访问指引：https://eol.sicau.edu.cn/ 。
- Android CookieManager 官方接口：https://developer.android.com/reference/android/webkit/CookieManager 。

农屿参考源码只读核对固定版本 `7265f694bfcfa7aa05f93e9ccafa42ed8142fb40` 的 `packages/nongyu-tool-jiaowu/core/login/index.ts` 与 `core/utils/request.ts`，仓库 `nongzhuan555/nongyu-system`。这些模块直接请求教务 HTTPS、使用 ASP 和动态字段/GBK；不能推断整个安装版没有其他通道。源码与本机 4.0.4 是否一致、干净校外刷新是否依赖缓存，本轮未验证。未执行参考仓库账号测试。

### J3. 状态、存储与恢复

- `SicauWebVpn` 只表示校外资源通道，`SicauJiaowuAuth` 只表示经本人核验的教务会话，本地身份和缓存归属仍独立。统一认证旧实现保持独立，没有将 WebVPN 登录当成统一认证原生会话或所有系统登录。
- 认证页面在官方 WebView 完成。CookieManager 按实际 URL 选择 Cookie；原生请求仅在许可前缀内取值。原始 Set-Cookie 交给 Chromium 保留域、路径、过期、Secure/HttpOnly/SameSite 等属性。WebVPN Cookie 存于应用私有 WebView 存储；加密教务 vault 保存已核验 owner 和路由标记，不复制 WebVPN Cookie 字符串或密码。路由标记不是登录凭据。
- 教务绑定页是学号/教务密码两个输入，学校 CAPTCHA 位于官方认证页；保留校园直连诊断选项。有效代理教务会话先检查本人，可免输入密码；身份不匹配终止，凭据不变形、不跨入口自动重试。
- 冷启动读取缓存，不以 WebVPN 失效清空课程/成绩。网络变化只令下一次真实服务检查重新执行，不以 Wi-Fi 类型断定登录。同步串行，网络请求有超时，不自动重试凭据 POST。WebVPN 失效提示连接学校 WebVPN；教务失效单独处理，认证页面不能保存为空业务结果。
- 本次跨进程/覆盖安装后直接原生核验并同步，未再次输入密码或验证码；闲置约五分钟后同样通过。学校会话的真实有效时长、重启设备后的所有 Cookie 行为未测定。
- 早期两次闲置后首个请求连接失败，手动下一次成功且缓存保留；无法确定服务端原因。VPN 客户端改为不复用空闲连接，后续跨进程及五分钟闲置刷新通过。不能写成网络异常根因已确认或永久修复。
- 网页映射来自官方门户实际链接，不生成代理密文。异步资源卡片最多重读 5 次；需要恢复时用户点击「通过学校 WebVPN 继续」，最多自动返回目标一次。找不到映射时留在官方门户提示选择。真实过期后的自动回跳仍待现场验证。

### J4. 分阶段矩阵及边界

各行源码为 J1 业务提交；设备/网络为 J1。早期认证与断网实测使用同一开发过程中的 preview.9，最终包再次覆盖安装和刷新；10 项仪器测试使用隔离 validation 包，桥接实现相同，后续网页导航增量由最终包现场验证。

| 阶段/场景 | 上一个成功节点 → 首个失败节点或结果 | 证据/状态 |
|---|---|---|
| A 官方链路 | WebVPN → 统一认证 → 资源门户 → 教务 iframe → 原生检查 | PASS；实际页面/请求；会话寿命/SSO 条件仍待验证 |
| B 首次认证 | 新 App WebView 官方密码+验证码 → 资源门户 → 原生本人匹配 | 校园环境代理 PASS；完整清除后第二次重新登录未测；真实校外 NOT_TESTED |
| C 原生闭环 | native 登录 GET200/POST302 → 本人200匹配 → 课表保存展示 | PASS；课程15条学校记录映射25条，termMatch=true |
| D 成绩 | 本人匹配 → GRADES200 → records=25 saved=true → 原生25门 | PASS；本轮未重做25条全部字段独立对照，上午逐项结果见 I3 |
| D 考试 | 本人匹配 → EXAMS200 → records=0 saved=true → 明确成功空提示 | PASS_EMPTY；缺真实非空样本，响应明确学期仍未确认 |
| E 会话恢复 | 覆盖安装/进程重启 → 本人200匹配 → 三模块保存 | PASS；无再次凭据输入；不是长时间自然过期证明 |
| E 闲置刷新 | 约五分钟闲置 → 本人匹配 → 三模块保存 | PASS；前述早期连接失败也保留记录 |
| E 断网刷新 | 已有25门 → 断开模拟器Wi-Fi/数据 → NETWORK_UNREACHABLE | PASS保护；明确连接失败，不显示密码错误 |
| E 断网冷启动 | force-stop → COLD启动 → 首页/25门成绩可读 | PASS；随后恢复原两项网络开关；不等于校园/校外切换 |
| E 并发/缓存/账号隔离 | 合成管线、路由、vault、Cookie范围/轮换测试 | 自动测试PASS；真实第二账号、身份不符、错误密码未现场尝试 |
| E 真实WebVPN失效/教务单独失效/取消认证/服务故障 | 合成200认证页/302回认证/403等拒绝覆盖；现场未自然触发全矩阵 | PARTIAL；不能把合成响应写成学校实际行为 |
| F 官方资源门户/学校门户 | 新增 App 入口 → 官方门户发现映射 → 学校门户经代理标题一网通办 | PASS页面可达；未验证门户内部业务 |
| F 公告/官网 | 当日宿主访问 www/news 返回200；原公开入口保留 | 不是校外或VPN端到端证据 |
| F 校园缴费/水电费 | 当日宿主 op 返回正常登录页；未取得对应门户代理入口 | NOT_TESTED VPN；未发起付款或其他业务提交 |
| 真机/真实外网 | 无对应设备/用户暂不切换校园网 | NOT_TESTED |
| 原生页面/课程保护 | 原布局主题保持，原解析器未改；课程/成绩可见，隔离存储测试通过 | 未观察回归；提醒实际到达与Widget视觉未重测 |

最终 Debug `testDebugUnitTest`：194 tests，0 failures/errors；构建成功。隔离 `.validation` 设备测试：10 tests，通过（Cookie桥接1、管线7、教务vault1、统一认证vault1）；不使用真实账号、不访问学校。`git diff --check` 通过，提交前以本机凭据值做静默匹配检查通过。证据只包含许可事件、数量和布尔值；没有真实账号、密码、验证码、Cookie、HTML、数据库或真实截图。

### J5. 可执行下一步与交付

本机输出：工作区 `05_Output/webvpn-delivery-20261009`，含 APK、SHA256SUMS、原交接副本和脱敏证据；源码提交见 J1。

1. 在非校园网热点/移动数据环境重复「官方 WebVPN → 教务本人匹配 → 三类原生刷新」，记录系统VPN/代理是否开启，不能用本轮校园代理成功替代。
2. 保留已有缓存，另建干净测试会话重复认证；实际验证 WebVPN 单独失效、教务单独失效、取消、错误密码、同账号恢复及第二账号隔离。当前解绑会清理学习缓存，勿用它冒充仅清测试会话。
3. 真机覆盖同矩阵，补学校正常过期时长与资源回跳；继续查明早期闲置连接错误是否还会出现。
4. 从官方门户实际找到水电费/缴费对应资源后再增加映射与页面验收，不猜路径；验证学校门户内所需独立登录。
5. 取得真实非空考试样本后对日期、时间、地点逐项核对；复测提醒与Widget显示。

结论：WebVPN 与原生教务衔接已经在模拟器校园环境实际跑通；真实校外最终验收仍待完成。

## K. 学校网页服务与学习入口调整（2026-10-10）

本轮保留现有 Android 主体、主题、教务认证/同步和学校官网地址，没有引入网站数据解析或支付 SDK。

- `ElectronicActivity.kt`：保留原电费查询路由和名称，使用通用 WebView 打开 `https://bd.bdfairy.cn`。设备、室友、房间、电表、订单、充值及支付结果均由服务网站呈现。
- `SchoolServiceCatalog.kt`：学校服务增加“学工系统”，打开 `https://xsgz.sicau.edu.cn`。
- `SchoolHomeActivity.kt`：保留原路由与 `https://www.sicau.edu.cn/`，删除官网新闻列表与文章的 Jsoup 抓取及原生重绘，改为原站 WebView。
- `ServiceWebActivity.kt`、`ServiceWebPolicy.kt`、`SchoolWebPolicy.kt`：保留站点 CSS/交互与已有移动视口，仅在指定站点缺少 viewport 时补齐。容器支持安全区、键盘、滚动、缩放、网页历史、关闭、加载进度、超时、HTTP/网络/证书错误、重试及浏览器备用入口。新窗口使用独立 WebView，保留网页表单、opener 和 window.close；返回优先退回网页历史，再关闭子窗口或 Activity。
- 电费页支持微信/支付宝 scheme 和经过净化的 intent URI 跳转，要求用户操作并确认；不执行第三方 intent 提供的任意 component/flags/extras。未安装应用显示提示，存在合法 HTTPS fallback 时交回网站。返回恢复网页，由网站显示真实订单状态，不根据 Activity 返回推断支付成功；刷新使用 GET，拦截重复 POST，不采集支付表单。
- `FunctionCatalog.kt`、`AppConfigModels.kt`、`assets/app-config/default-v1.json`、`Route.kt`：移除空教室与作业菜单和对应跳转，过滤旧配置重新提供的入口；保留旧 action ID 的配置识别兼容性，避免一份旧配置让其他服务整体失效。电费入口不被旧远程 URL 覆盖。
- `OverviewScreen.kt`：清理首页关联到已移除作业路由的作业/测试快捷区，避免留下无响应入口；不改动其他课程、成绩、考试区及数据存储。
- `TimeTableComposeScreen.kt`、`TimeTableActivity.kt`、`MainActivity.kt`：课程表左上角使用现有圆角返回图标，系统返回和按钮共用逻辑；无上一级或小组件入口返回功能页（包含学习栏目），使用 safeDrawing 避开刘海及系统栏。
- `FunctionCatalogTest.kt`、`ServiceWebPolicyTest.kt`：覆盖新菜单排除、旧缓存配置、网页返回、支付 scheme 分类和原站样式保留。

验证记录以本轮最终构建结果为准。当前没有连接的 Android 设备；不将 JVM/构建结果描述为真机业务验收。仍须由用户在本人手机完成学工登录、设备/室友/房间/电表切换、微信/支付宝支付（成功/取消/未安装）、网站回调与结果展示，及不同屏幕/横屏实测。App 未增加或猜测目标网站专用支付回调协议；如运营方要求特殊浏览器环境或回调注册，应以真实站点协议进一步确认。

最终验证：执行 :app:testDebugUnitTest、:app:assembleDebug、:app:lintDebug（Gradle wrapper，--no-configuration-cache --console=plain），BUILD SUCCESSFUL。198 项 JVM 测试通过，0 失败/错误/跳过；git diff --check 无差异格式错误。Lint 任务按项目原有 abortOnError=false 配置完成，并非零诊断：4 条 NewApi Error 位于未修改的 BindingUserActivity.kt / SicauWebVpnActivity.kt，1 条 NetworkSecurityConfig Fatal 位于未修改的 res/xml/netconfig.xml；本次改动文件无 Error/Fatal。报告见 app/build/reports/lint-results-debug.html。Debug 产物：app/build/outputs/apk/debug/MyChuannong_debug_v0.1.0-preview.9.apk，SHA-256：4E15078E068FA897255C8B329952ED61833D5070ACD98E93D396D8A96AEEABC6。未发布或上传 APK。

## L. 学习栏目新增计算机考试（2026-10-10）

- `FunctionCatalog.kt`：新增 ncre / ComputerExam，名称“计算机考试”，使用现有风格的计算机图标。学习菜单的内置和动态配置均支持；旧缓存/远程配置缺少该项目时补齐，已有显式隐藏配置继续生效，其他学习条目保持不变。
- `AppConfigModels.kt`、`assets/app-config/default-v1.json`、`res/values/strings.xml`：注册 action/icon，补充默认菜单和中文文案。
- `Route.kt`：打开 `https://ncre.neea.edu.cn`，固定进入通用 ServiceWebActivity，避免远程 URL 覆盖跳转到产品官网占位页。
- `AndroidManifest.xml`：补齐 ServiceWebActivity 的非导出 Activity 注册，同时修复此前学工入口直接启动此容器时缺失注册的问题。
- `ServiceWebActivity.kt`：将 ncre.neea.edu.cn 加入缺失移动 viewport 时的补齐范围；已有 viewport、站点 CSS、HTML 和 JavaScript 保持原样。继承容器的安全区/键盘适配、滚动、缩放、网页新窗口、返回/关闭/刷新和加载/网络错误提示，不抓取或重绘考试内容。
- `FunctionCatalogTest.kt`、`ServiceWebPolicyTest.kt`：验证学习栏目入口、旧配置补齐、显式隐藏、其他项目不变及 NCRE 不注入重排 CSS。

验证：:app:testDebugUnitTest、:app:assembleDebug、:app:lintDebug 均完成（BUILD SUCCESSFUL）；200 项 JVM 测试通过，0 失败/错误/跳过。aapt2 核验最终 APK 已包含 ServiceWebActivity，exported=false。Lint 仍有 K 节列出的 5 条原有 Error/Fatal，未新增此级别诊断。未执行手机端网站登录或报名实测。Debug APK 路径不变，最新 SHA-256：C8E199C9C18E9B903C7B49415D17F313815C26EA96D44ABC834E7D7E66EEEBAC。
## M. 个人成绩单与有效必修加权排名（2026-10-10）

本轮实现成绩展示和安全读取管线，真实学校页面结构尚未验收，不能视为真实账号查询已经跑通。

- 新增 `school/SicauTranscriptParser.kt`：结构化 HTML 表格解析，保留课程名称、成绩、学分、性质、备注、课程序号及学校实际提供的汇总/排名字符串。缺考、缓考、补考、免修和小数位不转数值；只去除完全重复记录，不按课程名称合并，不自行计算均分/绩点/排名。
- 新增 `school/SicauTranscriptNavigation.kt`：从已有成绩入口的实际链接和框架发现个人成绩单与排名，不猜测新端点；限制教务来源、学生路径及当前账号查询参数，兼容已确认的 WebVPN 路由。
- `SicauJiaowuSession.kt`、`SicauJiaowuSessionVault.kt`、`SchoolSyncEngine.kt`：复用本人已授权会话和原有同步互斥锁，读取前后核对账号；排名表只选本人学号行，无法确认时返回解析待适配。成绩可读但排名失败时分别展示状态。网页入口复用当前校验过的学校会话，不读取桌面浏览器 Cookie。
- `ScoreCache.kt`：成绩单按当前账号摘要隔离，仅保存本机；账号切换/退出仍由现有清理机制管理。刷新覆盖快照，失败保留本人缓存并明确标记。
- `ScoreInquiryActivity.kt`、`ScoreInquiryViewModel.kt`、`ScoreInquiryContract.kt`：沿用应用主题和返回栏，分区呈现汇总、排名、课程卡片；支持自动换行、安全区、加载、登录失效、明确空数据、解析失败、网络异常、刷新、重新登录及打开教务网页。
- 新增 `SicauTranscriptParserTest.kt`、`SicauTranscriptNavigationTest.kt`、`SicauTranscriptSessionTest.kt`：19 项合成数据测试，覆盖原始格式、文字状态、重复课程、双栏成绩单、本人排名筛选、外部/其他账号链接拒绝、排名失败隔离和读取途中账号变化。测试没有使用真实个人成绩或截图。

验证：`:app:testDebugUnitTest` 与 `:app:assembleDebug` 成功；219 项 JVM 测试，0 失败/错误。`git diff --check` 通过（仅工作副本换行提示）。Debug APK：`app/build/outputs/apk/debug/MyChuannong_debug_v0.1.0-preview.9.apk`，SHA-256 `19DFE22D111E5339F304DC5CAEF9ED8B1B6C78EA98E44FE0E59617290966D78A`。

`:app:lintDebug` 完成，BUILD SUCCESSFUL；报告仍有 K 节所列 4 条 NewApi Error 和 1 条 NetworkSecurityConfig Fatal，位置均为未修改文件，无新增 Error/Fatal。项目仍使用原有 abortOnError=false，不能将任务成功描述为 Lint 零问题。

真实页面验证阻塞：用户确认 360 极速浏览器可打开教务网；已找到对应窗口，但 Computer Use 截图先后报告 `FrameArrived timed out` 和 `window capture timed out`，可访问性树仅有浏览器外壳。浏览器连接工具只列出空的应用内浏览器，无法连接现有 360 标签。因此没有取得受保护页面 DOM、没有提取桌面会话、没有将真实资料写入项目。当前解析规则仅由合成结构测试验证，若学校结构不匹配，明确显示解析失败/待适配。仍需通过可读取的已登录页面或脱敏结构核对真实导航、表头、汇总及排名格式，然后在手机实测；不可据构建成功宣称这部分验收通过。
### M1. 根据入口截图修正链接识别

用户提供的截图显示当前为“全部成绩记录”，顶部个人成绩单和有效必修加权排名的入口文字有【】装饰。`SicauTranscriptNavigation.kt` 已在精确匹配前移除成对装饰括号，并增加独立回归测试；其他排名入口仍不匹配。没有复制截图中的身份或成绩。此次 `testDebugUnitTest`、`assembleDebug` 成功，`git diff --check` 通过；增加 1 项测试。最新 APK SHA-256：`79B1887FAC1841046B805A8909F420C1C1027AFEB05DEE7C9892516EECAB3448`。此前 Lint 结果仍为 M 节记录，此次括号识别修正未重跑 Lint。实时浏览器捕获仍报 `FrameArrived timed out`；截图不提供 href/DOM，两个目标页面的真实结构验证仍未完成。
### M2. 根据两张目标页面截图补齐字段

用户提供个人成绩单与有效必修加权排名截图。仅参考标题、字段和布局，没有将图片、真实身份、成绩或排名数值复制进项目。

- 成绩单实际标题“四川农业大学学生成绩单”，课程按左右两组“序号/课程/成绩/学分/性质/备注”排列；增加该标题识别，测试最后一行右侧空白组。汇总实际“平均成绩绩点”作为学校原名保存和展示，不伪装成缺失、不重新计算。
- 排名标题“有效必修课加权平均成绩及专业排名”，增加“专业排名”“有效必修加权成绩”识别，仅选学号等于当前已验证用户的行。保留该行校区、系列、专业、年级作为学校提供的范围信息；不导出姓名、学号、班级或其他人的记录。列表“1条”不能作为排名总人数。
- 修改 `SicauTranscriptParser.kt`、`SicauJiaowuSession.kt`、`ScoreInquiryActivity.kt`；`SicauTranscriptParserTest.kt` 增加两项纯合成结构测试。
- 用户发来的两个地址均为 `https://jiaowu.sicau.edu.cn/jiaoshi/bangong/main/index1.asp`，只确认外层地址，未将它硬编码为成绩或排名内容页。实际内容 URL 继续从已授权会话内的真实链接发现；已请求复制顶部入口的链接地址核对，尚未取得目标 HTML/DOM。

222 项 JVM 测试通过，0 失败/错误；Debug 构建和 `git diff --check` 通过。最新 APK SHA-256：`DD19C5B4945A56BFDF0C09B784872FCB2F16CB7DBCD8F3697A135BC7D48829AC`。这些结果验证合成结构规则，不能替代真实登录会话端到端验收。

本轮 `lintDebug` 已重跑完成，仍为原有 4 条 NewApi Error 和 1 条 NetworkSecurityConfig Fatal，无新增 Error/Fatal。
### M3. 接入用户确认的内容页地址

用户从学校入口复制并提供了个人成绩单 `/xuesheng/chengji/chengji/bchengjike.asp` 和有效必修加权排名 `/xuesheng/chengji/chengji/zytongbf.asp`。`SicauTranscriptNavigation.kt` 集中保存两个 HTTPS 官方内容 URL；`SicauJiaowuSession.kt` 在核对本人身份后优先读取这两页，使用原有 CookieJar 与已确认的直连/WebVPN 路由。保留实际菜单链接发现作为兼容回退，不读取外层框架当作成绩数据，不增加账号参数或绕过认证。

`SicauTranscriptSessionTest.kt` 更新为明确内容路径，并验证没有菜单链接时仍读取成功；`SicauTranscriptNavigationTest.kt` 增加两个路径的直连/WebVPN 转换测试。224 项 JVM 测试通过，0 失败/错误；Debug 构建、`git diff --check` 通过。最新 APK SHA-256：`D5B8B1015484A5E4EEB46FB2B0EA86871928CC64D344672391BED9F31ECA2ABB`。

内容地址待确认的问题已解决；截图只用于字段及布局参考。尚未通过真实已登录会话取得这两个页面的 HTML 或在手机上完成成绩逐项对照，所以真实查询验收仍待完成，不能将合成测试结果视作真实学校响应验证。

本轮 `lintDebug` 执行完成；保留原有 4 条 NewApi Error、1 条 NetworkSecurityConfig Fatal，无新增 Error/Fatal。
## N. 底栏错位与服务页闪退修复（2026-10-10）

用户反馈底栏变为“加载中／时间：／Previous”，点击服务退出。在 MyChuannongTest 模拟器安装此前交付 APK 并进入服务页，确认 `NoSuchFieldError: R$string.feature_function_computer_exam`，栈位于 `FunctionCatalogKt.functionCatalog` → `FeatureScreen`。这是 APK 中过期资源类与新代码/资源表不一致的安装包问题；之前的 JVM 测试和增量构建成功没有覆盖该运行时故障，旧包不可继续作为交付依据。

处理：完整执行 `clean :app:assembleDebug :app:assembleDebugAndroidTest :app:testDebugUnitTest -PdeviceTestBuildType=debug --no-build-cache --no-configuration-cache`，重新生成资源、编译类及 DEX。未修改用户数据，不要求卸载或清除缓存。`app/build.gradle.kts` 增加可选 `deviceTestBuildType`（仅 validation/debug，默认保持 validation），使设备测试能针对实际 Debug 交付包执行。

新增 `ServiceNavigationSmokeTest.kt` 两项设备回归测试：通过安装包资源实际解析核对“首页／服务／我的”和“计算机考试”，点击服务回调，并渲染含学习及计算机考试的服务页；不执行学校登录，不读取个人资料。两项在安装后的 Debug 包上通过，224 项 JVM 测试通过，`git diff --check` 通过。

另在完整 MainActivity 实际点击：首页 → 服务 → 我的 → 首页。UI 自动化读取到正确的三个底栏名称，服务页“学习／计算机考试／生活”可见，切换后应用进程保持，无该进程新崩溃记录。此验证属于模拟器导航验收，不代替真实教务成绩/支付流程验收。

固定交付副本：工作区 `05_Output/navigation-fix-20261010/MyChuannong_preview9_navigation_fix.apk`，SHA-256：`C1E129515E886CFBB1C2E6C4BFBBA7AFF07503D246A2182DBECC4C11F5EF6818`。保持原 Debug 包名/签名/版本，覆盖安装；不发布、不上传。以后涉及 Android 资源变化的交付必须执行完整构建和安装包级导航冒烟检查，不以 JVM 测试代替。

完整重建后 `lintDebug` 执行成功，仍有未修改文件中的原有 4 条 NewApi Error 和 1 条 NetworkSecurityConfig Fatal，无新增 Error/Fatal。
## O. 恢复原成绩 UI，调整学工/电费网页承载（2026-10-10）

用户明确要求沿用原成绩 UI，并反馈两项服务为空白；随后确认手机浏览器可正常打开两个原 HTTPS 地址。

- `ScoreInquiryActivity.kt` 恢复 `FullScreenActivity<ActivityScoreInquiryBinding>` 和原 XML 工具栏、四指标卡、可展开课程列表，不再使用此前新增 Compose 成绩卡片页。四指标分别取学校平均成绩绩点、加权平均成绩、有效学分、算术平均成绩，保持原始字符串。状态及本人排名以相同原生卡片风格加入列表头并随列表滚动。
- `activity_score_inquiry.xml` 只调整对应指标标签；`CourseScore.kt`、`SemesterGroup.kt`、`CourseViewHolder.kt`、`SemesterViewHolder.kt`、`CourseAdapter.kt`、`score_item_course.xml` 沿用原列表布局，增加原始学分文本、备注和序号；不补算学校未提供的逐课绩点，不把文字成绩转数字，不按同名课程覆盖。保留现有刷新、状态提示、本人会话和两个已确认成绩数据端点。
- 新增 `BrowserServiceLauncher.kt`，复用已有 AndroidX Browser 依赖。`SchoolServiceCatalog.kt` 的学工入口，以及 `ElectronicActivity.kt`（含原菜单和电费小组件路径）优先使用手机默认浏览器的 Custom Tabs，不强制 Chrome；不支持 Custom Tabs 的浏览器可按普通 VIEW 打开，没有浏览器时才回退 WebView。保持原 HTTPS URL，不复制 Cookie、登录表单或支付数据。
- `ServiceWebActivity.kt`、`ServiceWebPolicy.kt`：浏览器启动后安全处理空 WebView 栈生命周期；WebView 回退增加连接失败/空内容识别、原始入口浏览器按钮及学工官方 WebVPN 备用入口。只检查错误标志或空页面布尔值，不采集网页内容；保留证书校验，没有降级 HTTP 或放行混合脚本。
- 新增 `ScoreAndBrowserDeviceTest.kt`：原布局存在、文字成绩/学分格式、备注序号、默认浏览器 HTTPS 启动意图、无浏览器回退。新增 `ServiceWebsiteProbeTest.kt` 仅显式 `probePublicServices=true` 时探测公共网页，输出网络错误名/内容长度，不输出页面或表单数据。

证据与限制：本机 WebView 访问两个 HTTPS 网站均复现 `net::ERR_CONNECTION_RESET`，本机 curl 也握手失败；该证据只能代表本机环境，不能否定用户手机浏览器可访问。新的入口在模拟器实际点击电费后交给浏览器，浏览器首次启动界面未代用户接受条款，返回 APP 正常；尚未在用户手机验证目标页面加载、学工登录及真实充值支付。方案依据 Android 官方 Custom Tabs 文档： https://developer.android.com/develop/ui/views/layout/webapps/overview-of-android-custom-tabs 。

验证：完整 `clean` 且 `--no-build-cache` 构建成功，224 项 JVM 测试通过；`ScoreAndBrowserDeviceTest` 与 `ServiceNavigationSmokeTest` 共 5 项设备测试通过；实际打开成绩页确认原工具栏、四指标与排名卡布局正常，无新崩溃。Lint 完成，仍有原有 4 条 NewApi Error 和 1 条 NetworkSecurityConfig Fatal，无新增 Error/Fatal。诊断探测不计作网站业务成功验收。

固定交付：工作区 `05_Output/score-ui-web-fix-20261010/MyChuannong_preview9_score_ui_web_fix.apk`；SHA-256 `E2F71CC28DD15281A2DE153C6A26C68DA12AD358C51CD23EEE131D923A51BEF5`。同包名签名覆盖安装，无需卸载，未发布或上传。

## P. 2026-10-10：恢复电费和学工 APP 内移动网页（取代 O 节浏览器优先方案）

- 用户明确要求与学校官网一样在 APP 内承载；删除 BrowserServiceLauncher 和 prefersBrowser 自动外跳逻辑。
- ElectronicActivity 使用用户确认的 http://bd.bdfairy.cn/selectservice；SchoolServiceCatalog 的学工入口直接启动 ServiceWebActivity，地址为 https://xsgz.sicau.edu.cn/mobile/stuhall/studentindex。
- main/debug netconfig 仅增加 bd.bdfairy.cn 的 HTTP 例外，includeSubdomains=false；保持其他 HTTPS 证书验证。主配置的 loopback 也显式声明子域范围，更新对应 V1SourceBoundaryTest 断言。
- 保留原网页、默认移动 UA、原站 viewport（缺失才补充）、JS、DOM storage、滚动、安全区域、网页返回/关闭/刷新、加载和异常提示。仅用户触发支付应用或主动选浏览器时外跳。成绩原有 UI 未改动。
- 清理缓存完整构建生成 debug APK，修正旧网络配置字符串断言后 224 项 JVM 测试通过；5 项设备测试通过（原成绩布局、底栏/服务、学工原生路由、HTTP 域名范围）。
- 实际公网检查不通过业务验收：电费 HTTP 200，但网站原文为“操作失败／网络异常，请重新尝试！”，Android WebView 同样显示网站错误页，viewport 存在，未到达水电管理大厅。学工 Windows TLS 握手失败，Android WebView 返回 ERR_CONNECTION_CLOSED/ERR_CONNECTION_RESET。不能声称已显示用户截图大厅或完成登录/支付验证；未伪装微信环境、未跳过证书、未执行充值或绑定操作。
- 新 APK：05_Output/mobile-webview-fix-20261010/MyChuannong_preview9_mobile_webview.apk（相对工作区根目录）。SHA256 BA37AD8EC69D78BEE3F2FC24505A44783005AE66EFE1BC035E51093C1BFD12F2。该包修复内嵌入口，不表示网站业务已联调成功。
- Lint 执行完成：仍有 4 个已有 NewApi 错误（BindingUserActivity、SicauWebVpnActivity 各 2 个），无本次新增错误；原 netconfig 缺少 includeSubdomains 问题已消除。git diff --check 通过。公网诊断运行结束只代表诊断执行成功，不代表网站业务通过。

## Q. 学工接入现有学校容器（2026-10-10）

- 用户确认同一学工地址在安卓浏览器可用；本机公网失败不能用作安卓不支持网站的结论。
- SchoolServiceCatalog 学工入口改为与一网通办一致的 SchoolWebActivity，保留官方移动端地址。复用该容器加载前 SicauNativeAuth.exportToWebView 的既有认证状态同步及正常学校跳转，不复制外部浏览器 Cookie、不绕过学校登录。
- SafeUrl 的学校 HTTPS 白名单精确增加 xsgz.sicau.edu.cn，确保该容器不会拒绝入口及学校页返回学工的跳转；不放开 HTTP、任意子域或外部域名。
- SchoolWebPolicy 为学工只补缺失 viewport，保留网站原始移动端布局、脚本及登录流程。其余服务路由不变。
- 新增 StudentAffairsWebPolicyTest，更新设备路由测试，增加实际学校容器存活、可交互设置与尺寸检查。公网探针改测真实学校容器，可使用 studentOnly 参数单独检查学工。
- 已发现电脑 Clash TUN 参与 DNS/连接；学工请求在收到 HTML 前失败，尚无证据判定代理或服务器何者为根因。不得把此环境中的失败等同于用户手机不可访问，也不得把此次代码修正描述成已完成真实大厅验收。
- 验证：完整 clean/no-build-cache 构建成功；226 项 JVM 测试、6 项设备测试通过；git diff --check 通过。真实学工探针在当前模拟器仍返回 net::ERR_CONNECTION_RESET，未完成真实大厅/登录验收。新包位于工作区 05_Output/student-school-container-20261010/MyChuannong_preview9_student_school_container.apk，SHA256 F3736CFB3FD983AE803F7CC60CE8EB20899E4D90F5FF0FB4E170887E977B3231。
- Lint 已执行完成，仍为此前 BindingUserActivity 与 SicauWebVpnActivity 的 4 项 NewApi 错误，本次修改未新增 Lint 错误。

## R. 学工返回修正（2026-10-10）

- 用户确认学工已可进入，本轮只处理返回问题。SchoolWebActivity 的学工入口在大厅、认证页、错误页返回时直接 finish 回到 APP 原页面；子页面继续回退学校网页历史。如果网站把返回吞掉或重定向到原页面，再按返回退出。网页内新的触摸操作重置该保护，避免旧记录干扰后续浏览。左上角与系统返回共用处理。
- SchoolWebPolicy 新增大厅识别，忽略登录查询参数及末尾斜杠，不把子路径或非空 hash 业务页当作大厅。不改变其他服务返回策略。
- StudentAffairsWebPolicyTest 覆盖大厅 URL 变体；ScoreAndServiceDeviceTest 使用本地合成 HTML 和 history.pushState 模拟有历史的大厅返回，不读真实学校数据。227 项 JVM 测试、7 项设备测试通过，包括系统返回退出该历史场景。
- 输出：工作区 05_Output/student-back-fix-20261010/MyChuannong_preview9_student_back_fix.apk。用户手机上的真实登录及业务历史需实际回归；不能把合成页面测试当成真实学校端到端验证。
- 构建和 Lint 任务完成；仍为此前 4 个 NewApi 错误，无新增 Lint 错误。APK SHA256：088B44AFD87BCED75CC491CDE271D9E20A6311D3258FA3F8A101A64BA2495641。

## S. 课表、成绩持久化缓存（2026-10-10）

### 行为
- 页面先读本机数据；已有缓存不再按 48 小时自动刷新。后台同步和重新登录不会替换已有课表，打开考试安排也不会顺带覆盖课表。课表新增“刷新数据”，成绩保留原刷新按钮，只有明确刷新成功才替换缓存。
- 首次绑定成功读取的数据落盘；个人成绩单附 updatedAt。登录过期、网络失败、解析失败保留旧值；排名刷新不完整且已有完整成绩缓存时保留整份旧报告。移除旧令牌失效/重新绑定流程中删除学习缓存的行为。
- 成绩、课表元数据按学校账号隔离；Room 查询保留账号过滤。页面监听身份变化，清掉旧账号可见状态并校验异步结果的当前身份。在线教务身份与本地当前身份不一致时不擅自切换账号。
- APP 主动退出或注销清除学校缓存与绑定（沿用清理全部学校数据策略）；仅在线令牌失效不清。换手机平台账号也先清除前一平台账号的学校数据。
- 页面显示“当前为本地缓存数据”或“当前为最新获取的数据”及最后更新时间；没有时间戳的旧版缓存显示“未记录（旧版缓存）”，不编造时间。

### 存储与文件
- SchoolSyncEngine.kt：更新策略、身份锁、按账号元数据、首次成绩读取、失败保留及清理。
- ScoreCache.kt、SicauTranscriptParser.kt：账号隔离成绩持久化、时间戳、旧成绩缓存迁移。
- LearningCacheKeys.kt：Android Keystore 保护本地随机存储密钥；成绩及课表元数据使用加密 MMKV，无账号密码。
- EncryptedCourseStorage.kt、CoursesDataBase.kt、app/build.gradle.kts：SQLCipher 4.19.1 加密课表数据库。旧 Room 库复制到临时加密库并执行完整性检查，成功后切换；失败不删旧库。保留 Room 10/11/12 的既有升级路径，不执行旧版本破坏性降级重建。实现参考 SQLCipher 官方 Room 集成文档：https://github.com/sqlcipher/sqlcipher-android#sqlcipher-for-android-room-integration 。
- ScoreInquiryViewModel.kt、ScoreInquiryActivity.kt、TimeTableViewModel.kt、TimeTableUiState.kt、TimeTableActivity.kt、TimeTableComposeScreen.kt、LearningCacheStatus.kt：缓存优先、状态/时间显示、手动刷新与账号切换后的异步结果校验，保留原有课程和成绩呈现。
- StudentInfoManager.kt、CampusToolsApi.kt、AccountDataManager.kt、PlanetApplication.kt、OkHttpHelper.kt、UserStore.kt、ExamArrangementViewModel.kt：身份变化通知、显式退出清理、移除会话失败/绑定后清缓存，考试查询不覆盖已有学习数据。
- LearningCacheDeviceTest.kt、SicauPipelineStorageTest.kt：离线失败、刷新、账号隔离、退出清除、加密迁移和冷启动回归。新增仅供测试的 cacheTestSuffix 构建参数，在独立 .cachetest 包中执行删除数据的测试，避免影响现有 .debug 包。

### 验证及交付
- 标准 debug 完整 clean/no-build-cache 构建；227 项 JVM 测试通过。
- 独立测试包 17 项设备回归通过（缓存 3 项、课表原有存储链路 7 项、页面/导航 7 项）。另分两次 instrumentation 执行保存、force-stop 后新进程读取，课表和成绩均保留，2 项验证通过。
- 最终标准 debug 包安装后 7 项页面/导航冒烟测试通过。测试只用合成课程及账号，不记录真实个人数据。
- 安装包：工作区 05_Output/learning-cache-20261010/MyChuannong_preview9_persistent_learning_cache.apk。SHA256 C032CEC6666C799B51E77789EA84ACA4B06FC5D7DBE81E15A2C020F46A8FF737。
- 未执行真实学校账号的完整抓取；数据抓取沿用此前已接入的教务流程，本轮验证重点是成功/失败后本地存储与展示生命周期。
- 最终 Lint 执行完成：仅原有 BindingUserActivity / SicauWebVpnActivity 共 4 项 NewApi 错误，无本次新增错误；git diff --check 通过。网络失败测试采用注入失败结果，不冒充真实学校网络端到端验收。

## T. WebVPN 入口移至主要设置（2026-10-10）
- ProfileSettingsRoute.kt：在“我的 → 主要设置 → 绑定学号”下方加入“学校 WebVPN”，沿用原 SchoolWebActivity、原地址和设置行样式。
- SchoolServiceCatalog.kt：移除学校服务中的 WebVPN 和“学校门户”入口，保留“一网通办”。内部认证域名与登录流程不变。
- Rc2ProfileSettingsContractTest.kt：同步现有设置菜单顺序断言。
- 验证：assembleDebug 成功，227 项单元测试通过，git diff --check 通过；lintDebug 完成，仅原有 4 项 NewApi 错误。本轮未执行设备交互测试。
- 安装包：05_Output/settings-webvpn-20261010/MyChuannong_preview9_settings_webvpn.apk；SHA256 7734C1C1A62FA7895EE3BC193712330755D4DA7F39D8249B6F6930340DB968BC。

## U. 学校服务教务系统展示原始网页（2026-10-10）
- SchoolAcademicActivity.kt：替换原查询导航中间页，继承与学校官网相同的 ServiceWebActivity，直接展示 https://jiaowu.sicau.edu.cn/web/web/web/index.asp 。
- SchoolServiceCatalog.kt：教务系统入口地址与实际页面统一。学习栏目课表、成绩、考试入口及教务认证端点不变。
- 沿用网页容器的安全区域、默认移动 UA、宽视口、整页缩放、双指缩放、滚动、JS、网页历史返回、关闭、刷新和错误提示。该原站固定宽度，不强行注入设备宽度视口或重写页面布局。
- 本机访问原网址 HTTP 200；JVM 227 项测试通过，修改文件 diff --check 通过。本轮未进行手机网页视觉验收。
- 安装包：05_Output/academic-web-20261010/MyChuannong_preview9_academic_web.apk；SHA256 D1110A316BC095BB32F4C4D67A35AB7F9337917A8537CCAE7E4AA4A86F0DDA66。
- 构建及 lintDebug 执行完成，原有 4 项 NewApi 错误仍在 BindingUserActivity / SicauWebVpnActivity，无新增错误。

## V. 首页缓存绑定、教务网页及共享认证修复（2026-10-10）
### 修改
- OverviewRepository.kt：首页绩点与均分绑定当前账号的个人成绩单缓存，保留学校原始数值；明确区分今日无课与没有本学期缓存；不以在线登录状态作为本地展示条件。
- OverviewViewModel.kt / ScoreCache.kt：订阅身份、同步和成绩缓存变化，读取后校验账号版本；移除保留旧账号/旧日期 UI 内容的合并逻辑。自动同步保留既有学习缓存，手动刷新允许更新；成绩尚无缓存时显示获取状态。
- SicauNativeAuth.kt：优先采用现有 WebView 新 Cookie，防止旧原生快照覆盖；共享认证状态继续使用 Android Keystore 加密快照。退出统一清除 Chromium Cookie/网页存储，恢复会话等待清理结束，使用版本号关闭旧网页并防止迟到的保存回写。
- SchoolSyncEngine.kt：切换学校账号时清除上一账号网页会话，平台退出沿用统一清理入口。
- SchoolWebActivity.kt：共享学校 Cookie、支持用户触发的新窗口在 APP 内继续、空白内容提示、认证回到目标站首页后恢复原目标路径、后台暂停时保存旋转后的认证状态。
- SchoolServiceCatalog.kt：校园缴费使用网站公开的 CasLogin.aspx 统一认证入口（从官方登录页确认），不猜测 CAS service 参数。
- ServiceWebActivity.kt / ServiceWebPolicy.kt / SicauJiaowuSessionVault.kt：公共教务网页容器复用已有学校认证与本人教务会话；退出关闭旧页面；渲染进程退出后提供重新加载；允许官方 SPA 合法片段字符而不改变 URL 原值。
### 验证及限制
- 标准 debug clean/no-build-cache 构建，227 项 JVM 测试通过。隔离包运行首页缓存/原有缓存/认证轮换及退出/导航回归（14 项计数，其中 2 项仅供独立进程重启的门控用例未启用）；成功执行的 12 项通过。
- 模拟器实际访问教务公开网页：正文长度非零、未检测到 net::ERR 错误，截图已查看，原站导航、通知和登录区可见；固定宽度网页采用原始布局整页缩放。未复现用户之前的纯白页面；已补强会话及异常恢复。
- 新会话覆盖优先级与退出清理使用合成 Cookie 验证，不以此冒充本人在四个真实学校业务中的端到端免登录验收。真实统一认证、验证码及业务返回仍需本人登录验证；未绕过认证，也未保存真实个人页面或凭据。
- 过程中误把固定 applicationId 的 JVM 检查运行在隔离 .cachetest 包上，AppIdentityTest 因预期包名不同失败；切回正式 debug 变体后 227 项全部通过。
- 交付：05_Output/home-auth-fix-20261010/MyChuannong_preview9_home_auth_fix.apk；SHA256 33CF32E949542747A80EBB8C429D6FC9247B66F064342A0F0D606E54A9D65DA9。
- 最终 Lint 无新增错误，仅既有 BindingUserActivity / SicauWebVpnActivity 共 4 项 NewApi；git diff --check 通过。

## W. 首页四项成绩汇总（2026-10-10）
- OverviewRepository.kt：从当前账号个人成绩单缓存读取平均成绩绩点、有效学分、加权平均成绩、算术平均成绩，保持原始字符串；兼容“平均绩点”字段别名，缺失值显示 --，不采用截图数值。
- OverviewUiState.kt：成绩卡片模型增加四项汇总字段。
- OverviewScreen.kt：首页成绩卡片两行两列同时展示四项，沿用原主题与圆角、隐藏成绩开关、点击进入成绩查询及缓存时间提示；高度自适应。
- 验证：227 项 JVM 测试通过，修改文件 diff --check 通过；本轮未进行手机视觉验收。
- 交付：05_Output/home-score-summary-20261010/MyChuannong_preview9_home_score_summary.apk；SHA256 774AAFECB6052CED7DE2A1A50D0C147B8723D1863709C6BDF12DC05403668024。
- 构建与 lintDebug 执行完成，Lint 仅原有 4 项 NewApi 错误，无新增错误。

## X. 四模块统一认证复用完善（2026-10-11）
- 核对官方 https://auth.sicau.edu.cn/wui/assets/index-DwxyaMpm.js：cascallback=choose 触发官方已有登录复用逻辑。
- 新增 SchoolSsoNavigation.kt：仅对官方 HTTPS /wui/、存在 appid/service 且没有既定 casc 回调的登录地址，在已有认证 Cookie 时追加 choose；完整保留原始 service 和回调编码。不改写微信、错误或其他明确回调，不猜测服务注册信息。
- SchoolWebActivity.kt：四模块共用原有 Chromium Cookie 存储；一网通办优先进入已确认的官方 SSO 入口；完整加载及 SPA 路由支持已有会话尝试，单个认证地址只自动尝试一次。缴费成功后不再重新打开 CasLogin.aspx；学工大厅回到原目标最多自动执行一次，避免循环。
- SicauNativeAuth.kt：离开网页时以进程生命周期完成加密会话快照保存，避免 Activity 销毁取消持久化；保持退出清理屏障和版本保护。沿用既有 Android Keystore 加密、认证过期保留学习缓存策略。
- SchoolWebActivity.kt / ServiceWebActivity.kt：关闭 WebView 远程调试、消费网页控制台输出，避免官方脚本输出认证响应到日志。四个认证模块不再把可能含一次性票据的完整网页历史保存到未加密 Activity Bundle；恢复页面通过原模块入口和共享会话。
- SchoolSsoNavigationTest.kt：测试会话条件、原回调字符串不变、重复/特殊回调不覆盖、非官方源和异常路由不处理。
- 本轮未连接安卓设备，未执行真实账号四模块端到端免登录测试；官方身份选择、验证码和权限检查仍由学校处理，不代选授权账号。
- 最终验证：标准 debug 构建成功，229 项单元测试通过；Lint 无新增错误，仅既有 4 项 NewApi。开发中两次 Lint Kotlin 分析器内部异常，停止源码调整后重新执行全部检查成功。
- 安装包：05_Output/unified-sso-20261011/MyChuannong_preview9_unified_sso.apk；SHA256 20AD742F79CA7E7B04162C1E010E295F37F8DAE2D91251B1063328E8C0E2624F。

## Y. 2026-10-11 用户登录后的模拟器实测

- 用户亲自在官方川农 e 页面完成登录；未由工具输入凭据或处理验证码。
- 标准 debug APK：统一认证完成后进入学校页面；一网通办和校园缴费均直接显示当前账号页面，没有再次输入密码。安装返回修复版并重新启动进程后，两项仍能直接进入。
- 学工系统两次返回 net::ERR_CONNECTION_RESET，未到认证回调，故不能声称其真实 SSO 验收通过。尝试启动模拟器浏览器做同设备对照的 ADB 操作被自动审批 blocked by policy 拦截，未绕过。
- 实测一网通办存在返回被网页重定向到当前页面的问题；SchoolWebActivity 将重复返回保护从学工扩展到其他学校网页。首次返回被重定向后，第二次返回已实际回到服务栏目。SchoolWebPolicy 补充识别 CasLogin.aspx，保留正常业务页历史。
- 修改文件：SchoolWebActivity.kt、SchoolWebPolicy.kt、StudentAffairsWebPolicyTest.kt、本记录。230 项 JVM 测试通过，assembleDebug 成功，lintDebug 完成但仍报告既有 4 项 NewApi 错误；日志 build-sso-device-back.log。未执行真实账号退出以免删除用户缓存，未发起任何支付。
- 新包：05_Output/unified-sso-20261011/MyChuannong_preview9_unified_sso_verified.apk（位于工作区输出目录），已覆盖安装至 emulator-5554，保留账号数据。私人页面截图只用于临时核验，不放入仓库或输出交付物。

## Z. 2026-10-11 学工模拟器白屏复核

- 当前连接的模拟器 Android 14，WebView 146.0.7680.119；原包实际先显示白屏，随后 ERR_CONNECTION_CLOSED。电脑 curl 对公开学工入口也在 TLS 握手阶段失败。手机可用由用户报告，不能以此推断同网络对照成功，也不能声称模拟器网络已修复。
- SchoolWebActivity.kt：加载状态改为 Compose 可观察状态；网络失败使用独立原生错误区域，隐藏 WebView 的系统错误文档；保留重新加载及返回按钮；内部 chrome-error 文档不重置错误；兼容旧错误回调。不修改学校地址、认证、User-Agent 或证书校验。
- 构建、单元测试通过，lint 保留既有问题。build-student-loading.log。已覆盖安装保留用户数据，并实测失败提示与重试后失败提示正常，网络访问仍失败。
- 输出：工作区 05_Output/unified-sso-20261011/MyChuannong_preview9_web_error.apk。

## AA. 2026-10-11 校园 WebVPN 优先引导与会话复用

- 在工作区现有 K–Z 轮未提交修改上增量实施，保留成绩缓存、共享统一认证、学工错误显示等成果。没有回滚或把先前其他修改冒充本次新增。
- MainActivity 增加首页可见 WebVPN 状态及连接按钮；隐私同意后首次创建首页执行真实原生通道检查。没有有效通道时自动进入官方认证页；返回后不会循环强制弹出。可选择暂不连接，继续查看本机缓存。
- SicauWebVpn 增加串行通道检查，成功结果最多短暂复用30秒；网络变化重置状态，下一次实际检查以服务响应为准。退出取消待完成检查、清除路由与状态，利用既有认证版本屏障防止迟到结果标记已连接。
- SicauWebVpnActivity 复用官方 Chromium Cookie；启动时先恢复既有统一认证存储并检查通道。官方门户页面及SPA跳转后自动读取门户实际资源链接，以原生教务登录页验证通道，再返回调用页。没有模拟点击、自动提交密码、跳过验证码或伪造登录成功；不保存密码。VPN通道成功不代表教务本人绑定成功。
- 官方资源发现保留有上限重读；继续登录/重新连接可手动重试。错误显示保留证书检查和超时，不把网络失败当作密码错误。关闭WebView远程调试、消费网页控制台消息，避免学校脚本认证内容进入日志。
- 学校网页与公共教务容器在进入前检查WebVPN，需要认证时使用Activity结果回调继续原目标。已有官方映射时用代理；未发现映射时保留原官方入口，不生成代理路径。将学工与统一认证加入门户可发现资源范围，但不声称实际门户一定提供这些映射。浏览中回到官方WebVPN认证时，完成后有上限返回原页面。
- SicauNativeAuth.clear 是现有退出/切换公共路径，现在同时清理 WebVPN 路由/状态；既有Cookie/网页存储及Keystore会话清理继续保留。不会把统一认证直接域Cookie复制到代理域；是否单点登录仍由学校实际回调决定。
- 模拟器 Android14 / emulator-5554 覆盖安装后COLD启动，实际自动进入「连接学校WebVPN」，显示官方「川农e认证」及密码/SMS入口，无需查找设置。可见「暂不连接，查看本机缓存」。未清除用户真实缓存/会话。
- 新增 WebVpnPriorityDeviceTest：隔离 .validation 包验证无路由要求认证、退出同时清除VPN及统一认证合成Cookie和路由/状态。与原 SicauWebVpnCookieDeviceTest 合计2项通过，不使用真实凭据、不联网学校。
- 本轮230项JVM单元测试通过。用户随后完成官方登录：实际自动返回首页显示「校园 WebVPN：已连接」；force-stop后COLD启动无需凭据仍已连接，原生日志新增 WEBVPN_NATIVE_LOGIN_PAGE verified=true。覆盖安装后同样已连接。教务本人会话另报SESSION_EXPIRED，正确区分VPN与教务会话，不声称三类数据已重新刷新。
- 进入教务、一网通办、校园缴费未再次弹出VPN登录；一网通办未出现密码框或网络错误。只验收页面进入和VPN免重复输入，不声称五类服务全部请求均已走代理；学工、统一认证此次未逐一验证。真实长时间过期/撤销、切换账号、官方MFA及所有资源代理范围仍待验收。
- 当前网络是否真实校外未重新确认；没有真机结果。用户完成认证后再覆盖安装最终包，保留缓存与会话。
- 当前存在多轮混合未提交改动，本轮未整体提交或推送；源码保存在原工作区。交付目录：05_Output/vpn-priority-20261011，包含APK与SHA256校验。
- 最终标准debug构建、230项单元测试通过；Lint最终报告0项Error（旧绑定页API26自动填充设置已加版本守卫）。APK SHA-256：3a078ca1d0ac06ee1b8bd7a131d31594acacdd142edfcf30248921734142f5dc。

## 电费微信授权容器接续（2026-10-11）

- 修改 ServiceWebActivity.kt，新增 ElectricAuthorizationPolicy.kt 和 ElectricAuthorizationPolicyTest.kt。
- 内嵌 WebView 优先：保留网站原始 OAuth URL、回调、JS、DOM storage、站点 Cookie 和支付过程；电费容器允许授权所需第三方 Cookie，仍由 WebView 管理并 flush，不导出第三方会话或保存密码。禁止 JS 控制台输出、关闭远程调试沿用。
- 仅识别精确的官方微信授权端点和 bd.bdfairy.cn 的授权受限提示；明确不支持 WebView 或官方授权主文档加载失败时，自动一次性启动 Custom Tab 打开原始电费入口，以外部独立会话重新发起授权。可手动重试；不伪造微信 deep link 或 native callback。真实官方授权页发出的 weixin 链接交给系统客户端。
- 外部返回只提示继续使用，不冒充授权/支付成功，不复制外部 Cookie。支付结果仍由网站展示。电费的 current_page 和 WebView 历史不再写入 Activity Bundle，防止保存授权码/支付参数。
- 公开电费入口当时返回 HTTP 200 但实际内容是网站的“网络异常，请重新尝试”错误页，未获得官方 APP 回调或 SDK 接口；真实授权与支付未验收。未将该匿名响应的会话用于 APP。
- assembleDebug、232 JVM tests 成功；lintDebug 完成，报告以 build/reports 为准。增量编译因路径别名失效后由编译器自动完整编译成功，日志 build-electric-auth.log。
- APK：工作区 05_Output/unified-sso-20261011/MyChuannong_preview9_electric_auth.apk，已覆盖安装 emulator-5554，保留本地数据。

## 电费站点业务错误页补充处理（2026-10-11）

用户手机截图确认仍在 APP 内，显示第三方“操作失败／网络异常”，没有微信授权跳转。不能据此宣称授权失败或伪造 OAuth URL。
- ElectricAuthorizationPolicy.kt 针对已核对的 #errorApp .error-title/.error-message 结构识别此错误，返回状态而非页面数据。
- ServiceWebActivity.kt 在完成加载及延迟 1.5/5 秒检查，检查时确认仍是原 WebView 和原 URL；提供真实失败说明及外部打开入口，手动外部打开不再被描述为已进入授权。网络业务错误不自动触发 OAuth 回退或支付。
- 6 个 JavaScript 状态探针检查通过（包括正常业务文字、余额不足不误判），232 JVM tests 通过；构建及 lint 完成。真实微信授权仍未完成验收。
- 输出 05_Output/unified-sso-20261011/MyChuannong_preview9_electric_error_retry.apk，已安装到模拟器，手机需要覆盖安装此包。

## 2026-10-11 校内服务登录调整

- 我的主要设置入口改为“校内服务登录”，使用官方川农e认证入口；显示用户指定说明，官方验证码及登录交互保留，未重建密码表单。
- 移除首页每次启动的访问通道检查/登录弹出，以及 SchoolWebActivity、ServiceWebActivity 的强制前置通道登录；业务系统自行决定是否需要官方认证。
- 两类业务容器与校内访问页面统一使用已有官方 SSO 会话选择机制，保留学校 service/callback 参数，不复制跨域会话、不伪造登录。
- 登录入口完成官方一网通办落地后返回设置；原业务入口保留自身认证回调。简化校内访问页面按钮及错误提示。
- 继续使用现有加密学校会话存储和主动退出清理逻辑；未改变学习缓存策略。
- 校内教务本人身份核验仍遵循学校实际要求，不能宣称统一认证能替代学校独立密码验证。
- 新增 SSO 返回地址边界测试；233 项 JVM 测试通过。模拟器已安装新版，实际确认“校内服务登录”入口、简洁说明和川农e官方认证页面能够显示。当前显示学校登录页，未代填账号密码，因此本次未完成新的真实账号五服务互通验收。
- 清理构建后重新安装，解决模拟器运行旧 UI 的现象。截图和真实个人数据不写入仓库。

## 2026-10-11 手机账号注册失败修正

- 确认交付 Debug BuildConfig 的账号服务为设备回环地址，且用户确认尚无已配置服务器。不能把本机开发服务当作手机可用后台。
- ServiceEndpointPolicy 新增账号端点检查：排除回环、开发模拟器地址、占位地址及非 HTTPS 地址；不把配置存在当作服务真实可用。
- AccountGateScreen 在尚未配置账号服务时不展示注册/密码表单，显示简洁说明，并提供进入首页及官方校内服务登录。未绕过平台账号权限。
- CampusToolsSession 的注册及登录在请求前检查端点，不向当前回环配置提交账号密码。
- ImeCompatibleTextField 在 setSingleLine 后重新应用密码遮挡，修复重组时密码明文显示；关闭密码 View 状态保存；AccountGateScreen 密码改为仅内存状态，切换模式或成功后清空。
- 不使用用户截图中的个人信息或密码测试，不将截图写入仓库。真实平台注册仍需后续部署并配置 HTTPS 后台。
- 验证：assembleDebug/testDebugUnitTest/lintDebug 成功，235 项 JVM 测试通过，Lint 0 错误；模拟器实测账号入口显示未接入服务说明及两个操作入口。

## 2026-10-13 绑定学号先选择网络环境

- BindingUserActivity / activity_binding_user.xml：进入绑定页先询问校园网或非校园网，默认不选；替换原“校园网直连（诊断）”复选框。校园网直接展示学号与教务密码并使用 Direct 路由；非校园网先显示“校园 VPN 绑定”，验证成功后展开教务登录表单。
- NativeBindingViewModel：显式管理网络选择、VPN 绑定中/成功状态。已有代理地址不代表本次通道已验证；未选网络、未完成 VPN、取消或失效时阻止非校园网教务登录。页面重建保留当前 ViewModel 状态；取消后的迟到结果不会标记成功。VPN 成功不会自动提交教务登录或冒充学号绑定成功。
- SicauWebVpnActivity：绑定流程的页面标题改为“校园 VPN 绑定”，说明改为非校园网的前置认证步骤；沿用官方认证和原生通道校验，回调后持续显示用户要求的“已绑定校园 VPN，请继续登录”。通用校内服务 SSO 入口保持其独立用途。
- 教务登录继续调用既有本人身份核验和课表、成绩、考试同步流程；校园网密码提示改为“请输入教务密码”。未改学校协议、会话持久化和同步解析。
- 验证：标准 debug assembleDebug / testDebugUnitTest / lintDebug 成功，235 项 JVM 测试全部通过，Lint 0 错误；APK v2 签名通过，包名和证书与上一版一致。新增 BindingNetworkDeviceTest 在独立 .bindingtest 包运行 4 项测试，全部通过；覆盖分支切换、密码校验、取消、模拟成功回调及页面重建、缓存地址不能替代认证、路由失效、迟到回调。
- 模拟器 Android 16，已检查四种状态截图；截图仅在 05_Output。本次成功提示使用合成回调测试，没有登录真实学校账号或抓取真实学习数据，不宣称已完成校园网/VPN 真实账号端到端验收。
- 交付：05_Output/binding-network-20261013/MyChuannong_preview9_binding_network.apk；SHA-256：F9F471B65AAC29068ED5098BE63E70444F528E1DA518F875625228FD744CD9CC。保留工作区已有未提交改动，未发布 Release 或公开 APK。

## 2026-10-13 校园网络环境绑定与 VPN 优先

- “我的”原“校内服务登录”改为“校园网络环境绑定”，转入新增 CampusNetworkActivity 原生状态页。进入、返回认证页、网络变化及学校会话变化时检查实际连接；已验证 VPN 显示“你现在已经连接校园 VPN”，直连探测成功显示“当前校园网直连通道可用”。检查中及未确认连接分别显示真实状态，不用 Wi-Fi 类型、用户选择或保存的代理地址推断已连接。
- 状态页推荐绑定校园 VPN；官方 VPN 认证完成后返回状态页重新校验，提供继续教务登录入口。直连可用时保留备用按钮。新页面沿用浅灰背景、白色圆角卡片及绿色主按钮。
- 绑定学号保留上一轮网络选择；校园网同样推荐绑定 VPN。选择网络后检查已有 VPN，会话有效时复用；无论选择校园网还是非校园网，经过验证的 VPN 路由均优先。提交教务登录前再次检查 VPN；通道失效时明确提示，不在同一次提交中静默降级到直连。
- SicauWebVpn.check 增加强制检查参数及网络版本校验；网络变化使状态失效，迟到的旧网络结果不能作为新网络下的 VPN 可用证据。官方认证页面验证结果同样检查网络版本。
- VPN 通道与教务账号会话仍是两步：完成 VPN 后需继续登录教务系统；不会复制直连 Cookie 到 VPN 域、静默迁移旧的直连教务会话或代填学校凭据。后续教务同步沿用本次登录并核验后的会话路由。
- 验证：243 项 JVM 测试通过，最终 assembleDebug / lintDebug 成功，Lint 0 错误。隔离 .bindingtest 包中 5 项绑定流程、1 项三态显示/按钮、1 项新 Activity 打开/重建/返回测试共 7 项通过；样式微调后重复核对后两项。截图仅保留在 05_Output，VPN 成功和校园网状态为合成测试状态；没有真实学校账号校内/校外端到端验收。
- 交付：05_Output/campus-network-20261013/MyChuannong_preview9_campus_network.apk；SHA-256：5F4EE77C0AE474DFFA45FEE807599ECCDBC093E7A2224C90DA092351F053C8CE。保留此前未提交修改，未发布 Release 或公开 APK。

## 2026-10-13 绑定状态、VPN 恢复、课表刷新与离线提醒

- 本节替代前两轮“默认先选择网络环境”的流程。新绑定默认先连接校园 VPN，再展开教务账号输入；校园网直连作为次级备用。已核验的学号进入绑定页先显示“学校账号已绑定”，提供查看课表、主动重新登录和解绑。校园网络状态页的继续登录按钮显式进入重新认证表单。
- 移除课表页面因在线会话过期而自动跳绑定页的监听，缓存先展示；在线会话与已绑定身份保持分离。个人设置显示“已绑定学号”。仅从已认证、匹配本人学号的教务个人资料表读取姓名，本机加密保存；首页和个人页显示姓氏加“同学”，支持常见复姓，未绑定或尚未读到姓名使用默认称呼。
- WebVpnSessionStore 使用 AndroidX WebKit 的完整 Cookie 属性读取接口，保存校园 VPN 门户和实际教务代理路径的加密快照，进程启动后恢复缺失会话。保留 Path、Domain、Secure、HttpOnly、SameSite 和原始有效期，Max-Age 转为绝对时间，现有浏览器新会话优先。真实通道和本人身份仍须校验；解绑清理快照并用会话版本阻止迟到写入。旧 WebView 不支持完整属性接口时继续使用 Chromium 的原有持久化，不从请求头推测 Cookie 属性。未延长学校服务端有效期，也未保存密码或添加后台账户保活请求。
- 上课提醒完全使用本机缓存。修复提醒时间已过但课程未开始时重排丢弃未送达闹钟的问题，补发并记录已送达状态去重。应用启动恢复提醒，并以无网络约束的每日 WorkManager 任务续排未来 14 天窗口。开启提醒时引导通知授权；缺通知渠道/精确闹钟权限时明确展示待设置或可能延迟，不能误报正常。通知接收器保持本地课程、日期、开关和有效时段核对。
- 课表右上角直接显示“刷新”和页内进度，下方灰字显示最近更新时间；刷新失败保留原课程和日期。删除旧刷新选项弹窗、图片选择入口和图片预览，保留历史本地课程及学校网页课表的核对能力。当天/下一节桌面小组件、深色资源、添加预览统一绿色；已绑定但暂时无课表的小组件点击后进入课表页。
- 用户认可的绿色主题、网络检查和成功反馈风格已写入 AGENTS.md。当前没有云端记忆写入接口，本次只记录项目规范，未声称已保存云端记忆。
- 验证：247 项 JVM 测试全部通过；隔离 .bindingtest 包的 5 项绑定流程、1 项网络状态、4 项新流程/课表/真实 AlarmManager 通知/绿色 RemoteViews、2 项分进程 VPN 恢复测试，共 12 项设备测试通过。冷恢复测试先保存合成 Cookie，再清空 Chromium Cookie 并 force-stop，然后在另一进程恢复，核对作用域、HttpOnly/SameSite、姓名和解绑清理。截图仅保留于 05_Output。未使用真实账号凭据，未完成学校服务端长时间会话或用户真机厂商省电策略验收。
- 标准 debug 包名 com.mychuannong.app.debug，版本 0.1.0-preview.9；签名验证通过，证书与前版一致。独立测试包及测试数据已卸载，未修改用户原安装包的数据。
- 交付：05_Output/student-flow-20261013/MyChuannong_preview9_student_flow.apk；SHA-256：255A60D78A953CE9031E10991C8F799996655C38BA83328DA3E4E929F5ED7D96。保留原工作区未提交修改，未发布 Release 或公开 APK。

## 2026-10-13 上传 GitHub 前源码清理与审计

- 已验收的 student_flow APK 原样保留；修改前 Android 946 个候选文件和 Backend 36 个文件已在本机留存快照及哈希。Android 的当前功能包含未提交改动，不能只用旧 HEAD 复现最新包。
- 修复考试安排明文共享缓存：改用加密、按本人账号分区的缓存，解绑清理所有考试分区，旧无归属记录不迁入新账号。清学习缓存改为挂起调用，避免主线程等待同步锁造成阻塞。
- 升级 jsoup 1.23.2、cryptography 50.0.0；删除未使用且导出 Provider 的 CodeLocator 调试依赖、原始业务日志及旧图片课表导入代码；补齐云备份/设备迁移排除规则，恢复 Lint 错误阻断，移除旧学校 HTTP 例外。保留电费 HTTP 兼容风险并在报告中明确列出。
- 后端已从其现有私有仓库只读取回；修复测试库保护和未经核验的固定签名配置，但备份恢复模块仍缺失。完整 unittest 为 35 通过、8 失败、1 导入错误，另 17 项生产测试未加载；没有删除或绕过失败测试。
- Android 最终标准 Debug 构建和 247 项 JVM 测试通过；Debug/Release Lint 均 0 错误，分别 1496/1495 条警告。合成数据设备回归 9 项通过，隔离测试包已卸载。最终 267 项 Maven 运行时依赖及 5 项 Python 锁定依赖查询无已知漏洞命中；扫描范围和限制见完整报告。
- 审计候选 APK：`05_Output/source-audit-20261013/MyChuannong_preview9_audit_candidate.apk`，SHA-256 `e30fe05c437edaf46212fa21de3a01ee8070c16145090f416a8d53cbcd5984ea`。仍为内部 Debug、preview.9，同验收包签名；没有覆盖用户原包或安装到用户数据所在包中。
- 本轮没有提交、push、创建 PR/Tag/Release 或生产部署。后续上传前需审查实际暂存文件，保留两个独立私有仓库；禁止上传本地 APK、数据库、学校会话及原始测试证据。
