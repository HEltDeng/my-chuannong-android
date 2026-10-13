# 我的川农 · 上传 GitHub 前源码审计报告

日期：2026-10-13。对象：已验收 preview.9 对应工作区，以及取回的独立 Backend/Admin 源码。阶段：内部预览。

## 结论

本轮已完成全量源码文件盘点、当前源码与 Git 历史密钥扫描、依赖漏洞查询、Android Lint、后端 Bandit 扫描，以及认证、缓存、网络、权限、发布与备份等关键链路审查。已直接修复确认且范围明确的问题，保留了修改前源码快照。

**可以准备私有源码归档，但不能把前后端标成“全部测试通过”或“可直接上线”。** Android 的考试缓存跨账号问题已修复；后端仍有缺失模块和未通过的既有测试，详见下文。当前没有确认的真实密钥泄漏；这不等于对所有未知漏洞作出保证。

本轮没有执行 Git 提交、push、创建 PR/Tag/Release、公开 APK 或生产部署。两个既有 GitHub 仓库均经只读查询确认是 private：

- [Android：HEltDeng/my-chuannong-android](https://github.com/HEltDeng/my-chuannong-android)
- [Backend：HEltDeng/my-chuannong-backend](https://github.com/HEltDeng/my-chuannong-backend)

## 1. 范围与版本对应关系

| 对象 | 审计基线 |
| --- | --- |
| 已验收 APK | `MyChuannong_preview9_student_flow.apk`，66,905,358 字节 |
| APK SHA-256 | `255a60d78a953ce9031e10991c8f799996655c38ba83328da3e4e929f5ed7d96` |
| Android HEAD | `b378526a74b8d5f5b784de6cc3e6b79d4bed1b4a`，`codex/restore-v105-adaptation` |
| Android 清洗前工作区 | 946 个候选源文件；包括 531 个 Kotlin、13 个 Java、230 个 XML，以及资源、构建与说明文件 |
| Backend HEAD | `a6001495ef64a6d95db35980a86c92b342d53743`，同名分支，从原有私有仓库只读取回 |
| Backend 清洗前工作区 | 36 个文件；包含 11 个 Python 文件、管理界面的 JS/HTML/CSS、SQL schema、依赖和文档 |
| Git 历史范围 | Android 本地全部引用共 11 个提交、1,070 个唯一文件对象；Backend 共 2 个提交、50 个唯一文件对象 |

Android 最新功能存在大量此前未提交改动，**仅检出上述 HEAD 不能复现已验收包**。本轮先保存完整候选源文件快照与逐文件 SHA-256，再进行审计修复；未将已有改动误作本轮新增工作。快照清单摘要：

- Android：`e2da159f11571cfe33a4b78290d4566b4e370bcd1000c7fbd4cfdf36c2eab40b`
- Backend：`f6319297eb6da52721c2a53d202136ca0f03cf5a88b1a18b65670090de58ab17`

快照是在本轮开始时生成的源码证据，不是对历史 APK 可逐字节重现的证明。清洗后的代码与已验收 APK 有明确差异；原 APK 未覆盖，审计构建另存为候选包。最终候选包、测试摘要和变化清单见 [审计证据摘要](evidence/audit-summary.json)。

审计候选包为 `MyChuannong_preview9_audit_candidate.apk`，64,179,855 字节，SHA-256 `e30fe05c437edaf46212fa21de3a01ee8070c16145090f416a8d53cbcd5984ea`。版本仍为 `0.1.0-preview.9 / 9`，包名 `com.mychuannong.app.debug`，签名与原验收版一致。它仍是内部 Debug 包，保留的 LeakCanary 等调试工具不属于正式发布配置，不能直接作为生产 Release。

## 2. 前端和后端实际状态

Android 是原生 Kotlin/Java 客户端，含 `app`、`CP_Common` 和 `baselineprofile` 模块，不是单纯网页外壳。教务认证、VPN 通道、课表、成绩与考试请求由用户手机直接访问学校系统，数据在本机解析和缓存。

Backend 已有实现，并非尚未编写：它提供平台账号、管理员角色、公告、动态配置、版本元数据及自建内容接口。管理端是 HTML/CSS/JavaScript，数据层是 SQLite。**存在生产入口文件不代表已经部署或验收成功。** 当前 APK 没有可用的 HTTPS 平台账号服务配置，学校登录与课表不依赖该后端。

本轮未访问真实学校账号、未上传学校资料、未复制运营数据库，也未修改其他产品。自动化设备检查使用独立 `.bindingtest` 包和合成数据，测试后已卸载隔离包。

## 3. 已确认并修复的问题

优先级含义：P1 为隐私或交付关键问题；P2 为应修复的安全/可靠性问题；P3 为维护改进。它们是本项目的处置顺序，不等同于上游漏洞 CVSS 分数。

| 编号 / 优先级 | 发现与影响 | 本轮处理与证据 |
| --- | --- | --- |
| A01 / P1 | 考试安排使用明文、无账号归属的共享 `content_cache/exams`。A 账号写入后切到 B，B 可能读到 A 的考试数据；解绑也未独立清理此缓存。 | `ExamArrangementCache.kt` 改为设备密钥保护的 MMKV，并按账号摘要分区；解绑/清缓存清除全部考试分区。旧记录无法确认归属，丢弃后重新同步。设备测试覆盖换号、切回、解绑与旧缓存拒收。 |
| A02 / P2 | 清除学习缓存从主线程调用 `runBlocking`；若并行同步持有锁并等待 WebView 主线程回调，会形成阻塞。 | `AccountDataManager.clearLocalLearningData` 改为挂起函数，UI 协程调用；移除重复清理。设备回归构造“同步持锁且需要主线程”场景并通过。 |
| A03 / P2 | Android 使用的 jsoup 1.22.2 命中 2 项漏洞通告。 | 升级 1.23.2，重新运行解析单元测试、构建与依赖查询。项目当前未发现使用通告所述自定义 Safelist/XML 解析路径，不能把依赖命中直接等同于已被利用。 |
| A04 / P2 | 后端 cryptography 46.0.7 命中 4 项去重后的上游通告；pip-audit 原始输出有重复项，共 7 条。 | 升级至 50.0.0，同步依赖范围/锁定文件，扫描不再命中。后端使用 Fernet，未发现 X.509 验证或 PKCS#7 解密调用；现有通过的账号与加密相关 API 测试未出现新增失败。 |
| A05 / P2 | 宿舍信息、帖子对象、图片 URL 等业务数据被写入 logcat，包含内部 Debug 构建。 | 删除相关原始内容日志；公共协程异常日志只保留异常类型。保留不含凭据的固定状态、计数和诊断事件。 |
| A06 / P2 | `allowBackup=false` 之外，备份和数据迁移 XML 仍是空模板；部分 Android 厂商的换机迁移不只由该标志控制。 | 明确排除云备份和设备迁移中的 root/file/database/sharedpref/external 及设备保护存储域。资源编译与 Lint 复核通过；未进行跨厂商实机迁移试验。 |
| A07 / P2 | 验收 Debug 包合并清单中，未使用的 CodeLocator 依赖带入了 `exported=true` 且无权限保护的 Provider。 | 删除两个 CodeLocator 调试依赖及版本目录条目；最终合并清单复核。普通业务代码无相关调用。 |
| A08 / P2 | 后端保留未经本产品核验的固定正式签名指纹；测试库保护只拦截旧数据库文件名。 | 正式证书指纹改为显式外部配置，缺失时拒绝发布；测试保护同时覆盖旧库、当前 `v105` 库和配置中的实际库。新增 2 项后端回归通过。 |
| A09 / P2 | Lint 配置允许错误后构建继续，且关闭发布检查；Debug 网络配置残留其他学校的 HTTP 例外。 | 开启 `abortOnError` / `checkReleaseBuilds`；移除两个旧学校域名例外；保留必要本地回环与现行电费兼容例外。 |
| A10 / P3 | 旧课表图片导入函数/探针、不可达错误弹窗、重复仓库条目、旧产品后台标识及过时说明残留。 | 移除旧图片导入实现和唯一旧探针，保留学校 HTML 核对与官方校历图片解析；整理依赖源，更新后台品牌、sessionStorage 名称、远程链接 Schema、README 和安全边界。扩充私钥/数据库/备份等 Git 排除规则。 |

A01 的升级影响：旧共享考试缓存会失效一次，联网同步后重新取得；原有课表、成绩和绑定身份不因此删除。A06 的预期行为是换设备重新登录并获取学校数据，不迁移登录 Cookie。

上游依据：[jsoup Safelist 通告](https://github.com/jhy/jsoup/security/advisories/GHSA-pmhh-3w7g-xqp8)、[jsoup 1.23.2 发布说明](https://jsoup.org/news/release-1.23.2)、[cryptography OpenSSL 通告](https://github.com/pyca/cryptography/security/advisories/GHSA-537c-gmf6-5ccf)、[PKCS#7 通告](https://github.com/pyca/cryptography/security/advisories/GHSA-g6cj-pr64-35w5)、[DNS 约束通告](https://github.com/pyca/cryptography/security/advisories/GHSA-m2h6-j472-rp4c)、[证书链耗时通告](https://github.com/pyca/cryptography/security/advisories/GHSA-jwv3-5hgf-82ww)。备份规则依据 [Android 官方备份文档](https://developer.android.com/identity/data/autobackup)。

## 4. 尚未解决、不能省略的项目

| 编号 / 优先级 | 当前证据 | 后续要求 |
| --- | --- | --- |
| U01 / P1：后端交付不完整 | `tests/test_production.py:31` 引用的 `scripts/database_tools.py` 缺失，导致该文件中 17 项测试无法加载。既有 API 测试另有 8 项失败。 | 补齐并审查备份/恢复实现；用独立合成夹具更新旧场地、签名与版本测试，再运行完整套件。当前不能称为完整可运行的生产交付。 |
| U02 / P2：电费 HTTP 例外 | 主配置与 Debug 均允许精确主机 `bd.bdfairy.cn` 使用 HTTP。现有兼容实现需要它。 | 联系服务提供方确认 HTTPS 或官方安全入口；在落实替代方案前明确保留风险。转到系统浏览器不等于传输已加密。 |
| U03 / P2：更新下载边界 | 后端 `verify_release_artifact` 接受管理员提供的下载地址、跟随重定向并读到结束；Android 下载也在完成后才核对预期大小。 | 上线前增加下载目标/重定向限制、内网目标防护、流式大小上限及对应对抗测试。当前发布默认被未配置签名阻断，未对公网进行攻击验证。 |
| U04 / P2：可重现构建不足 | Gradle wrapper 已有 SHA-256 校验，但缺少完整依赖锁定和制品校验元数据；仍依赖外部 Maven/JitPack/镜像。Python 版本锁定尚无逐制品 hash。 | 私有归档可保留当前版本清单；正式发布前补制品校验、锁定和干净环境重建。依赖扫描不证明仓库或制品绝对可信。 |
| U05 / P3：技术债 | Lint 仍有大量警告，主要是未使用资源、硬编码文字、无障碍说明、过时 API 等；后端 `server.py` 约 7,700 行。 | 分批处理。资源可能通过动态皮肤/反射使用，不能为了归零盲删；关键业务拆分应另有回归覆盖。最终数量见证据摘要。 |
| U06 / 发布前核对：许可与素材 | Android 保留 MIT 原版权；第三方声明与继承来源存在，但没有完整逐项许可证/素材授权清单。 | 保持现有私有范围；公开再分发之前核对并补齐声明，不能因源码在 GitHub 可见就推定拥有所有授权。 |

后端 8 个失败按原因分组：6 个依赖旧 `badminton` 场地/库存或开放状态（返回 404/400/503），2 个依赖旧发布配置（返回 409）。未为通过测试加入旧运营数据、开放未配置功能或关闭发布保护。

## 5. 实际验证结果

| 检查 | 本轮结果与范围 |
| --- | --- |
| 密钥扫描 | Gitleaks 8.30.1：两个源码快照及两个仓库全部本地 Git 引用均未检出；清洗后候选源码再次扫描。原始结果已脱敏。 |
| 隐私与文件排除检查 | 扫描候选文件和历史对象，未发现 JKS/数据库/私钥等禁止归档制品；用户提供的姓名/学号未在源文件或历史对象中命中。手机号形态命中 13 处，均复核为单元测试样例。此类模式扫描不能穷举所有个人数据。 |
| Android 依赖 | 通过 Gradle 实际解析 Debug/Release runtime，再查询 OSV Maven 数据；最终去重 267 项（Debug 267 / Release 246），升级和移除调试依赖后无命中。只向漏洞服务发送公开依赖坐标，不发送私有源码。 |
| Backend 依赖 | pip-audit 2.10.1 检查 `requirements.lock.txt` 的 5 个锁定组件；升级后无已知漏洞命中。 |
| Android JVM | 默认 Debug 变体 247 项测试通过，0 失败、0 错误、0 跳过。 |
| Android 设备 | Android 16 模拟器，9 项通过：新增缓存隔离/旧数据拒收/并发清理 3 项；绑定与离线课表、真实系统本地通知、小组件 4 项；VPN Cookie 跨进程恢复 2 项。全部为合成资料，非学校真实账号攻击测试。 |
| APK 与 Lint | 默认 Debug 构建成功；Debug/Release Lint 均 0 error，分别 1496/1495 条 warning，各有 4 条 hint。最后的备份 XML 与无用调试依赖清理后重新构建、运行单元测试并检查清单。设备 9 项用于验证此前完成的业务修复，未重复做跨厂商设备测试。 |
| Backend API | unittest 最终显示 44 项：35 通过、8 失败、1 模块导入错误；其中实际运行 43 项，导入错误导致另 17 项生产测试未运行。新增 2 项审计回归通过。 |
| Python 静态安全扫描 | Bandit 1.9.4，生产 Python 8,045 行，无文件读取错误；45 条中等级提示、0 高等级。41 条是 SQL 拼接提示，抽查/逐类核对为固定 SQL 片段、白名单表名或 `?` 占位符，未确认可利用注入；2 条目录 0750 权限、1 条拒绝列表中的 `0.0.0.0` 字面量；1 条 URL 打开提示对应 U03。没有把原始提示隐藏或当作全部误报。 |
| 其他 | 后台 JS `node --check` 通过；两个仓库 `git diff --check` 通过；最终 APK 的包名、版本、Debug 属性、备份标志和签名再次核验。 |

一次带 `.bindingtest` 参数运行的 JVM 包名断言失败，是测试包名与普通 Debug 合同不一致。随后将设备构建与普通 JVM 验证分开，默认变体 247 项通过。该中间失败记录保留在本地日志中，没有修改断言绕过检查。

## 6. GitHub 上传准备方案

建议继续使用两个既有私有仓库，保留 Android / Backend 的独立历史和职责。下一步先记录已验收 preview.9 的源码快照，再提交审计修复，报告中清晰标明后台待完成项。上传的是源码、测试、资源、schema、依赖与脱敏说明。

正式执行上传前，应审查**实际暂存文件与 diff**：不能对项目根目录执行无差别添加。`05_Output` 下的本地快照、APK、扫描工具环境、原始日志和测试截图不在上传范围；学校账号、Cookie、验证码、数据库、签名配置及私钥始终排除。不改公开可见性，不 force push，不生成正式 Release。

如果此次仅做私有源码备份，可以如实附带 U01 等已知问题归档；如果要求“前后端完整交付且测试通过”，应先完成 U01 后再上传交付分支。当前尚未执行任何上传操作。

## 7. 审计边界与证据

本轮是全量自动扫描配合关键链路代码审查，不是独立第三方渗透测试，也不是逐行形式化验证。未测试真实校园凭据、真实运营库、厂商后台保活、任意学校页面未来变化、校园 VPN 服务端最长有效期或未部署的公网基础设施。Maven/PyPI 无命中不覆盖所有嵌入原生库、系统 WebView、构建插件或尚未披露漏洞。

原始本地证据在项目 `05_Output/source-audit-20261013/`：修改前快照、逐文件哈希、扫描 JSON、构建/测试日志、依赖坐标清单和独立审计候选 APK。可归档的脱敏摘要和实际修改清单位于本报告同目录 `evidence/`。已验收包保留在原 `student-flow-20261013/` 目录。
