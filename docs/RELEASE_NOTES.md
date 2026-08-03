# Gridea Android v0.2.0

> 修复一加设备编辑器预览正文不显示、致谢署名错误等关键问题，优化版本历史 UI 与下载体验。

---

## 🐛 Bug 修复

### 一加手机编辑器预览正文不显示（关键修复）
- **问题**：一加手机上在文章编辑页面输入文字后点击"预览"Tab，只能看到标题，正文区域空白
- **根因**：`MarkdownPreview.kt` 中渲染正文的 `AndroidView` 缺少 `modifier = Modifier.fillMaxWidth()`，且 `TextView` 未设置 `layoutParams`（默认 WRAP_CONTENT）。在 `verticalScroll` + `AnimatedContent` 的 `scaleIn/scaleOut` 动画组合下，AndroidView 测量异常导致正文高度为 0。标题用 Compose `Text` 渲染不受影响，所以标题能显示
- **修复**：给 `AndroidView` 添加 `modifier = Modifier.fillMaxWidth()`，给 `TextView` 显式设置 `layoutParams = MATCH_PARENT × WRAP_CONTENT`

### 致谢栏作者署名修复
- **问题**：关于页面致谢栏中 Gridea Pro 版本作者被错误署名为 "Vega"
- **修复**：更正为正确的作者名 "Tespera"，并补充英文版致谢内容中缺失的对应条目

---

## ✨ 功能优化

### 版本历史界面圆角卡片
- **优化前**：版本历史列表项使用默认 12dp 圆角、1dp 阴影、0.5f 透明度，与项目其他卡片风格不一致
- **优化后**：统一为 16dp 圆角、0dp 扁平阴影、0.4f 透明度，与设置页、文章列表等卡片风格保持一致
- 移除卡片间多余的 `HorizontalDivider` 分隔线，改为 10dp 间距留白
- 内部 padding 从 12dp 增至 16dp，留白更舒适

### 主题包下载改用 GitHub Release
- **优化前**：主题包存放在站点 `Download/` 目录，部署到 Vercel/EdgeOne 后受流量限制，下载速度慢
- **优化后**：主题画廊下载按钮改为指向 GitHub Release 的 assets 下载链接，由 GitHub CDN 提供加速，不受部署平台流量限制

### WebView 兼容性增强
- 禁用 WebView `forceDark` 强制暗色反转，避免一加/OPPO/ColorOS 系统暗色模式下预览页面被二次反转
- 启用 `mixedContentMode = ALWAYS_ALLOW`，允许主题脚本引用外部资源
- 启用 `databaseEnabled`，支持使用 IndexedDB/WebSQL 的主题脚本

---

## 📋 更新检测规则说明

应用内「设置 → 检查更新」通过 GitHub Releases API 检测新版本，规则如下：

- **API**：`https://api.github.com/repos/QingYu327/GrideaAndroid/releases/latest`
- **对比字段**：Release 的 `tag_name`（去掉 `v` 前缀）与 `BuildConfig.VERSION_NAME` 按 `.` 分段逐段比较数字大小
- **触发条件**：`tag_name` 严格大于 `VERSION_NAME` 时才提示更新（相等或更小都不触发）
- **示例**：当前版本 `0.1.0`，Release tag 设为 `v0.2.0` → 提示更新；Release tag 设为 `v0.1.0` → 不提示

> **注意**：`/releases/latest` 接口返回的是最新非预发布 Release。更改已有 Release 的标签后，GitHub API 可能有缓存延迟。推荐做法是**创建新的 Release** 并设置更高的 tag，而非修改已有 Release 的标签。

---

## 🎨 主题控件类型说明

外部主题通过 `theme.json` 的 `customConfig` 数组声明配置项，支持以下 **12 种内置控件类型**：

| 控件类型 | 说明 | 适用场景 |
|---------|------|---------|
| `input` | 单行文本 | 标题、副标题、自定义文案 |
| `textarea` | 多行文本 | 副标题、长文案 |
| `switch` | 开关 | 布尔型选项 |
| `color` | 颜色选择器 | 主题色、背景色 |
| `select` | 下拉单选 | 单选预设值 |
| `radio` | 单选按钮组 | 单选预设值 |
| `multiselect` | 多选标签 | 启用的社交图标（逗号分隔） |
| `number` | 数字输入 | 精确数值 |
| `slider` | 滑块 | 数值范围调节 |
| `code` | 代码编辑 | 自定义 CSS/JS |
| `image` | 图片 URL | 头像、封面图 |
| `compound` | 复合控件 | 多字段组合（如渐变起止色） |

### 动态适配：自定义控件无需 APP 发版

外部主题可声明自定义 `type`，通过两种机制实现动态适配，配置值始终可读写：

1. **fallback 智能降级**：自定义 `type` 通过 `fallback` 字段指定降级渲染的原子类型（如 `"color"`/`"textarea"`）。APP 遇到未知类型时优先使用 fallback 渲染；未指定则统一降级为 `textarea`。

2. **compound 复合控件**：通过 `items` 数组组合多个原子控件（支持递归嵌套），`layout` 指定布局（`row`/`column`）。值以 JSON 对象字符串存储，如 `{"start":"#fff","end":"#000"}`，可在 `custom.js` 中 `JSON.parse()` 解析。

> 详见 `THEME_DEVELOPMENT_GUIDE.md` 第 4.4 节「动态适配：compound 复合控件与 fallback 智能降级」。

---

## 📥 下载

- GitHub Release 附件：`app-release.apk`
- 应用内「设置 → 检查更新」可自动检测新版本

> 系统要求：Android 7.0（API 24）及以上

---

## 🙏 致谢

- [Gridea](https://github.com/getgridea/gridea) — 原项目作者 EryouHao
- [Gridea Pro](https://github.com/vegaaltair/gridea-pro) — Pro 版本作者 Tespera
- 所有开源项目的贡献者

---

## 📄 许可证

GPL-3.0，详见 [LICENSE](./LICENSE)。
