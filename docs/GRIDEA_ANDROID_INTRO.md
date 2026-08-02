# Gridea Android — 把静态博客装进口袋

> 一款专为 Android 设计的静态博客写作客户端。无需电脑、无需服务器，一部手机就能完成「写作 → 预览 → 部署」的全流程。

---

## 这是什么

Gridea Android 是 Gridea 全平台博客客户端的移动端成员。它把 Notion 式的轻盈写作体验搬到了手机上，同时保留静态博客「Markdown + 主题 + GitHub Pages」的核心范式。

打开 App，写下一行字，点一下部署——你的博客就更新了。整个流程不依赖任何云服务，所有数据都存在本地，所有部署都直连你自己的代码托管平台。

---

## 核心特性

### 写作 — 像发朋友圈一样写长文

- **Markdown 编辑器**：基于 Markwon 引擎，支持代码高亮、任务列表、上下标、删除线、高亮标记等扩展语法
- **查找替换**：IDE 风格的查找替换面板，所有匹配项以浅黄背景高亮，当前匹配项用深黄背景 + 斜体突出显示；切换匹配时编辑器自动滚动定位，220ms 防抖避免长文章卡顿
- **自动保存**：每停笔 2 秒自动保存，离开页面时用 `NonCancellable` 协程兜底，保证不丢一个字
- **版本历史**：每 2 分钟生成一次文章快照，可随时回滚到任意历史版本
- **写作时长统计**：自动记录每篇文章的写作时长，配合字数目标进度条，量化你的输出节奏
- **桌面小部件**：1×1 新建文章小部件 + 4×1 写作统计小部件，长按桌面即可添加

### 预览 — 所见即所得

- **WebView 实时预览**：使用系统 WebView 加载本地构建产物，与浏览器表现一致
- **浏览器级导航**：返回上一页 / 前到下一页按钮遵循浏览器历史栈语义，按钮启用状态实时同步 `canGoBack`/`canGoForward`
- **JS Bridge**：内置 `GrideaPreview` 桥接对象，把页面内点击、控制台日志、资源加载失败、404 等行为回传到 AppLogger，无需 logcat 即可定位主题问题
- **外链交给系统浏览器**：http/https 链接自动调起系统浏览器打开，不在应用内加载
- **调试开关**：设置 → 调试模式 → WebView 远程调试，开启后可用 Chrome DevTools 远程审查预览页

### 主题 — 一键切换，热更新

- **内置 + 外部主题包**：内置一个 default-theme，其余通过 `.zip` 主题包导入
- **theme.json 通信契约**：主题与 App 之间通过 `theme.json` 的 `customConfig` 数组通信，支持 text/switch/slider/code/multiselect 五种控件类型
- **即时预览**：切换主题配置后无需重新部署，预览界面立即重新渲染
- **assets 资源目录**：主题包可声明 css/js/font/image/file 类型附加资源，自动复制到输出目录并按需注入 HTML
- **响应式强制要求**：每个主题必须同时适配手机端（≤768px）和电脑端（>768px）

### 部署 — 五条直连通道

| 平台 | 认证方式 | 增量策略 | 备注 |
|------|---------|---------|------|
| GitHub Pages | Personal Access Token | Contents API 逐文件 PUT | 1 秒间隔防限流 |
| Gitee + EdgeOne | Personal Access Token | PUT + DELETE | 404 增强诊断 |
| SFTP | 密码 / 私钥 | 全量替换 | JSch mwiede fork |
| Netlify | Bearer Token | SHA1 增量 | 仅上传缺失文件 |
| Vercel | Bearer Token | SHA 去重 | SHA 去重优化 |

部署过程在后台 Service 中执行，支持中断、进度通知、失败重试。部署完成后自动写入部署历史记录。

### 数据 — 全本地，全透明

- **Room 数据库 v8**：5 张表（文章 / 标签 / 菜单 / 友链 / 文章版本），8 次迁移历史，软删除机制
- **DataStore 配置**：所有应用配置基于 Jetpack DataStore 持久化，无任何云端同步
- **自动备份**：`BackupScheduler` 调度周期性备份
- **日志系统**：内置 `AppLogger` 分级日志（INFO/WARN/ERROR/ACTION/DEBUG），支持一键打包脱敏后通过反馈通道提交

### 体验 — Material You 设计

- **Jetpack Compose**：全 App 100% Compose 实现，无任何 XML 布局
- **Material 3 + 动态取色**：跟随系统主题色，支持亮色 / 暗色 / 跟随系统
- **灵动岛通知**：`NoticeManager` 在屏幕顶部以灵动岛样式展示操作反馈
- **手势导航**：横向手势切换主 Tab（阈值 80dp，350ms 导航锁），手势返回优先 WebView 历史回退
- **FAB 按压动画**：按下 0.85 缩放（50ms），松手 1.0 回弹（150ms，`DampingRatioMediumBouncy`）
- **国际化**：中文 / 英文 / 跟随系统

---

## 安全与隐私

- 所有文章、配置、主题数据均存储在应用沙箱内，不上传任何服务器
- 部署时仅与你配置的代码托管平台通信，凭证保存在 DataStore 中（未加密，但隔离于其他应用）
- WebView 预览只加载本地 `file://` 产物，不加载任何外部 CDN（评论系统 CDN 在预览模式下自动跳过）
- 反馈日志打包前会脱敏处理（token 等敏感字段仅保留前缀）

---

## 技术栈

| 层 | 技术 |
|----|------|
| UI | Jetpack Compose + Material 3 |
| 架构 | MVVM + Hilt DI |
| 数据 | Room v8 + DataStore |
| 异步 | Kotlin Coroutines + Flow |
| Markdown | Markwon 4.6.2 |
| 图片 | Coil 2.7.0 |
| 部署 | OkHttp / JSch |
| 渲染 | 内置 TemplateEngine（Kotlin） |

**最低系统**：Android 7.0（API 24）
**目标系统**：Android 14（API 34）

---

## 下载与安装

- 通过 GitHub Releases 下载 APK：[QingYu327/GrideaAndriod](https://github.com/QingYu327/GrideaAndriod/releases)
- 应用内「设置 → 检查更新」会自动查询最新 Release 并提示下载安装

> 应用未上架 Google Play，安装时需在系统设置中允许「安装未知来源应用」。

---

## 适用场景

- 通勤地铁上想记下一段灵感，回家自动部署到博客
- 旅行途中用手机写游记，配图直接拍照插入
- 不想配置 Hexo/Hugo 命令行，只想写完点一下就发布
- 想要一个完全本地、不依赖任何云服务的博客工具

---

## 反馈与社区

- 提交 Bug 或功能建议：通过应用内「设置 → 问题反馈」一键打包日志
- 主题开发：参考 [主题开发 Wiki](./THEME_DEVELOPMENT.md)
- 代码贡献：欢迎 PR，构建方式见 [Code Wiki](./CODE_WIKI.md)

---

## 许可证

Gridea Android 遵循 MIT 许权证开源。
