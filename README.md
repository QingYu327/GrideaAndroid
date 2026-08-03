# Gridea Android

> 把静态博客装进口袋 —— 一款专为 Android 设计的静态博客写作客户端。

[![Platform](https://img.shields.io/badge/Platform-Android-3DDC84?logo=android&logoColor=white)](https://developer.android.com)
[![Min SDK](https://img.shields.io/badge/Min%20SDK-24%20(Android%207.0)-0052CC)](https://developer.android.com/about/versions/nougat)
[![Language](https://img.shields.io/badge/Language-Kotlin-7F52FF?logo=kotlin&logoColor=white)](https://kotlinlang.org)
[![UI](https://img.shields.io/badge/UI-Jetpack%20Compose-4285F4?logo=jetpackcompose&logoColor=white)](https://developer.android.com/jetpack/compose)
[![License](https://img.shields.io/badge/License-GPL--3.0-blue)](./LICENSE)

无需电脑、无需服务器，一部手机即可完成「写作 → 预览 → 部署」的全流程。所有数据存储在应用沙箱内，所有部署直连你自己的代码托管平台，不依赖任何云服务，不经过任何中间服务器。

打开 App，写下一行字，点一下部署——你的博客就更新了。

---

## 核心特性

### ✍️ 写作 — 像发朋友圈一样写长文

- **Markdown 编辑器**：基于 Markwon 引擎，支持代码高亮、任务列表、上下标、删除线、高亮标记等扩展语法
- **查找替换**：IDE 风格的查找替换面板，所有匹配项高亮定位，220ms 防抖避免长文卡顿
- **自动保存**：每停笔 2 秒自动保存，离开页面时用 `NonCancellable` 协程兜底，保证不丢一个字
- **版本历史**：每 2 分钟生成一次文章快照，可随时回滚到任意历史版本
- **写作统计**：自动记录写作时长，字数目标进度条 + 文章热力图，量化你的输出节奏
- **桌面小部件**：1×1 新建文章小部件 + 4×1 写作统计小部件，长按桌面即可添加

### 👁️ 预览 — 所见即所得

- **WebView 实时预览**：加载本地构建产物，与浏览器表现完全一致
- **即时重渲染**：修改主题配置后预览立即刷新，无需重新部署
- **浏览器级导航**：返回 / 前进按钮遵循浏览器历史栈语义，状态实时同步
- **JS Bridge**：内置 `GrideaPreview` 桥接对象，把点击、控制台日志、资源加载失败、404 等行为回传到 AppLogger
- **外链交给系统浏览器**：http / https 链接自动调起系统浏览器，不在应用内加载
- **离线可用**：预览只加载本地资源，飞行模式下也能查看效果

### 🎨 主题 — 一键切换，热更新

- **内置 6 款主题**：magazine（杂志）、retro（复古）、masonry（瀑布流）、sidebar（双栏）、terminal（终端）、ink（水墨）
- **Pebble 模板引擎**：使用 Pebble 4.x 渲染 `.peb` 模板，每个主题包含 8 个模板 + theme.json + custom.css + custom.js + preview.jpg
- **theme.json 通信契约**：通过 `customConfig` 数组声明配置项，支持 **12 种内置控件类型**：`input` / `textarea` / `switch` / `color` / `select` / `radio` / `multiselect` / `number` / `slider` / `code` / `image` / `compound`
- **动态适配**：外部主题可声明自定义 `type`，通过 `fallback` 字段降级渲染（未指定则降级 `textarea`）；`compound` 复合控件可组合多个原子控件，无需 APP 发版即可支持新控件
- **统计字段统一**：所有主题的统计组包含 ga（Google Analytics）、baidu（百度统计）、tencent（腾讯分析）、view（不蒜子访客）四项配置
- **即时预览**：切换主题配置后无需重新部署，预览界面立即重新渲染
- **外部主题包**：通过 `.zip` 主题包导入，文件平铺在根目录
- **三端响应式**：每个主题适配手机端（≤768px）、平板端（769-1024px）、电脑端（>1024px）

### 🚀 部署 — 五条直连通道

| 平台 | 认证方式 | 增量策略 | 备注 |
|------|---------|---------|------|
| GitHub Pages | Personal Access Token / OAuth | Contents API 逐文件 PUT | 1 秒间隔防限流 |
| Gitee + EdgeOne | Personal Access Token | PUT + DELETE | Gitee Pages 已关闭，改用腾讯云 EdgeOne 静态托管 |
| SFTP | 密码 / 私钥 | 全量替换 | JSch mwiede fork |
| Netlify | Bearer Token | SHA1 增量 | 仅上传缺失文件 |
| Vercel | Bearer Token | SHA 去重 | 支持 Clean URLs 配置 |

部署过程在后台 Service 中执行，支持中断、进度通知、失败重试。各平台配置相互隔离，切换不会互相干扰。

### 🔒 隐私 — 全本地，全透明

- 所有文章、配置、主题数据均存储在应用沙箱内，不上传任何服务器
- 部署时仅与你配置的代码托管平台通信，凭证保存在本地 DataStore
- WebView 预览只加载本地 `file://` 产物，不加载任何外部 CDN
- 反馈日志打包前自动脱敏处理（Token 等敏感字段仅保留前缀）

### ✨ 体验 — Material You 设计

- **100% Jetpack Compose**：全 App 无任何 XML 布局
- **Material 3 + 动态取色**：跟随系统主题色（Android 12+），亮色 / 暗色 / 跟随系统
- **灵动岛通知**：屏幕顶部以灵动岛样式展示操作反馈
- **手势导航**：横向手势切换主 Tab（阈值 80dp，350ms 导航锁），手势返回优先 WebView 历史回退
- **FAB 按压动画**：按下 0.85 缩放，松手回弹，按压反馈清晰利落
- **国际化**：中文 / 英文 / 跟随系统

---

## 与原项目的区别

本项目基于 [Gridea 0.9.3](https://github.com/getgridea/gridea) 重构，针对 Android 平台从底层重新设计：

| 维度 | Gridea 原版 | Gridea Android |
|------|------------|----------------|
| 目标平台 | 桌面端（Electron） | Android 7.0+ |
| 技术栈 | Electron + Vue 2 | Kotlin + Jetpack Compose + Hilt |
| 模板引擎 | EJS | Pebble 4.x |
| 数据存储 | 本地 JSON 文件 | Room 数据库 + DataStore |
| Markdown 渲染 | Editor.js（前端 JS） | Markwon（原生 Android） |
| 主题包格式 | 文件夹目录 | 标准 `.zip` 压缩包 |
| 部署平台 | GitHub / Coding / SFTP / Netlify / Vercel | GitHub / Gitee / SFTP / Netlify / Vercel |
| 开源协议 | MIT | GPL-3.0 |

---

## 技术架构

| 层 | 技术 |
|----|------|
| UI | Jetpack Compose + Material 3 |
| 架构 | MVVM + Hilt DI |
| 数据 | Room v8 + DataStore |
| 异步 | Kotlin Coroutines + Flow |
| Markdown | Markwon 4.6.2 |
| 图片 | Coil 2.7.0 |
| 部署 | OkHttp / JSch |
| 渲染 | Pebble 4.x 模板引擎 |

**最低系统**：Android 7.0（API 24）　**目标系统**：Android 14（API 34）

---

## 下载与安装

- 通过 GitHub Releases 下载 APK：[Releases](https://github.com/QingYu327/GrideaAndroid/releases)
- 应用内「设置 → 检查更新」会自动查询最新 Release 并提示下载安装

> 应用未上架 Google Play，安装时需在系统设置中允许「安装未知来源应用」。
> 系统要求：Android 7.0（API 24）及以上，推荐 Android 12+ 以获得动态取色支持。

---

## 主题系统

每个主题打包为 `.zip`，内部文件平铺在根目录（不嵌套父目录），包含：

```
my-theme.zip
├── theme.json        # 主题元数据与配置项声明（必需）
├── custom.css        # 主题样式，支持 {{变量}} 占位符（必需）
├── custom.js         # 主题脚本，IIFE 结构（必需）
├── base.peb          # 基础布局模板（必需）
├── index.peb         # 首页文章列表与分页（必需）
├── post.peb          # 文章详情页（必需）
├── archives.peb      # 归档页（必需）
├── tags.peb          # 标签总览页（必需）
├── tag.peb           # 标签详情页（必需）
├── friends.peb       # 友链页（必需）
├── 404.peb           # 404 错误页（必需）
└── preview.jpg       # 主题预览缩略图（必需）
```

### theme.json 示例

```json
{
  "name": "Magazine",
  "version": "1.0.0",
  "author": "Gridea Android",
  "description": "大图封面、网格布局的杂志风格主题",
  "customConfig": [
    {
      "name": "primaryColor",
      "label": "主色调",
      "type": "color",
      "value": "#9C8FDA",
      "group": "颜色"
    },
    {
      "name": "showAuthor",
      "label": "显示作者信息",
      "type": "switch",
      "value": true,
      "group": "排版"
    },
    {
      "name": "postColumns",
      "label": "首页文章列数",
      "type": "slider",
      "value": 2,
      "min": 1,
      "max": 4,
      "group": "排版"
    }
  ],
  "stats": {
    "ga": "",
    "baidu": "",
    "tencent": "",
    "view": ""
  }
}
```

支持的控件类型：`input` / `textarea` / `switch` / `color` / `select` / `radio` / `multiselect` / `number` / `slider` / `code` / `image` / `compound`

**动态适配机制**：
- **fallback 智能降级**：自定义 `type` 通过 `fallback` 字段指定降级渲染的原子类型（未指定则降级 `textarea`），保证配置值始终可读写
- **compound 复合控件**：通过 `items` 数组组合多个原子控件（支持递归嵌套），`layout` 指定布局方向（`row`/`column`），值以 JSON 对象字符串存储

详细的主题开发指南请参考 [THEME_DEVELOPMENT_GUIDE.md](./docs/THEME_DEVELOPMENT_GUIDE.md)。

---

## 构建指南

### 环境要求

- JDK 17+
- Android SDK 34
- Android Studio Hedgehog+ 或 Gradle 8.9+

### 构建步骤

```bash
git clone https://github.com/QingYu327/GrideaAndroid.git
cd GrideaAndroid/APP

# 配置 SDK 路径
echo "sdk.dir=/path/to/Android/Sdk" > local.properties

# 编译 Debug APK
./gradlew assembleDebug
```

生成的 APK 位于 `app/build/outputs/apk/debug/app-debug.apk`。

国内已配置腾讯云 Gradle 镜像和阿里云依赖镜像，无需额外配置。

---

## 文档

- [官方文档](https://gridea.cc.cd/docs/index.html) — 快速开始、写作、主题、部署、设置与常见问题
- [主题画廊](https://gridea.cc.cd/themes.html) — 6 款内置主题预览与下载
- [Code Wiki](./docs/CODE_WIKI.md) — 项目结构与关键文件说明
- [主题开发指南](./docs/THEME_DEVELOPMENT_GUIDE.md) — 完整的主题开发规范
- [更新日志](./docs/RELEASE_NOTES.md) — 版本更新记录

---

## 适用场景

- 通勤地铁上想记下一段灵感，回家自动部署到博客
- 旅行途中用手机写游记，配图直接拍照插入
- 不想配置 Hexo / Hugo 命令行，只想写完点一下就发布
- 想要一个完全本地、不依赖任何云服务的博客工具

---

## 反馈与社区

- 提交 Bug 或功能建议：通过应用内「设置 → 问题反馈」一键打包日志
- 代码贡献：欢迎 PR，构建方式见 [Code Wiki](./docs/CODE_WIKI.md)

---

## 致谢

- [Gridea](https://github.com/getgridea/gridea) — 原项目作者 EryouHao，本项目基于 Gridea 0.9.3 重构
- [Gridea Pro](https://github.com/vegaaltair/gridea-pro) — Pro 版本作者 Tespera，部分设计灵感来源于此
- [Jetpack Compose](https://developer.android.com/jetpack/compose) / [Pebble](https://pebbletemplates.io/) / [Markwon](https://github.com/noties/markwon) / [Coil](https://github.com/coil-kt/coil)

---

## 许可证

本项目基于 [GPL-3.0](./LICENSE) 许可证开源。

> 本项目基于 Gridea 0.9.3（MIT 许可证）重构。Gridea 原项目版权归 EryouHao 所有。
