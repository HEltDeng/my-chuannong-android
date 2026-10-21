# 失物招领页面

用户于 2026-10-16 确认可交互版本后接入 Android。页面源码位于此目录，应用运行时只读取 `app/src/main/assets/lost-found/` 中的离线产物，不依赖 Vite 或电脑服务器。

使用 Node 24、npm 11：

```sh
npm ci
npm test
npm run build
```

`build` 先执行 TypeScript 检查和 Vite 打包，再将资源与许可文件同步到 Android assets。同步范围仅为 `assets/lost-found`，不会修改其他功能资源。随后按仓库 README 构建 APK。

- `src/App.tsx`：搜索、校区、寻物/招领、详情、发布、我的发布、编辑、完成、删除/撤销。
- `src/storage.ts`：IndexedDB 事务、校验和写入失败处理。
- `src/mobile.tsx`、`src/native.css`：实际手机上的滚动、底部面板、原生键盘和安全区域适配。没有模拟手机壳或模拟键盘。
- `tests/storage.test.mjs`：持久化、并发、归属限制、无效输入、写入失败及损坏数据保护。
- `public/assets/items`：已确认的 ImageGen 物品照片；图标来自 Radix，字体为 Noto Sans SC，许可随离线包提供。

新数据使用 `mychuannong-lost-found-v2` 数据库，版本 1、`content` 仓库、`state` 键。保存仅在事务完成后反馈成功。旧失物招领缓存保留且不被覆盖；新页面不读取旧草稿或旧发布模型。首次使用提供 7 条明确标注的示例，不能从“我的发布”管理示例。发布、编辑、完成和删除只在本机生效，未接入跨设备发布服务，表单中有相应说明。

Android 容器保留既有列表、详情、发布 Intent；只加载本功能的离线文件，禁止网络页面、其他功能资源和文件访问，不提供通用 JavaScript 桥。系统文档选择器只接收用户选择的 PNG/JPEG/WebP，网页限制单张 10 MB。真实发布图片按 data URL 持久化，重开不依赖临时 URI 授权。
