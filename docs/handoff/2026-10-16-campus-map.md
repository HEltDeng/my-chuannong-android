# 校园地图 · 2026-10-16

用户确认校园地图效果图后要求接入现有 Android APP。本次采用原生 Compose 页面与项目已有 PhotoView，不新增网页服务、地图 SDK 或定位权限。

## 入口与行为

- `SchoolServiceCatalog` 在「学校服务」原有七个入口后追加「校园地图」，使用绿色折叠地图图标。
- `school/CampusMapActivity.kt` 默认展示成都校区，三个等宽标签切换雅安、成都、都江堰；切换重置图片缩放，防止沿用其他校区的偏移。
- 图片按原比例完整适配，支持 1–6 倍缩放、双指手势、双击和放大后的拖动。加减按钮使用 48dp 触摸区域，位于地图下方以保留全部原图标注。
- 全屏隐藏系统栏，提供关闭、重置和加减控制；系统返回先退出全屏，再返回原服务页。页面重建保留当前校区及全屏模式。
- 所有资源随 APK 打包。首次访问与离线查看均不需要学校登录、产品后台或网络；不提供实时定位或路线导航。
- 新 Activity 不导出。同步移除旧版「不开放校园地图」限制及过时协议表述，继续保留真实体育预约、支付及定位权限的限制。

## 原图

资源位于 `app/src/main/res/drawable-nodpi/`，逐字节保留上传文件，未使用效果图中的重绘地图。

| 校区 | 文件 | SHA-256 |
| --- | --- | --- |
| 雅安 | campus_map_yaan.jpg | 23dce306b3fa25d56653a50d8754607e8191b1f8656a41d3d9ff3799e60b5e4f |
| 成都 | campus_map_chengdu.jpg | b9ce1680a32904e0bb8eb1305c1191d66402d30d41d51e5560ccec28101cf6cd |
| 都江堰 | campus_map_dujiangyan.jpg | 0135329aee1d780812333510cc6ac8c8414017513dc2befe621af332731175dc |

## 验证入口

`CampusMapDeviceTest` 覆盖三校区、缩放上下限、手势、全屏、返回与重建；`CampusMapEntryDeviceTest` 覆盖真实学校服务组件的第八项与导航。测试使用独立 `.campusmaptest` 包，不读取或修改已安装主应用的数据。

证据与内部 APK 保存在工作区 `05_Output/campus-map-integrated-20261016/`，最终验证摘要见该目录 `verification.json`，视觉比较见仓库根 `design-qa.md` 的校园地图章节。
