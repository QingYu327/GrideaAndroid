# Gridea Android v0.1.0

**把静态博客装进口袋 —— Android 端静态博客写作客户端首个公开版本。**

## 简介

Gridea Android 是一款专为 Android 设计的静态博客写作客户端。无需电脑、无需服务器，一部手机即可完成「写作 → 预览 → 部署」的全流程。所有数据存储在本地，所有部署直连你自己的代码托管平台，不依赖任何云服务。

## 主要功能

- **Markdown 写作**：代码高亮、任务列表、查找替换、2 秒自动保存、版本历史可回滚
- **实时预览**：WebView 加载本地构建产物，与浏览器表现一致，外链自动交给系统浏览器
- **主题系统**：7 款内置主题，Pebble 模板引擎 + theme.json 通信契约，切换配置即时预览，三端响应式
- **一键部署**：支持 GitHub Pages、Gitee、SFTP、Netlify、Vercel 五大平台，后台 Service 执行
- **Material You**：100% Jetpack Compose，动态取色（Android 12+），灵动岛通知，手势导航
- **全本地隐私**：Room 数据库 + DataStore 持久化，不上传任何服务器
- **配置隔离**：SFTP / Gitee / GitHub 各自独立字段，切换平台不会串台

## 系统要求

- Android 7.0（API 24）及以上
- 推荐 Android 12+ 以获得动态取色支持

## 下载

- GitHub Release 附件：`app-release.apk`
- 应用内「设置 → 检查更新」可自动检测新版本

## 与原项目的区别

本项目基于 Gridea 0.9.3（MIT 许可证）重构，针对 Android 平台从底层重新设计：Electron + Vue → Kotlin + Compose，EJS → Pebble，JSON 文件 → Room + DataStore，MIT → GPL-3.0。

## 致谢

- [Gridea](https://github.com/getgridea/gridea) 原项目作者 EryouHao
- [Gridea Pro](https://github.com/vegaaltair/gridea-pro) Pro 版本作者 Tespera

## 许可证

GPL-3.0，详见 [LICENSE](./LICENSE)。
