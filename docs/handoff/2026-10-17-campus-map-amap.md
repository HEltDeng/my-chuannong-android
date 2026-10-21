# 高德校园地图接入（2026-10-17）

用户在离线示意图完成后明确要求接入高德，并提供了 Android Key。用户随后要求高德叫什么就显示什么，并补齐主要楼宇。随后按用户“不要示意图”的要求，仅保留高德地图，删除模式切换、原图入口、离线绘制代码、79 个示意地点和三个打包手绘图片；其他模块保持现有逻辑。

## 配置与初始化

- 固定官方 Maven 合包 `com.amap.api:3dmap-location-search:11.3.100_loc11.3.000_sea9.8.1`，避免动态升级。
- 在 Git 忽略的 `local.properties` 配置 `amap.apiKey.debug`，或通过 `AMAP_API_KEY_DEBUG` 环境变量提供。禁止把实际值写进文档、源码、测试或提交。
- Debug 包名 `com.mychuannong.app.debug`，必须在高德控制台绑定对应 APK 的 SHA-1。Release 另配 `amap.apiKey.release` / `AMAP_API_KEY_RELEASE`，不会沿用 Debug Key；validation 始终空 Key。缺少 Key 时显示不可用提示，不创建地图或提供示意图替代。
- 本 SDK 原生库只包含 ARM，本项目构建限定 ARM64，避免混合 APK 在 x86 模拟器中选错架构。验证模拟器使用 ARM64 翻译；普通 ARM64 Android 手机可直接安装。
- 首次地图说明可拒绝；拒绝后不创建 MapView、不调用搜索 SDK，下次进入不会反复弹出。拒绝后显示启用入口，可主动重新打开说明。旧版已同意但曾切到示意图的用户，升级后直接使用高德。
- 同意后先调用两组 SDK 的隐私接口，再配置 Key 与 HTTPS，随后创建 TextureMapView。生命周期跟随 Activity，离开页面会销毁视图并取消协程。
- 合包包含定位组件，但应用没有初始化定位客户端，也未增加定位、Wi-Fi 或电话权限。隐私页和第三方清单已增加说明及可点击政策链接。

## 数据与交互

- CampusAmapData 查询三校区中心、学校与 14 组楼宇/场所关键词，按页检索（每页 25、每词最多 8 页），启用 CHILDREN 子地点。学校归属来自名称、明确学校门牌地址或父子地点关系，并校验校区及地理距离；不使用学校账号或手机位置。
- CampusAmapCatalogue 原样保留高德名称、ID、经纬度，按主要场所名称分类，排除校外旅店、商铺、公交站、停车场等。中文数字、楼号别名仅用于搜索，不改变显示名称。
- assets/campus-map/amap-verified-buildings.json 补入 16 个普通搜索缺失、但从高德原生 OnPOIClick 回调实际记录的楼宇：成都 7、雅安 6、都江堰 3。包含来源、核验日期及 GCJ-02 坐标。没有按手绘图猜位置或用商铺位置代替建筑。搜索已有相同 ID 时优先保留接口记录。
- 删除旧 CampusPoiMatcher 及选择后补查/“暂未匹配”流程。地点模型不再包含虚拟轮廓、示意高度或手绘坐标。
- 安装包附带 2026-10-17 实际核验的 116 个地点快照（成都 46、雅安 49、都江堰 21）；快照与本机缓存有效期均为七天，过期后重新查询。首次同意后按当前校区优先预载三校区，使用 campus_amap_catalogue_v2 缓存，读取时也补入核验楼宇。搜索在本机进行，不发送输入内容。
- 当前可见模拟器曾出现 CUQPS_HAS_EXCEEDED_THE_LIMIT。公开搜索请求以进程级 Mutex 串行、至少间隔 1100 ms，QPS 错误退避 2500 ms 后重试一次。有效快照避免每次新安装集中查询。其他校区加载中或失败时提示，可重试。
- 选择地点后收起键盘、同步分类与校区、平滑移动，保持方位。选中卡片置顶并换行，面板收起后仍可见。
- Native MapView 高度跟随可视区域，使自然中心位于面板上方。移除 SDK 自定义中心偏移，修复原生 POI 点击坐标偏移。高德署名保持在面板上方。
- 总览用小型分类标记和高德自身文字；选中或筛选少量地点时显示完整名称标牌。原生 POI、标记和列表通过 ID 联动；选中标牌和位置圈高亮。真实建筑几何与高度由高德提供，没有伪造轮廓。
- 手动视角和选择在 Activity 重建后恢复；切校区定位相应校区。

## 数据边界

目录来自当前 Key 的校园公开地点与核验的原生楼宇标注。高德底图、搜索索引、纯楼栋文字并不完全相同，目录数量不是学校建筑总数。后续补录须继续核对实际标注与坐标，不得重引入手绘名称猜测或随机占位。公共 POI 本身可能更新滞后。加载失败只提供重试，不回退到示意地图。

## 验证与安装

独立 AVD MyChuannongAMap 上运行联网集成、首次拒绝/重新启用、旧偏好升级、学校服务入口测试。覆盖三校区原名与坐标、跨分类/跨校区搜索、雅安操场、补录教学楼、键盘收起、卡片可见、真实地图触摸联动和重建。人工核验辅助测试保存在输出目录，不进入回归套件。

公共查询审计、原生回调 JSONL、截图、构建/测试日志和 APK 位于仓库外 05_Output/campus-map-vendor-places-20261017；最终统计和校验值见 verification.json。移除示意图后的新 APK、构建与设备测试记录位于 05_Output/campus-map-amap-only-20261017。验收后通过 adb install -r 更新当前可见 emulator-5554 的同包名 Debug App，保留原有数据。

移除示意图后验证：276 项 JVM 测试、4 项设备测试全部通过；Debug/Release Lint 零错误（分别 1496/1495 条既有警告）。当前可见模拟器已覆盖安装并停留在高德校园地图。

未提交、推送、创建 Release 或正式发布。

官方参考：[Android SDK 集成](https://developer.amap.com/api/android-sdk/guide/create-project/android-studio-create-project)、[POI 扩展字段](https://a.amap.com/lbs/static/unzip/Android_Map_Doc/Search/com/amap/api/services/poisearch/PoiSearchV2.ShowFields.html)、[高德隐私政策](https://lbs.amap.com/pages/privacy/)。
