# Gridea Android

> 把静态博客装进口袋 —— 一款专为 Android 设计的静态博客写作客户端。

[![Platform](https://img.shields.io/badge/Platform-Android-3DDC84?logo=android&logoColor=white)](https://developer.android.com)
[![Min SDK](https://img.shields.io/badge/Min%20SDK-24%20(Android%207.0)-0052CC)](https://developer.android.com/about/versions/nougat)
[![License](https://img.shields.io/badge/License-GPL--3.0-blue)](./LICENSE)
[![Language](https://img.shields.io/badge/Language-Kotlin-7F52FF?logo=kotlin&logoColor=white)](https://kotlinlang.org)
[![UI](https://img.shields.io/badge/UI-Jetpack%20Compose-4285F4?logo=jetpackcompose&logoColor=white)](https://developer.android.com/jetpack/compose)

无需电脑、无需服务器，一部手机即可完成「写作 → 预览 → 部署」的全流程。所有数据存储在本地，所有部署直连你自己的代码托管平台，不依赖任何云服务。

---

## 核心特性

### 写作

- **Markdown 编辑器**：基于 Markwon 引擎，支持代码高亮、任务列表、上下标、删除线、高亮标记等扩展语法
- **查找替换**：IDE 风格的查找替换面板，所有匹配项高亮定位
- **自动保存**：每停笔 2 秒自动保存，离开页面时协程兜底，保证不丢一个字
- **版本历史**：每 2 分钟生成一次文章快照，可随时回滚到任意历史版本
- **写作时长统计**：自动记录每篇文章的写作时长，配合字数目标进度条
- **桌面小部件**：1×1 新建文章小部件 + 4×1 写作统计小部件

### 预览

- **WebView 实时预览**：使用系统 WebView 加载本地构建产物，与浏览器表现一致
- **浏览器级导航**：返回 / 前进按钮遵循浏览器历史栈语义
- **JS Bridge**：内置 `GrideaPreview` 桥接对象，把页面内点击、控制台日志、资源加载失败等行为回传到 AppLogger
- **外链交给系统浏览器**：http / https 链接自动调起系统浏览器打开

### 主题

- **内置主题**：6 款精选主题（杂志、复古、瀑布流、双栏、终端、水墨）
- **theme.json 通信契约**：主题与 App 之间通过 `theme.json` 的 `customConfig` 数组通信，支持 text / switch / slider / code / multiselect 五种控件类型
- **即时预览**：切换主题配置后无需重新部署，预览界面立即重新渲染
- **响应式**：每个主题适配手机端（≤768px）、平板端（769-1024px）和电脑端（>1024px）
- **外部主题包**：通过 `.zip` 主题包导入

### 部署

| 平台 | 认证方式 | 增量策略 |
|------|---------|---------|
| GitHub Pages | Personal Access Token | Contents API 逐文件 PUT |
| Gitee + EdgeOne | Personal Access Token | PUT + DELETE |
| SFTP | 密码 / 私钥 | 全量替换 |
| Netlify | Bearer Token | SHA1 增量 |
| Vercel | Bearer Token | SHA 去重 |

部署过程在后台 Service 中执行，支持中断、进度通知、失败重试。

### 体验

- **100% Jetpack Compose**：全 App 无任何 XML 布局
- **Material 3 + 动态取色**：跟随系统主题色（Android 12+），支持亮色 / 暗色 / 跟随系统
- **灵动岛通知**：屏幕顶部以灵动岛样式展示操作反馈
- **手势导航**：横向手势切换主 Tab，手势返回优先 WebView 历史回退
- **国际化**：中文 / 英文 / 跟随系统
- **配置数据隔离**：SFTP / Gitee / GitHub 各自独立字段，切换平台不会串台

---

## 与原项目的区别

本项目基于 [Gridea 0.9.3](https://github.com/getgridea/gridea) 重构，针对 Android 平台从底层重新设计：

| 维度 | Gridea 原版 | Gridea Android |
|------|------------|----------------|
| 目标平台 | 桌面端（Electron） | Android 7.0+ |
| 技术栈 | Electron + Vue 2 | Kotlin + Jetpack Compose + Hilt |
| 模板引擎 | EJS | Pebble |
| 数据存储 | 本地 JSON 文件 | Room 数据库 + DataStore |
| Markdown 渲染 | Editor.js（前端 JS） | Markwon（原生 Android） |
| 主题包格式 | 文件夹目录 | 标准 `.zip` 压缩包 |
| 部署平台 | GitHub / Coding / SFTP / Netlify / Vercel | GitHub / Gitee / SFTP / Netlify / Vercel |
| 开源协议 | MIT | GPL-3.0 |

---

## 下载与安装

- 通过 GitHub Releases 下载 APK：[Releases](https://github.com/QingYu327/GrideaAndriod/releases)
- 应用内「设置 → 检查更新」会自动查询最新 Release

> 应用未上架 Google Play，安装时需在系统设置中允许「安装未知来源应用」。
> 系统要求：Android 7.0（API 24）及以上，推荐 Android 12+ 以获得动态取色支持。

---

## 主题系统

每个主题打包为 `.zip`，内部包含 theme.json + custom.css + custom.js + Pebble 模板文件。

### theme.json 通信契约

```json
{
  "name": "主题名称",
  "author": "作者",
  "version": "1.0.0",
  "customConfig": [
    {
      "key": "primaryColor",
      "label": "主色调",
      "type": "text",
      "value": "#42b983",
      "group": "颜色"
    }
  ]
}
```

支持的控件类型：`text` / `switch` / `slider` / `code` / `multiselect`

详细的主题开发指南请参考 [THEME_DEVELOPMENT_GUIDE.md](./docs/THEME_DEVELOPMENT_GUIDE.md)。

---

## 技术架构

| 层 | 技术 |
|----|------|
| UI | Jetpack Compose + Material 3 |
| 架构 | MVVM + Hilt DI |
| 数据 | Room + DataStore |
| Markdown | Markwon 4.6.2 |
| 图片 | Coil 2.7.0 |
| 部署 | OkHttp / JSch |
| 渲染 | Pebble 模板引擎 |

---

## 构建指南

### 环境要求

- JDK 17+
- Android SDK 34
- Android Studio Hedgehog+ 或 Gradle 8.9+

### 构建步骤

```bash
git clone https://github.com/QingYu327/GrideaAndriod.git
cd Gridea-Android/APP

# 配置 SDK 路径
echo "sdk.dir=/path/to/Android/Sdk" > local.properties

# 编译 Debug APK
./gradlew assembleDebug
```

生成的 APK 位于 `app/build/outputs/apk/debug/app-debug.apk`。

国内已配置腾讯云 Gradle 镜像和阿里云依赖镜像，无需额外配置。

---

## 致谢

- [Gridea](https://github.com/getgridea/gridea) — 原项目作者 EryouHao，本项目基于 Gridea 0.9.3 重构
- [Gridea Pro](https://github.com/vegaaltair/gridea-pro) — Pro 版本作者 Vega，部分设计灵感来源于此
- [Jetpack Compose](https://developer.android.com/jetpack/compose) / [Pebble](https://pebbletemplates.io/) / [Markwon](https://github.com/noties/markwon) / [Coil](https://github.com/coil-kt/coil)

---

## 许可证

本项目基于 [GPL-3.0](./LICENSE) 许可证开源。

> 本项目基于 Gridea 0.9.3（MIT 许可证）重构。Gridea 原项目版权归 EryouHao 所有。
