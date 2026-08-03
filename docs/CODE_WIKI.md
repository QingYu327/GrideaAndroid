# Gridea 全平台博客写作客户端 — Code Wiki

> 本仓库是一个**多端静态博客写作客户端**的完整工程集合，包含三个独立子项目，覆盖 Android、桌面（Wails/Go）与旧版桌面（Electron）三套技术栈。所有子项目均围绕同一产品理念：**让用户像用 Notion 一样写博客**，并提供静态站点生成与多平台部署能力。

---

## 目录

- [一、项目总览](#一项目总览)
  - [1.1 仓库结构](#11-仓库结构)
  - [1.2 三大子项目对比](#12-三大子项目对比)
  - [1.3 技术演进路线](#13-技术演进路线)
- [二、APP — Android 原生应用](#二app--android-原生应用)
  - [2.1 项目配置](#21-项目配置)
  - [2.2 整体架构](#22-整体架构)
  - [2.3 模块职责](#23-模块职责)
  - [2.4 关键类与函数](#24-关键类与函数)
  - [2.5 资源与主题](#25-资源与主题)
  - [2.6 运行方式](#26-运行方式)
- [三、gridea-pro — Wails 桌面应用](#三gridea-pro--wails-桌面应用)
  - [3.1 项目配置](#31-项目配置)
  - [3.2 整体架构](#32-整体架构)
  - [3.3 后端模块（Go）](#33-后端模块go)
  - [3.4 前端模块（Vue 3）](#34-前端模块vue-3)
  - [3.5 MCP 服务器](#35-mcp-服务器)
  - [3.6 运行方式](#36-运行方式)
- [四、gridea-old — Electron 旧版应用](#四gridea-old--electron-旧版应用)
  - [4.1 项目配置](#41-项目配置)
  - [4.2 整体架构](#42-整体架构)
  - [4.3 后端服务层](#43-后端服务层)
  - [4.4 前端架构](#44-前端架构)
  - [4.5 运行方式](#45-运行方式)
- [五、跨项目共性设计](#五跨项目共性设计)
- [六、依赖关系总览](#六依赖关系总览)
- [七、构建与发布](#七构建与发布)
- [八、最新更新日志（v0.1.0+）](#八最新更新日志v010)

---

## 一、项目总览

### 1.1 仓库结构

```
test/
├── APP/                          # Android 原生应用（Kotlin + Compose）
│   ├── app/                      # 应用模块
│   ├── gradle/                   # Gradle 配置与版本目录
│   ├── build.gradle.kts          # 顶层构建脚本
│   └── settings.gradle.kts       # 项目设置（阿里云镜像）
├── gridea-pro/
│   └── gridea-pro-1.2.2/         # Wails 桌面应用（Go + Vue 3）
│       ├── backend/              # Go 后端
│       ├── frontend/             # Vue 3 前端
│       ├── main.go               # 程序入口
│       └── wails.json            # Wails 配置
├── gridea-old/
│   └── gridea-0.9.3/             # Electron 旧版（Vue 2 + TS）
│       ├── src/                  # 源码（主进程 + 渲染进程）
│       └── package.json
├── ico/                          # 应用图标资源（各密度）
├── img/                          # 截图
└── log/                          # 构建与运行日志
```

### 1.2 三大子项目对比

| 维度 | APP（Android） | gridea-pro（桌面） | gridea-old（旧版桌面） |
|------|---------------|-------------------|----------------------|
| 技术栈 | Kotlin + Jetpack Compose | Go + Vue 3 + Wails v2 | Electron 7 + Vue 2 + TS |
| 架构模式 | MVVM + Hilt DI | 分层（Domain/Engine/Facade） | IPC 双进程（主/渲染） |
| UI 框架 | Compose + Material3 | Vue 3 + Headless UI + Tailwind 4 | Ant Design Vue + Tailwind 1 |
| 数据存储 | Room v8 + DataStore | JSON 文件 + 系统 Keychain | lowdb（JSON 文件） |
| 状态管理 | ViewModel + StateFlow | Pinia | Vuex |
| 渲染引擎 | 内置 TemplateEngine（Kotlin） | Pongo2/EJS/Go Templates | EJS + Less |
| Markdown | Markwon | goldmark | markdown-it（13 插件） |
| 部署平台 | GitHub/Gitee/SFTP/Netlify/Vercel | Git/SFTP/FTP/Netlify/Vercel | GitHub/Coding/Gitee/SFTP/Netlify |
| 国际化 | 中/英/跟随系统 | 11 种语言 | 5 种语言 |
| 版本号 | 0.1.0 | 1.2.2 | 0.9.3 |
| 目标平台 | Android 7.0+（API 24） | macOS/Windows/Linux | macOS/Windows/Linux |

### 1.3 技术演进路线

```
gridea-old (Electron + Vue 2)          ← 2019 年初版，10k+ Stars
        │
        ├─► APP (Android 原生)          ← 移动端重构，现代 Android 技术栈
        │
        └─► gridea-pro (Wails + Go)     ← 桌面端重写，引入 MCP/AI/PWA
```

三者**产品功能同源**（文章管理、标签、菜单、主题、部署、评论），但**实现完全独立**，各自采用所在生态的最佳实践。

---

## 二、APP — Android 原生应用

### 2.1 项目配置

**构建工具**：Gradle 8.9（腾讯云镜像）+ AGP 8.5.2 + Kotlin 2.0.20 + KSP 2.0.20-1.0.25

**应用信息**：
- `applicationId` = `com.gridea.android`
- `compileSdk` / `targetSdk` = 34，`minSdk` = 24
- `versionCode` = 1，`versionName` = "0.1.0"
- Java/Kotlin 目标 = VERSION_17

**核心依赖**（来自 [libs.versions.toml](file:///c:/Users/Lime/Desktop/test/APP/gradle/libs.versions.toml)）：

| 类别 | 依赖 | 版本 |
|------|------|------|
| Compose | compose-bom | 2024.09.02 |
| 导航 | navigation-compose | 2.8.1 |
| 数据库 | Room | 2.6.1 |
| DI | Hilt | 2.52 |
| 异步 | kotlinx-coroutines | 1.9.0 |
| 序列化 | kotlinx-serialization | 1.7.3 |
| Markdown | markwon | 4.6.2 |
| 图片 | coil | 2.7.0 |
| 部署 | okhttp / jsch | 4.12.0 / 0.2.20 |
| 存储 | datastore | 1.1.1 |
| 性能 | profileinstaller | 1.4.1 |

**镜像配置**：[settings.gradle.kts](file:///c:/Users/Lime/Desktop/test/APP/settings.gradle.kts) 全量使用阿里云 Maven 镜像，`FAIL_ON_PROJECT_REPOS` 禁止子项目自定义仓库。

### 2.2 整体架构

采用**单模块 MVVM + Hilt DI**架构，分层清晰：

```
┌─────────────────────────────────────────────┐
│                  UI Layer                    │
│  Compose Screens + ViewModel + StateFlow     │
│  (GrideaApp → NavHost → 16 Screens)          │
├─────────────────────────────────────────────┤
│               Domain Layer                   │
│  SiteRenderer · TemplateEngine ·             │
│  MarkdownConverter · DeployManager           │
├─────────────────────────────────────────────┤
│                Data Layer                    │
│  Room v8 (5 DAO/Entity) · 13 Repositories ·  │
│  DataStore                                    │
├─────────────────────────────────────────────┤
│             Infrastructure                   │
│  Hilt DI · AppLogger · CrashHandler ·        │
│  5 Deployers · Widget · BackupScheduler      │
└─────────────────────────────────────────────┘
```

**入口流程**：
1. [GrideaApp.kt](file:///c:/Users/Lime/Desktop/test/APP/app/src/main/java/com/gridea/android/GrideaApp.kt)（`@HiltAndroidApp`）→ 初始化 AppLogger / CrashHandler / 输出目录
2. [MainActivity.kt](file:///c:/Users/Lime/Desktop/test/APP/app/src/main/java/com/gridea/android/MainActivity.kt)（`@AndroidEntryPoint`）→ 读取语言/主题设置 → `setContent { GrideaAndroidTheme { GrideaApp() } }`
3. [ui/GrideaApp.kt](file:///c:/Users/Lime/Desktop/test/APP/app/src/main/java/com/gridea/android/ui/GrideaApp.kt) → 引导页判断 → `GrideaAppContent`（SharedTransitionLayout + NavHost）

### 2.3 模块职责

#### 2.3.1 数据层（`data/`）

- **`data/db/`**：Room 数据库 [GrideaDatabase.kt](file:///c:/Users/Lime/Desktop/test/APP/app/src/main/java/com/gridea/android/data/db/GrideaDatabase.kt)（v8，5 张表）+ 5 个 DAO + 5 个 Entity
  - 迁移历史：v1→v2 文章版本表、v3 友链、v4 写作时长、v5 菜单、v6/v7/v8 软删除
- **`data/model/`**：13 个领域模型（Post、Tag、Setting、Theme、ThemePack、Menu、FriendLink、DeployRecord 等）
- **`data/repository/`**：13 个 Repository，封装 DAO 与业务逻辑

#### 2.3.2 渲染层（`renderer/`）

- **[SiteRenderer.kt](file:///c:/Users/Lime/Desktop/test/APP/app/src/main/java/com/gridea/android/renderer/SiteRenderer.kt)**：静态站点生成核心，支持**增量构建**（MD5 content hash + config hash）
- **[PebbleTemplateEngine.kt](file:///c:/Users/Lime/Desktop/test/APP/app/src/main/java/com/gridea/android/renderer/PebbleTemplateEngine.kt)**：基于 Pebble 4.x 的模板引擎，加载 `.peb` 模板生成首页/归档/标签/详情/友链/404/RSS/sitemap；支持自定义过滤器、`striptags`、`https_upgrade`、模板缓存预热
- **[DefaultCssGenerator.kt](file:///c:/Users/Lime/Desktop/test/APP/app/src/main/java/com/gridea/android/renderer/DefaultCssGenerator.kt)**：处理 `custom.css` 中的 `{{变量}}` 占位符替换，生成 `styles/main.css`
- **[MarkdownConverter.kt](file:///c:/Users/Lime/Desktop/test/APP/app/src/main/java/com/gridea/android/renderer/MarkdownConverter.kt)**：基于 Markwon 的 Markdown 转换
- **[CommentRenderer.kt](file:///c:/Users/Lime/Desktop/test/APP/app/src/main/java/com/gridea/android/renderer/CommentRenderer.kt)**：评论系统（Gitalk/Giscus/Disqus/Valine/Twikoo/Waline）渲染注入
- **[RenderData.kt](file:///c:/Users/Lime/Desktop/test/APP/app/src/main/java/com/gridea/android/renderer/RenderData.kt)**：渲染数据模型（Post/Tag/Menu/FriendLink/Pagination/PostStats 等）

#### 2.3.3 部署层（`deploy/`）

- **[Deployer.kt](file:///c:/Users/Lime/Desktop/test/APP/app/src/main/java/com/gridea/android/deploy/Deployer.kt)**：定义 `Deployer` 接口（`detect` + `publish`）
- **[DeployManager.kt](file:///c:/Users/Lime/Desktop/test/APP/app/src/main/java/com/gridea/android/deploy/DeployManager.kt)**：路由到 5 个平台部署器
- **[DeployService.kt](file:///c:/Users/Lime/Desktop/test/APP/app/src/main/java/com/gridea/android/deploy/DeployService.kt)**：Application 级单例，切页不中断部署

#### 2.3.4 UI 层（`ui/`）

- **`ui/theme/`**：Material3 主题（AccentColorScheme、NoticeManager 灵动岛）
- **`ui/navigation/`**：[Screen.kt](file:///c:/Users/Lime/Desktop/test/APP/app/src/main/java/com/gridea/android/ui/navigation/Screen.kt) 16 个路由
- **`ui/screen/`**：14 个 Screen + ViewModel（Home/Editor/Tags/Pages/Statistics/Trash/Preview/Deploy/Setting/ThemeHub/ThemeManager/Menu/FriendLink/Log/Onboarding）
- **`ui/component/`**：通用组件（ImagePickerSheet、MarkdownPreview、MarkdownToolbar、SelectionActionBar）

#### 2.3.5 DI 层（`di/`）

- **[DatabaseModule.kt](file:///c:/Users/Lime/Desktop/test/APP/app/src/main/java/com/gridea/android/di/DatabaseModule.kt)**：提供 Room 数据库 + 8 个迁移 + 5 个 DAO
- **[DataStoreModule.kt](file:///c:/Users/Lime/Desktop/test/APP/app/src/main/java/com/gridea/android/di/DataStoreModule.kt)**：提供 `DataStore<Preferences>` 单例

#### 2.3.6 工具层（`util/`）

- **[AppLogger.kt](file:///c:/Users/Lime/Desktop/test/APP/app/src/main/java/com/gridea/android/util/AppLogger.kt)**：全局日志（内存 500 条 + JSONL 文件，7 天自动清理）
- **[CrashHandler.kt](file:///c:/Users/Lime/Desktop/test/APP/app/src/main/java/com/gridea/android/util/CrashHandler.kt)**：全局崩溃捕获
- **[LocaleHelper.kt](file:///c:/Users/Lime/Desktop/test/APP/app/src/main/java/com/gridea/android/util/LocaleHelper.kt)**：多语言切换
- **ApkDownloader / UpdateChecker / BackupScheduler / FeedbackCollector / MarkdownEditorHelper / MarkdownUtils**

#### 2.3.7 桌面小部件（`widget/`）

- **NewPostWidgetProvider**：2×1 快捷新建文章
- **StatsWidgetProvider**：3×3 统计数据（总数/已发布/草稿/标签）
- **WidgetEntryPoint**：Hilt EntryPoint（BroadcastReceiver 无法 `@AndroidEntryPoint`）

### 2.4 关键类与函数

#### SiteRenderer — 渲染核心

```kotlin
@Singleton
class SiteRenderer @Inject constructor(
    settingRepository, postRepository, tagRepository,
    imageRepository, friendLinkRepository, menuRepository,
    themePackRepository, markdownConverter
) {
    fun renderAll(isPreview: Boolean, forceRebuild: Boolean): RenderResult
    // 增量构建：computeConfigHash + computeContentHash(MD5)
    // 缓存：.build_state.json 记录每篇文章渲染产物
    // 产物：index.html / archives / tags / post/{fileName} / atom.xml / sitemap.xml
}
```

#### Deployer 接口与实现

```kotlin
interface Deployer {
    fun detect(setting: Setting): DetectResult
    fun publish(setting: Setting, buildDir: File, onProgress: (DeployProgress) -> Unit): DeployResult
}
// 5 个实现：GithubDeployer / GiteeDeployer / SftpDeployer / NetlifyDeployer / VercelDeployer
```

| 部署器 | 认证 | 策略 | 特性 |
|-------|------|------|------|
| GithubDeployer | Bearer token | Contents API 逐文件 PUT | 1s 间隔防限流 |
| GiteeDeployer | token 参数 | PUT + DELETE | 404 增强 diagnose |
| SftpDeployer | 密码/私钥 | 全量替换 | JSch mwiede fork |
| NetlifyDeployer | Bearer token | SHA1 增量 | 仅上传缺失文件 |
| VercelDeployer | Bearer token | SHA 去重 | 替代 Gitee Pages |

#### GrideaApp（Compose 入口）

- 三态分支：null（预热动画）/ false（引导页）/ true（主界面）
- 5 主 Tab：Home、Tags、Deploy、ThemeHub、Setting（圆角 20dp 悬浮 NavigationBar）
- 横向手势切 Tab（阈值 80dp，350ms 导航锁）
- `PressableFloatingActionButton`：按下 0.85 缩放（50ms），松手 1.0 回弹（150ms，`DampingRatioMediumBouncy`）

### 2.5 资源与主题

#### res 资源

- [strings.xml](file:///c:/Users/Lime/Desktop/test/APP/app/src/main/res/values/strings.xml)：539 行中文 + [values-en](file:///c:/Users/Lime/Desktop/test/APP/app/src/main/res/values-en/strings.xml) 英文
- [shortcuts.xml](file:///c:/Users/Lime/Desktop/test/APP/app/src/main/res/xml/shortcuts.xml)：`new_post` 快捷方式
- [appwidget_new_post.xml](file:///c:/Users/Lime/Desktop/test/APP/app/src/main/res/xml/appwidget_new_post.xml) / [appwidget_stats.xml](file:///c:/Users/Lime/Desktop/test/APP/app/src/main/res/xml/appwidget_stats.xml)：桌面小部件配置
- `baseline-prof.txt`：Baseline Profile（配合 profileinstaller 提升 Release 首屏性能）

#### assets/themes 主题系统

每个主题含三件套：`theme.json`（元数据 + customConfig）+ `custom.css`（`{{变量}}` 占位符）+ `custom.js`（增强脚本）。模板文件使用 Pebble 4.x 引擎渲染，后缀为 `.peb`，包含 `base.peb`（基础布局）、`index.peb`（首页/分页）、`post.peb`（文章详情）、`archives.peb`（归档）、`tags.peb`（标签云）、`tag.peb`（标签详情）、`friends.peb`（友链）、`404.peb`（错误页）共 8 个模板 + `preview.jpg` 预览图。

> **重要**：每个主题必须同时适配手机端（≤768px）、平板端（769-1024px）、电脑端（>1024px）三端。

| 主题 | 风格 | 描述 |
|------|------|------|
| magazine（杂志编辑） | 杂志风 | 大图封面、网格布局、衬线标题、首字下沉 |
| retro（复古打字机） | 复古 | 米色纸张、打字机字体、虚线边框、光标闪烁 |
| masonry（瀑布流卡片） | 瀑布流 | Pinterest 风格、CSS columns、图片为主 |
| sidebar（经典双栏） | 双栏 | 固定侧边栏、桌面双栏、手机堆叠 |
| terminal（极客终端） | 极客 | 黑底绿字、等宽字体、CRT 扫描线 |
| ink（水墨中国风） | 中国风 | 宣纸纹理、楷书标题、朱砂红强调、印章标签 |

**控件类型与动态适配**：customConfig 支持 12 种内置控件（color/switch/select/slider/input/textarea/radio/number/code/multiselect/image/compound）。外部主题可声明自定义 type，通过 `fallback` 字段指定降级渲染的原子类型（未指定则降级 textarea），保证配置值始终可读写。`compound` 复合控件通过 `items` 数组组合多个原子控件（支持递归嵌套），值以 JSON 对象字符串存储。`ThemeConfigItem.effectiveType` 为数据层与 UI 层共用的有效类型解析入口。无需 APP 发版即可支持新控件。

### 2.6 运行方式

```bash
# 前置：配置 local.properties 指向 Android SDK
# sdk.dir=C\:\\Users\\Lime\\AppData\\Local\\Android\\Sdk

# 开发运行（连接设备/模拟器）
cd APP
./gradlew installDebug

# 构建 Release APK（启用 R8 + ProGuard）
./gradlew assembleRelease

# 产物位置
# APP/app/build/outputs/apk/debug/app-debug.apk
# APP/app/build/outputs/apk/release/app-release.apk
```

**AndroidManifest 权限**：INTERNET、ACCESS_NETWORK_STATE、READ_MEDIA_IMAGES、MANAGE_EXTERNAL_STORAGE、REQUEST_INSTALL_PACKAGES

---

## 三、gridea-pro — Wails 桌面应用

### 3.1 项目配置

**技术栈**：Go 1.25.5 + Wails v2.12.0 + Vue 3.6 + Vite（rolldown）

**模块信息**（[go.mod](file:///c:/Users/Lime/Desktop/test/gridea-pro/gridea-pro-1.2.2/go.mod)）：
- 模块名：`gridea-pro`
- 核心依赖：wails/v2、mcp-go、go-git/v5、goldmark、goja、pongo2、quickjs、sftp、go-keyring、selfupdate

**Wails 配置**（[wails.json](file:///c:/Users/Lime/Desktop/test/gridea-pro/gridea-pro-1.2.2/wails.json)）：
- `outputfilename` = "Gridea Pro"
- 前端目录 `./frontend`，dev server `http://localhost:5173`
- `wailsjsdir` = `./frontend/src`（自动生成绑定）

**作者**：Eliauk (tespera@foxmail.com)，版权 © 2026

### 3.2 整体架构

采用**分层架构**，前后端通过 Wails 绑定通信：

```
┌──────────────────────────────────────────────┐
│            Frontend (Vue 3 + Pinia)          │
│  MainLayout → 9 Views → Stores → Wails JS    │
├──────────────────────────────────────────────┤
│         Wails Binding (18 Facades)           │
├──────────────────────────────────────────────┤
│            Facade Layer (Go)                 │
│  AppServices → 18 XxxFacade                  │
├──────────────────────────────────────────────┤
│            Service Layer (Go)                │
│  14 Services + Engine + ResourceWatcher      │
├──────────────────────────────────────────────┤
│          Repository Layer (Go)               │
│  11 JSON Repositories + Keychain             │
├──────────────────────────────────────────────┤
│            Domain Layer (Go)                 │
│  Post/Tag/Menu/Link/Memo/Theme/...           │
└──────────────────────────────────────────────┘
```

**启动流程**（[boot.go](file:///c:/Users/Lime/Desktop/test/gridea-pro/gridea-pro-1.2.2/backend/pkg/boot/boot.go)）：
1. `main.go` 内嵌 `frontend/dist` → `boot.Run(assets, version)`
2. 初始化 `ConfigManager`（多站点配置，位于 `os.UserConfigDir()/Gridea Pro`）
3. `facade.NewAppServices(appDir, assets)` 装配 11 Repository + 14 Service + 18 Facade
4. `app.NewApp()` 创建应用核心
5. `wails.Run()` 注册 18 个绑定 + 原生菜单 + 文件服务中间件

### 3.3 后端模块（Go）

#### 3.3.1 应用核心 [app.go](file:///c:/Users/Lime/Desktop/test/gridea-pro/gridea-pro-1.2.2/backend/internal/app/app.go)

- **事件常量**：`EventAppReady` / `EventAppSiteLoaded` / `EventPreviewSite` / `EventShowPreferences` 等
- **`App` 结构体**：持有 ctx、appDir、buildDir、previewService、services、resourceWatcher
- **关键方法**：
  - `Startup(ctx)`：初始化站点 → 数据迁移 → 启动 ResourceWatcher → 注册事件 → 预启动预览服务
  - `LoadSite()`：**并行 8 路**加载（posts/categories/tags/menus/links/themes/themeConfig/setting），错误收集非 fail-fast
  - `switchToPath(newPath)`：多站点热切换（验证→初始化→加锁更新→重启预览+监听）
  - `ExportAsZip()`：渲染后打包 output 为 zip

#### 3.3.2 门面层 [facade/app.go](file:///c:/Users/Lime/Desktop/test/gridea-pro/gridea-pro-1.2.2/backend/internal/facade/app.go)

`AppServices` 是 Wails 绑定的核心容器，包装 18 个 Facade：

```
Category · Post · Menu · Link · Tag · Deploy · Renderer · Theme
Setting · Comment · Memo · Preview · SeoSetting · CdnSetting
PwaSetting · CdnUpload · AI · OAuth · Update · ImageHosting
```

**装配流程**：11 Repository → 审计标签/分类唯一性 → 14 Service（AI/凭证/OAuth 用应用级目录）→ 18 Facade

#### 3.3.3 领域模型 `domain/`

| 文件 | 核心结构 | 说明 |
|------|---------|------|
| `post.go` | `Post` + `PostRepository` | 自定义 UnmarshalJSON 兼容老版 `date` 字段 |
| `memo.go` | `Memo` + `MemoStats` + `MemoDashboardDTO` | 闪念（含热力图数据） |
| `theme.go` | `Theme` + `ThemeConfig` | 兼容老配置无 `katexEnabled` 时默认 true |
| `tag.go` | `Tag` + `TagRepository` | 含 Color 字段 |
| `menu.go` | `Menu` | 支持嵌套 Children |
| `link.go` | `Link` | 友链 |
| `site.go` | `SiteData` | 前端聚合视图模型 |
| `errors.go` | 13 个错误码 | 用户可见错误，前端 i18n 翻译 |

#### 3.3.4 引擎层 [engine/](file:///c:/Users/Lime/Desktop/test/gridea-pro/gridea-pro-1.2.2/backend/internal/engine)

**[engine.go](file:///c:/Users/Lime/Desktop/test/gridea-pro/gridea-pro-1.2.2/backend/internal/engine/engine.go)** — 渲染协调器：

```go
type Engine struct {
    appDir string
    dataBuilder       *TemplateDataBuilder
    pageRenderer      *PageRenderer
    seoGenerator      *SeoGenerator
    pwaGenerator      *PwaGenerator
    searchBuilder     *SearchIndexBuilder
    assetManager      *AssetManager
    renderer          render.ThemeRenderer  // Pongo2/EJS/Go
    // ...
}
```

**关键设计**：
- **single-flight + coalesce**：`RenderAll` 用 `atomic.Int32` 合并并发请求，N 个并发最多 2 次实际渲染
- **增量构建**：基于 manifest diff，首次 RemoveAll，后续保留用户自定义文件（CNAME/ads.txt）
- **并发渲染**：文章详情用 `errgroup` + `runtime.NumCPU()` 限流

**渲染流程**：清理 → 复制资源 → 构建数据 → 初始化后处理器 → 列表页 → 文章详情（并发）→ 独立任务（友链/闪念/404/search/feed/sitemap/robots/manifest/sw）→ CSS 合并压缩 → 孤儿文件清理

子模块：`asset_manager` / `data_builder` / `html_postprocessor` / `icon_generator` / `manifest` / `page_renderer` / `pwa_generator` / `search_builder` / `seo_generator` / `theme_config_service`

#### 3.3.5 配置层 [config.go](file:///c:/Users/Lime/Desktop/test/gridea-pro/gridea-pro-1.2.2/backend/internal/config/config.go)

- `AppName = "Gridea Pro"`
- `SiteEntry`：多站点条目（Name/Path/Active）
- `PlatformMeta`：平台连接元信息（非敏感，凭证在 Keychain）
- `AppConfig`：应用级配置（Sites + AISetting + PlatformMeta）
- 配置文件：`os.UserConfigDir()/Gridea Pro/config.json`（权限 0600）

#### 3.3.6 评论系统 [comment/](file:///c:/Users/Lime/Desktop/test/gridea-pro/gridea-pro-1.2.2/backend/internal/comment)

- **[base.go](file:///c:/Users/Lime/Desktop/test/gridea-pro/gridea-pro-1.2.2/backend/internal/comment/base.go)**：`BaseProvider`（HTTP 客户端 + 代理支持 + 状态码映射）
- 7 个 Provider：Disqus / GitHub(Gitalk/Giscus) / Twikoo / Valine / Waline / Cusdis
- `factory.go`：根据配置创建 Provider

#### 3.3.7 通知 [notify.go](file:///c:/Users/Lime/Desktop/test/gridea-pro/gridea-pro-1.2.2/backend/internal/notify/notify.go)

跨平台 OS 通知：
- macOS：CGO + NSUserNotification
- Windows：go-toast/v2，AUMID = `com.gridea.pro`
- Linux：D-Bus `org.freedesktop.Notifications`

#### 3.3.8 工具

- **[utils/slug.go](file:///c:/Users/Lime/Desktop/test/gridea-pro/gridea-pro-1.2.2/backend/internal/utils/slug.go)**：`SlugifyName`（中文转拼音）+ `ValidateSlug`（正则 `^[a-z0-9]+(-[a-z0-9]+)*$`）
- **[version/version.go](file:///c:/Users/Lime/Desktop/test/gridea-pro/gridea-pro-1.2.2/backend/internal/version/version.go)**：`var Version = "0.0.0-dev"`，CI 通过 `-ldflags` 注入

### 3.4 前端模块（Vue 3）

#### 3.4.1 入口与布局

- **[main.ts](file:///c:/Users/Lime/Desktop/test/gridea-pro/gridea-pro-1.2.2/frontend/src/main.ts)**：注册 Pinia/router/i18n，Prism 高亮，错误捕获
- **[App.vue](file:///c:/Users/Lime/Desktop/test/gridea-pro/gridea-pro-1.2.2/frontend/src/App.vue)**：Toaster + 错误兜底 + router-view
- **[MainLayout.vue](file:///c:/Users/Lime/Desktop/test/gridea-pro/gridea-pro-1.2.2/frontend/src/layouts/MainLayout.vue)**：三栏布局
  - 左侧 Sidebar（200px）：WindowControls + Logo + 9 项 nav + Preview/Publish 按钮
  - 主内容区：router-view + keep-alive
  - 右侧 Deploy Panel（380px）：进度条 + 日志流

#### 3.4.2 路由 [router/index.ts](file:///c:/Users/Lime/Desktop/test/gridea-pro/gridea-pro-1.2.2/frontend/src/router/index.ts)

`createWebHashHistory()`（Wails 桌面标配），9 个子路由：
`/articles` · `/comments` · `/memos` · `/menu` · `/tags` · `/categories` · `/links` · `/theme` · `/settings`

#### 3.4.3 状态管理（Pinia）

| Store | 文件 | 职责 |
|-------|------|------|
| site | [site.ts](file:///c:/Users/Lime/Desktop/test/gridea-pro/gridea-pro-1.2.2/frontend/src/stores/site.ts) | 站点全量数据（posts/tags/menus/themes/setting） |
| memo | [memo.ts](file:///c:/Users/Lime/Desktop/test/gridea-pro/gridea-pro-1.2.2/frontend/src/stores/memo.ts) | 闪念管理（三维过滤：关键词+时间+标签） |
| comment | [comment.ts](file:///c:/Users/Lime/Desktop/test/gridea-pro/gridea-pro-1.2.2/frontend/src/stores/comment.ts) | 评论（分页 + 客户端未读计算） |
| theme | [theme.ts](file:///c:/Users/Lime/Desktop/test/gridea-pro/gridea-pro-1.2.2/frontend/src/stores/theme.ts) | UI 主题（light/dark/system + 6 色彩） |

#### 3.4.4 视图组件

9 个视图目录，采用 `index.vue` + `components/` + `composables/` 分层：

| 视图 | 子组件 | Composables |
|------|--------|-------------|
| articles | editor/(4) + list/(5) | useArticleActions/Form/Stats + useArticleList/Selection |
| categories | CategoryCard/Editor | useCategory |
| comments | 6 平台配置 + CommentItem | - |
| links | LinkCard/Editor | useLink |
| memos | ContributionGraph/Input/Item/List | - |
| menu | MenuCard/Editor | useMenu |
| tags | TagCard/Editor | useTag |
| theme | Custom/Personalization/SiteInfo/ThemeSelection | - |
| settings | Basic/Cdn/ImageHosting/Pwa/Seo | - |

#### 3.4.5 国际化 [locales/](file:///c:/Users/Lime/Desktop/test/gridea-pro/gridea-pro-1.2.2/frontend/src/locales)

11 种语言：en / zh-CN / zh-TW / fr-FR / ru / ja-JP / es / pt-BR / de / ko / it

`getLanguage()` 优先级：LocalStorage → 浏览器语言精确匹配 → 中文映射 → 前缀匹配 → 默认 'en'

#### 3.4.6 Wails 运行时辅助 [wailsRuntime.ts](file:///c:/Users/Lime/Desktop/test/gridea-pro/gridea-pro-1.2.2/frontend/src/helpers/wailsRuntime.ts)

- `isWailsEnvironment()`：检测 `window.go` / `window.wails` / `window.runtime`
- `safeEventsOn/Emit/WindowShow`：非 Wails 环境（纯 Vite dev）安全降级

### 3.5 MCP 服务器

**独立二进制** `gridea-pro-mcp`，通过 stdio 与 AI 客户端通信，不依赖 GUI。

#### [server.go](file:///c:/Users/Lime/Desktop/test/gridea-pro/gridea-pro-1.2.2/backend/internal/mcp/server.go)

- `NewServer()` 装配服务，`Start()` 启动 stdio transport
- `GetAppDir()`：优先 `SOURCE_DIR` 环境变量，兼容 `GRIDEA_SOURCE_DIR`
- **注册 25+ 工具**：post(5) + memo(5) + tag/category/link/menu(各 4) + 主题(3) + 设置(2) + 评论(3) + 渲染(1) + 部署(opt-in 1)

#### [prompts.go](file:///c:/Users/Lime/Desktop/test/gridea-pro/gridea-pro-1.2.2/backend/internal/mcp/prompts.go)

5 个工作流提示词：
- `blog_writing_assistant` — 写作助手（自动调用 list_tags/categories + create_post）
- `memo_to_post` — 闪念整理成文
- `content_review` — 内容审查（SEO/标签/质量）
- `site_health_check` — 站点健康检查（🔴🟡🟢）
- `translate_post` — 文章翻译

#### [resources.go](file:///c:/Users/Lime/Desktop/test/gridea-pro/gridea-pro-1.2.2/backend/internal/mcp/resources.go)

3 个 MCP 资源：
- `gridea://site/info` — 站点基本信息
- `gridea://posts/summary` — 文章摘要列表
- `gridea://memos/recent` — 最近 20 条闪念

### 3.6 运行方式

```bash
cd gridea-pro/gridea-pro-1.2.2

# 前置：安装 Go 1.22+、Node.js、Wails CLI
go install github.com/wailsapp/wails/v2/cmd/wails@latest

# 开发（热重载，前端 5173 + 后端 34115）
wails dev
# 或
make dev

# 生产构建（同时构建 GUI + MCP 两个二进制）
make build
# 或
wails build

# 产物
# build/bin/Gridea Pro(.exe/.app)    — GUI 客户端
# build/bin/gridea-pro-mcp(.exe)      — MCP 独立二进制

# MCP 独立运行
SOURCE_DIR=/path/to/site build/bin/gridea-pro-mcp
```

**CI 发布**（[.github/workflows/release.yml](file:///c:/Users/Lime/Desktop/test/gridea-pro/gridea-pro-1.2.2/.github/workflows/release.yml)）：跨平台打包 macOS `.dmg` / Windows `.exe` NSIS / Linux `.AppImage` `.deb` `.rpm`

---

## 四、gridea-old — Electron 旧版应用

### 4.1 项目配置

**技术栈**：Electron 7.3.3 + Vue 2.6 + TypeScript 3 + vue-cli-plugin-electron-builder

**版本**：0.9.3（作者 EryouHao，MIT 协议）

**脚本**（[package.json](file:///c:/Users/Lime/Desktop/test/gridea-old/gridea-0.9.3/package.json)）：
```json
{
  "electron:build": "vue-cli-service electron:build",
  "electron:serve": "vue-cli-service electron:serve"
}
```

**核心依赖**：
- Markdown：markdown-it + 13 插件（katex/emoji/footnote/imsize/toc/task-lists 等）
- 博客生成：ejs（模板）+ feed（RSS）+ less（CSS 编译）
- Git 部署：isomorphic-git + hpagent（代理）
- SFTP：node-ssh + ssh2-sftp-client
- 数据存储：lowdb（JSON 文件数据库）
- 文章解析：gray-matter（Front Matter）
- 编辑器：monaco-markdown
- UI：ant-design-vue + vuedraggable + vee-validate
- 监控：@sentry/electron + electron-google-analytics
- 自动更新：electron-updater

### 4.2 整体架构

标准 **Electron 双进程**架构：

```
┌─────────────────────────────────────────────┐
│           主进程 (background.ts)             │
│  窗口管理 · IPC 监听 · 文件系统 ·            │
│  Express 预览服务器 · 自动更新 · Sentry      │
├─────────────────────────────────────────────┤
│              App (后端协调器)                │
│  Posts · Tags · Menus · Theme · Setting ·   │
│  Renderer · Deploy · 8 Events 类            │
├─────────────────────────────────────────────┤
│         渲染进程 (main.ts → Vue)            │
│  Ant Design Vue · Vuex · Vue Router ·       │
│  VueI18n · Monaco Editor                    │
└─────────────────────────────────────────────┘
```

**入口流程**：
1. [background.ts](file:///c:/Users/Lime/Desktop/test/gridea-old/gridea-0.9.3/src/background.ts)：初始化 Sentry → 注册 `app://` 协议 → 创建 BrowserWindow（1200×800）→ 构建应用菜单 → 启动 Express 预览服务器 → `new App()`
2. [main.ts](file:///c:/Users/Lime/Desktop/test/gridea-old/gridea-0.9.3/src/main.ts)：注册 Ant Design/i18n/VueBus → 初始化 Prism → 创建 Vue 实例
3. [App.vue](file:///c:/Users/Lime/Desktop/test/gridea-old/gridea-0.9.3/src/App.vue)：渲染 router-view + 外链处理

### 4.3 后端服务层

#### 4.3.1 应用主类 [app.ts](file:///c:/Users/Lime/Desktop/test/gridea-old/gridea-0.9.3/src/server/app.ts)

`App` 类是后端核心协调器：
- **属性**：mainWindow、appDir（默认 `~/Documents/gridea`）、buildDir（`~/.gridea/output`）、db、previewServer
- **方法**：
  - `loadSite()`：加载全部站点数据 → `updateStaticServer()` + `initEvents()`
  - `checkDir()`：初始化目录结构（.gridea 配置 + 源文件夹），旧版 `.hve-notes` 自动迁移
  - `initEvents()`：实例化 8 个事件类注册 IPC 监听

#### 4.3.2 基础模型 [model.ts](file:///c:/Users/Lime/Desktop/test/gridea-old/gridea-0.9.3/src/server/model.ts)

`Model` 基类：所有 Service 继承它，初始化 3 个 lowdb 数据库：
- `{appDir}/config/setting.json` → `$setting`
- `{appDir}/config/posts.json` → `$posts`
- `{appDir}/config/theme.json` → `$theme`

#### 4.3.3 服务类

| 文件 | 类 | 职责 |
|------|----|----|
| [posts.ts](file:///c:/Users/Lime/Desktop/test/gridea-old/gridea-0.9.3/src/server/posts.ts) | `Posts` | 文章 CRUD（.md + Front Matter），gray-matter 解析，图片上传 |
| [tags.ts](file:///c:/Users/Lime/Desktop/test/gridea-old/gridea-0.9.3/src/server/tags.ts) | `Tags` | 标签收集（used/unused）+ slug 生成 |
| [menus.ts](file:///c:/Users/Lime/Desktop/test/gridea-old/gridea-0.9.3/src/server/menus.ts) | `Menus` | 菜单 CRUD + 批量排序 |
| [setting.ts](file:///c:/Users/Lime/Desktop/test/gridea-old/gridea-0.9.3/src/server/setting.ts) | `Setting` | 基础/评论设置 + 头像/Favicon 上传 |
| [theme.ts](file:///c:/Users/Lime/Desktop/test/gridea-old/gridea-0.9.3/src/server/theme.ts) | `Theme` | 主题配置 + 自定义配置（支持 picture-upload/array 类型） |
| [renderer.ts](file:///c:/Users/Lime/Desktop/test/gridea-old/gridea-0.9.3/src/server/renderer.ts) | `Renderer` | 静态站点生成器（EJS + Less + style-override） |
| [deploy.ts](file:///c:/Users/Lime/Desktop/test/gridea-old/gridea-0.9.3/src/server/deploy.ts) | `Deploy` | Git 部署（GitHub/Coding/Gitee） |

#### 4.3.4 渲染器 Renderer

`renderAll()` 完整流程：
```
clearOutput → formatDataForRender → buildCss →
renderPostList(首页) → renderPostList(归档) →
renderTags → renderPostDetail → renderTagDetail →
copyFiles → renderCustomPage → buildCname → buildFeed
```

- **buildCss**：编译 less + 加载 `style-override.js`（主题样式覆盖函数）
- **buildFeed**：生成 `atom.xml` RSS
- 预览模式：domain = localhost:4000

#### 4.3.5 部署插件 `plugins/deploys/`

| 文件 | 类 | 平台 | 策略 |
|------|----|----|------|
| gitproxy.ts | `GitProxy` | - | isomorphic-git HTTP 代理（hpagent） |
| [netlify.ts](file:///c:/Users/Lime/Desktop/test/gridea-old/gridea-0.9.3/src/server/plugins/deploys/netlify.ts) | `NetlifyApi` | Netlify | SHA1 增量上传 |
| [sftp.ts](file:///c:/Users/Lime/Desktop/test/gridea-old/gridea-0.9.3/src/server/plugins/deploys/sftp.ts) | `SftpDeploy` | SFTP | 全量替换（node-ssh） |

#### 4.3.6 事件类 `events/`

8 个事件类注册 IPC 监听，桥接前端请求与后端 Service：

| 类 | 监听频道 |
|----|---------|
| SiteEvents | app-site-reload、app-source-folder-setting |
| PostEvents | app-post-create、app-post-delete、image-upload |
| MenuEvents | menu-delete、menu-save、menu-sort |
| TagEvents | tag-delete、tag-save |
| ThemeEvents | theme-save、theme-custom-config-save |
| RendererEvents | html-render |
| SettingEvents | setting-save、comment-setting-save、favicon-upload、avatar-upload |
| DeployEvents | site-publish、remote-detect |

#### 4.3.7 Markdown 插件 [markdown.ts](file:///c:/Users/Lime/Desktop/test/gridea-old/gridea-0.9.3/src/server/plugins/markdown.ts)

基于 markdown-it（html + breaks），集成 13 个插件，自定义 `validateLink` 防 XSS。

### 4.4 前端架构

#### 4.4.1 路由 [router.ts](file:///c:/Users/Lime/Desktop/test/gridea-old/gridea-0.9.3/src/router.ts)

```
/ → Main (父布局)
  ├── /articles → Articles
  ├── /menu → Menu
  ├── /tags → Tags
  ├── /theme → Theme
  ├── /setting → Setting
  └── /loading → Loading
```

#### 4.4.2 状态管理 [store/](file:///c:/Users/Lime/Desktop/test/gridea-old/gridea-0.9.3/src/store)

- `site` 模块：appDir、posts、tags、menus、themeConfig、setting、commentSetting
- mutations：`updateSite`、`updatePosts`

#### 4.4.3 组件 `components/`

| 组件 | 职责 |
|------|------|
| [Main.vue](file:///c:/Users/Lime/Desktop/test/gridea-old/gridea-0.9.3/src/components/Main.vue) | 主布局（侧边栏 + 预览/同步/版本检查） |
| MonacoMarkdownEditor | Monaco Markdown 编辑器（自定义 GrideaLight 主题） |
| ColorCard | 颜色选择（13 行预设色板） |
| EmojiCard | Emoji 选择器 |
| PostsCard | 文章卡片（主题配置用） |
| FooterBox | 底部固定容器 |
| AppSystem | 系统设置（语言/源文件夹/版本） |

#### 4.4.4 视图 `views/`

| 视图 | 职责 |
|------|------|
| [Articles.vue](file:///c:/Users/Lime/Desktop/test/gridea-old/gridea-0.9.3/src/views/article/Articles.vue) | 文章列表（批量删除/搜索/分页） |
| [ArticleUpdate.vue](file:///c:/Users/Lime/Desktop/test/gridea-old/gridea-0.9.3/src/views/article/ArticleUpdate.vue) | 文章编辑（全屏，统计/Emoji/图片/预览/设置抽屉） |
| [menu/Index.vue](file:///c:/Users/Lime/Desktop/test/gridea-old/gridea-0.9.3/src/views/menu/Index.vue) | 菜单管理（vuedraggable 拖拽） |
| [tags/Index.vue](file:///c:/Users/Lime/Desktop/test/gridea-old/gridea-0.9.3/src/views/tags/Index.vue) | 标签云管理 |
| [theme/Index.vue](file:///c:/Users/Lime/Desktop/test/gridea-old/gridea-0.9.3/src/views/theme/Index.vue) | 主题配置（4 Tab：基础/自定义/图标/头像） |
| [setting/Index.vue](file:///c:/Users/Lime/Desktop/test/gridea-old/gridea-0.9.3/src/views/setting/Index.vue) | 远程设置（2 Tab：基础/评论） |

#### 4.4.5 辅助函数 `helpers/`

| 文件 | 导出 | 职责 |
|------|------|------|
| analytics.ts | `Analytics` 单例 | Google Analytics（UA-113307620-4） |
| content-helper.ts | `ContentHelper` | 图片 URL 本地/线上互转 |
| slug.ts | `createSlug` | URL slug 生成（3 种模式） |
| words-count.ts | `wordCount`/`timeCalc` | 中英文字数与阅读时间 |
| constants.ts | 常量 | 默认分页/路径 |
| shortcut-keys.ts | 数组 | 编辑器快捷键说明 |

### 4.5 运行方式

```bash
cd gridea-old/gridea-0.9.3

# 安装依赖
yarn install
# 或 npm install

# 开发模式（Electron + Webpack 热重载）
yarn electron:serve

# 构建应用（Windows NSIS / macOS dmg / Linux AppImage+deb+snap）
yarn electron:build

# 配置说明
# vue.config.js: nodeIntegration:true, asar:false, publish:['github']
# 开发预览服务器端口 4000
```

**用户数据存储**：
- 全局配置：`~/.gridea/config.json`（源文件夹路径）
- 构建输出：`~/.gridea/output/`
- 源文件夹（默认 `~/Documents/gridea/`）：config/ + posts/ + post-images/ + themes/ + static/

---

## 五、跨项目共性设计

尽管三个子项目技术栈不同，但共享以下**产品设计理念**：

### 5.1 静态站点生成流程

三者均遵循相同的核心流程：

```
读取文章(Markdown + Front Matter)
  → 渲染 Markdown 为 HTML
  → 计算字数/阅读时间/摘要/TOC
  → 使用模板引擎生成页面(首页/归档/标签/详情/友链/404)
  → 编译主题样式(CSS/Less)
  → 复制静态资源
  → 生成 RSS/sitemap/CNAME
  → 输出到构建目录
```

| 项目 | 模板引擎 | Markdown 引擎 | 增量构建 |
|------|---------|--------------|---------|
| APP | Pebble 4.x（`.peb` 模板） | Markwon | ✅ MD5 content hash |
| gridea-pro | Pongo2/EJS/Go Templates | goldmark | ✅ manifest diff |
| gridea-old | EJS + Less | markdown-it（13 插件） | ❌ 全量渲染 |

### 5.2 主题系统

三者均支持主题自定义配置（`customConfig`），通过配置项动态生成样式：

| 项目 | 主题配置类型 | 样式覆盖机制 | 模板引擎 | 主题包格式 |
|------|------------|------------|---------|-----------|
| APP | `theme.json` customConfig 数组（12 种控件：color/switch/select/slider/input/textarea/radio/number/code/multiselect/image/compound，支持 fallback 智能降级） | `{{变量}}` 占位符替换 | Pebble 4.x（`.peb`） | `.zip`（根目录平铺） |
| gridea-pro | 主题 `config.json` customConfig | style-override + 后处理 | Pongo2/EJS/Go Templates | 目录 |
| gridea-old | 主题 `config.json` customConfig | `style-override.js` 函数 | EJS + Less | 目录 |

> **Gridea Android 主题统计字段统一**：所有主题的统计组（group=统计）必须包含四项配置：`ga`（Google Analytics）、`baidu`（百度统计 Token）、`tencent`（腾讯分析 ID）、`view`（不蒜子访客）。

### 5.3 多平台部署

| 平台 | APP | gridea-pro | gridea-old |
|------|-----|-----------|-----------|
| GitHub Pages | ✅ Contents API | ✅ go-git | ✅ isomorphic-git |
| Gitee Pages | ✅ | - | ✅ |
| Coding Pages | - | - | ✅ |
| SFTP | ✅ JSch | ✅ pkg/sftp | ✅ node-ssh |
| Netlify | ✅ SHA1 增量 | ✅ | ✅ SHA1 增量 |
| Vercel | ✅ SHA 去重 | ✅ | - |
| FTP | - | ✅ jlaffaye/ftp | - |

### 5.4 评论系统集成

| 评论系统 | APP | gridea-pro | gridea-old |
|---------|-----|-----------|-----------|
| Gitalk | ✅ | ✅ | ✅ |
| Giscus | ✅ | ✅ | - |
| Disqus | ✅ | ✅ | ✅ |
| Valine | ✅ | ✅ | - |
| Twikoo | ✅ | ✅ | - |
| Waline | ✅ | ✅ | - |
| Cusdis | - | ✅ | - |

---

## 六、依赖关系总览

### 6.1 APP（Android）依赖关系

```
MainActivity
  └─ SettingViewModel (themeMode/languageMode/fontSizeScale)
  └─ BackupScheduler
  └─ GrideaAndroidTheme → GrideaApp (Compose)
       ├─ OnboardingViewModel
       └─ GrideaAppContent (NavHost)
            ├─ HomeScreen/EditorScreen/TagsScreen/...
            ├─ DeployViewModel → DeployService (Singleton)
            │    ├─ SiteRenderer.renderAll
            │    │    ├─ MarkdownConverter (Markwon)
            │    │    ├─ PebbleTemplateEngine（Pebble 4.x）
            │    │    └─ 7 Repositories
            │    ├─ DeployManager.publish
            │    │    └─ 5 Deployers
            │    └─ SiteOutputRepository
            └─ NoticeManager (灵动岛)

GrideaApp (Application)
  ├─ AppLogger.init / cleanExpiredLogs
  ├─ CrashHandler.install
  └─ SiteOutputRepository.ensureOutputDir

DatabaseModule (Hilt)
  └─ GrideaDatabase (Room v8)
       ├─ 5 DAO + 5 Entity
       └─ 8 Migrations
```

### 6.2 gridea-pro 依赖关系

```
main.go
  └─ boot.Run(assets, version)
       ├─ config.NewConfigManager()  ← 应用级配置
       ├─ facade.NewAppServices(appDir, assets)
       │    ├─ 11 Repositories (JSON)
       │    │    └─ AuditTagUniqueness / AuditCategoryUniqueness
       │    ├─ credential.New(appConfigDir)  ← 系统 Keychain
       │    ├─ 14 Services
       │    │    └─ engine.New(appDir, repos...)
       │    │         ├─ ThemeConfigService
       │    │         ├─ TemplateDataBuilder
       │    │         ├─ PageRenderer
       │    │         ├─ SeoGenerator / PwaGenerator / SearchIndexBuilder
       │    │         ├─ AssetManager
       │    │         └─ render.ThemeRenderer (Pongo2/EJS/Go)
       │    └─ 18 Facades
       ├─ app.NewApp(appDir, services, version)
       │    └─ ResourceWatcher (fsnotify) + DataMigrator
       └─ wails.Run(Bind: [18 facades])

MCP 独立二进制 (cmd/mcp/main.go)
  └─ mcp.NewServer() → 复用 repository + service + engine
       └─ 注册 25+ tools / 3 resources / 5 prompts
       └─ server.ServeStdio()
```

### 6.3 gridea-old 依赖关系

```
background.ts (主进程)
  ├─ Sentry.init
  ├─ createWindow (BrowserWindow)
  ├─ initServer (Express 预览, port 4000)
  └─ new App({ mainWindow, app, baseDir, previewServer })
       ├─ Model (基类)
       │    └─ lowdb ($setting / $posts / $theme)
       ├─ Posts / Tags / Menus / Theme / Setting / Renderer / Deploy
       └─ initEvents → 8 Events 类 (IPC 监听)
            └─ ipcMain.on(channel, handler)

main.ts (渲染进程)
  └─ Vue 实例
       ├─ Ant Design Vue / VueI18n / VueBus / VueShortkey
       ├─ Vuex (site 模块)
       ├─ Vue Router (6 路由)
       └─ Components: Main / MonacoMarkdownEditor / ColorCard / ...
```

### 6.4 前后端通信机制对比

| 项目 | 通信方式 | 典型调用 |
|------|---------|---------|
| APP | ViewModel → Repository → DAO（同进程） | `postRepository.getPosts()` |
| gridea-pro | Wails 绑定（前端 import → Go Facade） | `await DeployToGit()` |
| gridea-old | Electron IPC（ipcRenderer → ipcMain） | `ipcRenderer.send('app-post-create')` |

---

## 七、构建与发布

### 7.1 APP（Android）

```bash
cd APP
./gradlew assembleRelease
# 产物: app/build/outputs/apk/release/app-release.apk
```

- Release 启用 R8 + ProGuard（[proguard-rules.pro](file:///c:/Users/Lime/Desktop/test/APP/app/proguard-rules.pro)）
- Baseline Profile + profileinstaller 提升 AOT 编译性能
- 多 dex 自动启用（方法数超 65535）

### 7.2 gridea-pro（Wails）

```bash
cd gridea-pro/gridea-pro-1.2.2
make build
# 产物: build/bin/Gridea Pro + build/bin/gridea-pro-mcp
```

- CI 跨平台打包（[release.yml](file:///c:/Users/Lime/Desktop/test/gridea-pro/gridea-pro-1.2.2/.github/workflows/release.yml)）
- 版本注入：`-ldflags="-X gridea-pro/backend/internal/version.Version=${VERSION}"`
- macOS `.dmg` / Windows `.exe` NSIS / Linux `.AppImage` `.deb` `.rpm`

### 7.3 gridea-old（Electron）

```bash
cd gridea-old/gridea-0.9.3
yarn electron:build
# 产物: dist/ 下平台安装包
```

- electron-builder 打包（NSIS / dmg / AppImage+deb+snap）
- `asar: false`，`publish: ['github']` 发布到 GitHub Releases
- 自动更新：electron-updater

---

## 附录：关键文件路径速查

### APP（Android）

| 模块 | 路径 |
|------|------|
| Application 入口 | [GrideaApp.kt](file:///c:/Users/Lime/Desktop/test/APP/app/src/main/java/com/gridea/android/GrideaApp.kt) |
| MainActivity | [MainActivity.kt](file:///c:/Users/Lime/Desktop/test/APP/app/src/main/java/com/gridea/android/MainActivity.kt) |
| Compose 入口 | [ui/GrideaApp.kt](file:///c:/Users/Lime/Desktop/test/APP/app/src/main/java/com/gridea/android/ui/GrideaApp.kt) |
| 渲染核心 | [renderer/SiteRenderer.kt](file:///c:/Users/Lime/Desktop/test/APP/app/src/main/java/com/gridea/android/renderer/SiteRenderer.kt) |
| 模板引擎 | [renderer/PebbleTemplateEngine.kt](file:///c:/Users/Lime/Desktop/test/APP/app/src/main/java/com/gridea/android/renderer/PebbleTemplateEngine.kt) |
| 部署接口 | [deploy/Deployer.kt](file:///c:/Users/Lime/Desktop/test/APP/app/src/main/java/com/gridea/android/deploy/Deployer.kt) |
| 部署服务 | [deploy/DeployService.kt](file:///c:/Users/Lime/Desktop/test/APP/app/src/main/java/com/gridea/android/deploy/DeployService.kt) |
| DI 模块 | [di/DatabaseModule.kt](file:///c:/Users/Lime/Desktop/test/APP/app/src/main/java/com/gridea/android/di/DatabaseModule.kt) |
| 日志 | [util/AppLogger.kt](file:///c:/Users/Lime/Desktop/test/APP/app/src/main/java/com/gridea/android/util/AppLogger.kt) |
| 崩溃处理 | [util/CrashHandler.kt](file:///c:/Users/Lime/Desktop/test/APP/app/src/main/java/com/gridea/android/util/CrashHandler.kt) |
| 构建配置 | [app/build.gradle.kts](file:///c:/Users/Lime/Desktop/test/APP/app/build.gradle.kts) |
| 版本目录 | [gradle/libs.versions.toml](file:///c:/Users/Lime/Desktop/test/APP/gradle/libs.versions.toml) |

### gridea-pro（Wails）

| 模块 | 路径 |
|------|------|
| 程序入口 | [main.go](file:///c:/Users/Lime/Desktop/test/gridea-pro/gridea-pro-1.2.2/main.go) |
| 启动装配 | [backend/pkg/boot/boot.go](file:///c:/Users/Lime/Desktop/test/gridea-pro/gridea-pro-1.2.2/backend/pkg/boot/boot.go) |
| 应用核心 | [backend/internal/app/app.go](file:///c:/Users/Lime/Desktop/test/gridea-pro/gridea-pro-1.2.2/backend/internal/app/app.go) |
| Facade 容器 | [backend/internal/facade/app.go](file:///c:/Users/Lime/Desktop/test/gridea-pro/gridea-pro-1.2.2/backend/internal/facade/app.go) |
| 渲染引擎 | [backend/internal/engine/engine.go](file:///c:/Users/Lime/Desktop/test/gridea-pro/gridea-pro-1.2.2/backend/internal/engine/engine.go) |
| MCP 服务器 | [backend/internal/mcp/server.go](file:///c:/Users/Lime/Desktop/test/gridea-pro/gridea-pro-1.2.2/backend/internal/mcp/server.go) |
| 配置管理 | [backend/internal/config/config.go](file:///c:/Users/Lime/Desktop/test/gridea-pro/gridea-pro-1.2.2/backend/internal/config/config.go) |
| 前端入口 | [frontend/src/main.ts](file:///c:/Users/Lime/Desktop/test/gridea-pro/gridea-pro-1.2.2/frontend/src/main.ts) |
| 主布局 | [frontend/src/layouts/MainLayout.vue](file:///c:/Users/Lime/Desktop/test/gridea-pro/gridea-pro-1.2.2/frontend/src/layouts/MainLayout.vue) |
| Go 模块 | [go.mod](file:///c:/Users/Lime/Desktop/test/gridea-pro/gridea-pro-1.2.2/go.mod) |
| Wails 配置 | [wails.json](file:///c:/Users/Lime/Desktop/test/gridea-pro/gridea-pro-1.2.2/wails.json) |

### gridea-old（Electron）

| 模块 | 路径 |
|------|------|
| 主进程入口 | [src/background.ts](file:///c:/Users/Lime/Desktop/test/gridea-old/gridea-0.9.3/src/background.ts) |
| 渲染进程入口 | [src/main.ts](file:///c:/Users/Lime/Desktop/test/gridea-old/gridea-0.9.3/src/main.ts) |
| 后端协调器 | [src/server/app.ts](file:///c:/Users/Lime/Desktop/test/gridea-old/gridea-0.9.3/src/server/app.ts) |
| 渲染器 | [src/server/renderer.ts](file:///c:/Users/Lime/Desktop/test/gridea-old/gridea-0.9.3/src/server/renderer.ts) |
| Git 部署 | [src/server/deploy.ts](file:///c:/Users/Lime/Desktop/test/gridea-old/gridea-0.9.3/src/server/deploy.ts) |
| Markdown | [src/server/plugins/markdown.ts](file:///c:/Users/Lime/Desktop/test/gridea-old/gridea-0.9.3/src/server/plugins/markdown.ts) |
| 主布局 | [src/components/Main.vue](file:///c:/Users/Lime/Desktop/test/gridea-old/gridea-0.9.3/src/components/Main.vue) |
| 文章编辑 | [src/views/article/ArticleUpdate.vue](file:///c:/Users/Lime/Desktop/test/gridea-old/gridea-0.9.3/src/views/article/ArticleUpdate.vue) |
| 项目配置 | [package.json](file:///c:/Users/Lime/Desktop/test/gridea-old/gridea-0.9.3/package.json) |

---

## 八、最新更新日志（v0.1.0+）

### 8.1 查找替换高亮（EditorScreen.kt）

在编辑器中实现 IDE 风格的查找替换体验：

- **`SearchHighlightTransformation`**（[EditorScreen.kt:1548](file:///c:/Users/Lime/Desktop/test/APP/app/src/main/java/com/gridea/android/ui/screen/editor/EditorScreen.kt)）：基于 `VisualTransformation` 实现 TextField 文本背景高亮
  - 当前匹配项：`Color(0x50FFC107)` 深黄背景 + 斜体
  - 其他匹配项：`Color(0x28FFEB3B)` 浅黄背景
- **`windowedMatches()`**（[EditorScreen.kt:1528](file:///c:/Users/Lime/Desktop/test/APP/app/src/main/java/com/gridea/android/ui/screen/editor/EditorScreen.kt)）：统计匹配位置（不重叠）
- **匹配定位**：监听 `searchMatchIndex` 变化，设置 `TextFieldValue.selection` 让 TextField 自动滚动到当前匹配位置
- **防抖**：220ms 延迟搜索，避免长文章每次按键卡顿

### 8.2 WebView 预览导航修复（PreviewScreen.kt）

修复返回上一页/前进下一页按钮失效问题，对齐浏览器导航语义：

- 移除 `clearHistory()` 调用：保留历史栈，避免清空后无法返回
- `shouldOverrideUrlLoading` 改用 `view?.post { view.loadUrl(targetUrl) }` 异步加载：同步 `loadUrl` 在拦截器内调用会导致历史项被替换而非追加
- 新增 `canGoBack`/`canGoForward` 状态：在 `onPageFinished` 同步，按钮点击后用 `post {}` 异步刷新
- 按钮添加 `enabled` 条件：无历史时禁用并降低图标透明度（`alpha = 0.38f`）

### 8.3 日志问题修复

针对 [feedback_temp/logs/](file:///c:/Users/Lime/Desktop/test/feedback_temp/logs/) 中的告警与错误：

| 问题 | 文件 | 修复方式 |
|------|------|---------|
| UpdateChecker HTTP 404 抛异常 | [UpdateChecker.kt:60-77](file:///c:/Users/Lime/Desktop/test/APP/app/src/main/java/com/gridea/android/util/UpdateChecker.kt) | 404 时返回 `UpdateInfo(hasUpdate=false)` 而非抛异常 |
| Editor `JobCancellationException` 误报 | [EditorViewModel.kt:550-557](file:///c:/Users/Lime/Desktop/test/APP/app/src/main/java/com/gridea/android/ui/screen/editor/EditorViewModel.kt) | `catch (CancellationException) { throw e }` 按协程规范重抛 |
| `scripts/custom.js` ERR_FILE_NOT_FOUND | [SiteRenderer.kt:185-199](file:///c:/Users/Lime/Desktop/test/APP/app/src/main/java/com/gridea/android/renderer/SiteRenderer.kt) | 始终生成 custom.js（即使主题无 JS 也写入占位注释） |
| Bilibili iframe `file://player.bilibili.com` ERR_INVALID_URL | [MarkdownConverter.kt:121-128](file:///c:/Users/Lime/Desktop/test/APP/app/src/main/java/com/gridea/android/renderer/MarkdownConverter.kt) | 协议相对 URL `//domain.com` → `https://domain.com` 转换 |
| `TemplateEngine.formatIso8601` Date? 类型推断 | [TemplateEngine.kt:428-440](file:///c:/Users/Lime/Desktop/test/APP/app/src/main/java/com/gridea/android/renderer/TemplateEngine.kt) | 显式 `?: return date` 处理 `parse` 返回的 nullable |

### 8.4 代码瘦身

- **移除 4 个未使用 import**：`GrideaApp.kt` 的 `slideInHorizontally`/`slideOutHorizontally`、`SiteRenderer.kt` 的 `ThemeAsset`、`LogManagerScreen.kt` 的 `CheckCircle`
- **移除 1 个未使用私有函数**：`TagRepository.kt` 的 `toTag()`（已被 `getAllList()` 内联实现替代）
- **修复 11 个编译警告**：将 `Icons.Filled.Sort/List/Article/OpenInNew/MenuOpen` 替换为 `Icons.AutoMirrored.Filled.*`；`LocalLifecycleOwner` 从 `androidx.compose.ui.platform` 迁移至 `androidx.lifecycle.compose`；补全 `values/strings.xml` 中缺失的 `setting_site_url_format_default`/`setting_site_url_format_slug` 默认值
- **构建状态**：`compileDebugKotlin` 零警告，`assembleDebug` 零错误零警告

---

> **文档说明**：本 Wiki 基于代码库静态分析生成，覆盖三个子项目的架构、模块、关键类、依赖与运行方式。所有文件路径均为绝对路径，可点击跳转。如需了解特定功能的实现细节，可定位到对应文件后进一步阅读源码与注释。
