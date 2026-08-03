# Gridea 主题开发完整流程指南

> **文档目标**：为人类开发者和 AI 助手提供一份准确、可操作的 Gridea 主题开发规范。按照本文档操作可高成功率制作出合格主题。
>
> **适用范围**：Gridea Android 客户端（基于 Pebble 4.x 模板引擎）。所有规范均来自源代码 `SiteRenderer.kt`、`PebbleTemplateEngine.kt`、`ThemePackRepository.kt` 及现有原型主题的实际代码。

---

## 目录

1. [概述](#1-概述)
2. [主题包结构](#2-主题包结构)
3. [快速开始](#3-快速开始)
4. [theme.json 规范](#4-themejson-规范)
5. [Pebble 模板开发](#5-pebble-模板开发)
6. [页面模板详解](#6-页面模板详解)
7. [CSS 开发规范](#7-css-开发规范)
8. [JavaScript 开发规范](#8-javascript-开发规范)
9. [路径与分页](#9-路径与分页)
10. [多端适配检查清单](#10-多端适配检查清单)
11. [常见陷阱与解决方案](#11-常见陷阱与解决方案)
12. [测试清单](#12-测试清单)
13. [打包与发布](#13-打包与发布)
14. [AI 助手开发指引](#14-ai-助手开发指引)

---

## 1. 概述

Gridea 是一个静态博客系统，主题负责控制站点的视觉呈现与交互。主题系统具有以下特点：

- **模板引擎**：使用 Pebble 4.x（Java 模板引擎），模板文件后缀为 `.peb`
- **配置体系**：通过 `theme.json` 声明可配置项，用户在 APP 内可视化调整
- **静态输出**：渲染后生成纯静态 HTML/CSS/JS，部署到任意静态服务器
- **相对路径**：通过 `<base href>` 标签解析相对路径，兼容 `file://` 预览和 HTTP 部署
- **多端适配**：必须同时适配手机（≤768px）、平板（769-1024px）、电脑（>1024px）三端

### 渲染流程简述

1. APP 读取 `theme.json` 解析主题元数据和配置项
2. 将 Markdown 文章转为 HTML
3. 用主题配置值替换 `custom.css` 中的 `{{变量}}` 占位符，生成 `styles/main.css`
4. 处理 `custom.js`（剥离误粘贴的 `<script>` 标签、替换占位符）
5. 通过 Pebble 引擎渲染各页面 `.peb` 模板为 HTML
6. 复制图片资源、生成 RSS/sitemap/robots.txt 等

---

## 2. 主题包结构

一个完整的主题包包含以下文件：

```
my-theme/
├── theme.json        # 主题元数据与配置项声明（必需）
├── custom.css        # 主题样式，支持 {{变量}} 占位符（必需）
├── custom.js         # 主题脚本，IIFE 结构（必需，可为空注释）
├── base.peb          # 基础布局模板（必需）
├── index.peb         # 首页/分页模板（必需）
├── post.peb          # 文章详情模板（必需）
├── archives.peb      # 归档页模板（必需）
├── tags.peb          # 标签总览页模板（必需）
├── tag.peb           # 标签详情页模板（必需）
├── friends.peb       # 友链页模板（必需）
├── 404.peb           # 404 错误页模板（必需）
└── preview.jpg       # 主题预览图（必需）
```

### 各文件职责

| 文件 | 作用 | 是否必需 |
|------|------|----------|
| `theme.json` | 声明主题 id、名称、作者、配置项 | ✅ 必需 |
| `custom.css` | 所有样式，支持 `{{变量}}` 占位符替换 | ✅ 必需 |
| `custom.js` | 交互逻辑，IIFE 结构，避免全局污染 | ✅ 必需 |
| `base.peb` | 基础骨架：`<html>`、`<head>`、页头、页脚、通用组件 | ✅ 必需 |
| `index.peb` | 首页文章列表与分页 | ✅ 必需 |
| `post.peb` | 单篇文章详情 | ✅ 必需 |
| `archives.peb` | 按年份归档的文章列表 | ✅ 必需 |
| `tags.peb` | 所有标签的云图 | ✅ 必需 |
| `tag.peb` | 某标签下的文章列表 | ✅ 必需 |
| `friends.peb` | 友情链接页 | ✅ 必需 |
| `404.peb` | 404 错误页 | ✅ 必需 |
| `preview.jpg` | 主题预览缩略图（导入时展示） | ✅ 必需 |

> **重要**：`.peb` 文件可以直接放在主题包根目录。若存在 `templates/` 子目录，引擎会优先从该目录加载。两种方式都支持，推荐根目录布局（更简单）。

---

## 3. 快速开始

### 3.1 创建主题目录

在本地创建一个文件夹，命名为你的主题 id（如 `my-theme`）。

### 3.2 编写 theme.json

```json
{
  "id": "my-theme",
  "name": "我的主题",
  "version": "1.0.0",
  "author": "你的名字",
  "description": "主题的一句话描述",
  "previewImage": null,
  "tags": ["简约", "博客"],
  "isBuiltin": false,
  "customConfig": [
    {
      "name": "accent_color",
      "label": "强调色",
      "group": "颜色",
      "type": "color",
      "value": "#42b983",
      "note": "用于链接、按钮等交互元素"
    },
    {
      "name": "show_hero",
      "label": "首页大标题",
      "group": "排版",
      "type": "switch",
      "value": false,
      "note": "在首页顶部显示站点名称和描述"
    },
    {
      "name": "ga",
      "label": "Google Analytics",
      "group": "统计",
      "type": "input",
      "value": "",
      "placeholder": "G-XXXXXXXXXX"
    },
    {
      "name": "baidu",
      "label": "百度统计 Token",
      "group": "统计",
      "type": "input",
      "value": "",
      "placeholder": "百度统计的 hm.js token"
    },
    {
      "name": "tencent",
      "label": "腾讯分析 ID",
      "group": "统计",
      "type": "input",
      "value": "",
      "placeholder": "腾讯分析的项目 ID"
    },
    {
      "name": "view",
      "label": "不蒜子访客",
      "group": "统计",
      "type": "switch",
      "value": false,
      "note": "启用不蒜子访客统计"
    }
  ]
}
```

### 3.3 编写 base.peb（基础骨架）

```pebble
{# 我的基础布局 #}
<!DOCTYPE html>
<html lang="zh-CN"{{ htmlDataAttrs | raw }}>
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>{{ title }}</title>
    <meta name="description" content="{{ site.siteDescription | striptags }}">
    {% if site.siteAuthor %}<meta name="author" content="{{ site.siteAuthor }}">{% endif %}
    {% if site.siteFavicon %}<link rel="icon" href="{{ site.siteFavicon | https_upgrade }}">{% endif %}
    <base href="{{ baseUrl }}">
    {% if themeVarsStyle %}{{ themeVarsStyle | raw }}{% endif %}
    {% for asset in cssAssets %}
    <link rel="stylesheet" href="{{ asset.src }}">
    {% endfor %}
    <link rel="stylesheet" href="styles/main.css">
    {% block head %}{% endblock %}
</head>
<body>
    <header class="site-header">
        <div class="container">
            <div class="site-brand">
                {% if site.siteAvatar %}<img class="site-avatar" src="{{ site.siteAvatar | https_upgrade }}" alt="{{ site.siteName }}">{% endif %}
                <div class="site-info">
                    <a href="./" class="site-logo">{{ site.siteName }}</a>
                    {% if site.siteDescription %}<p class="site-description">{{ site.siteDescription }}</p>{% endif %}
                </div>
            </div>
            {% if site.menus is not empty %}
            <nav class="site-nav">
                <button class="nav-toggle" aria-label="菜单" aria-expanded="false">
                    <span class="nav-toggle-bar"></span>
                    <span class="nav-toggle-bar"></span>
                    <span class="nav-toggle-bar"></span>
                </button>
                <div class="nav-links">
                    {% for menu in site.menus %}
                    <a href="{{ menu.link }}" class="nav-link"{% if menu.openType == "External" %} target="_blank"{% endif %}>{{ menu.name }}</a>
                    {% endfor %}
                </div>
            </nav>
            {% endif %}
        </div>
    </header>
    <main class="container">
        {% block content %}{% endblock %}
    </main>
    <footer class="site-footer">
        <div class="container">
            <p>{{ site.footerInfo }}</p>
        </div>
    </footer>
    {{ extraScripts | raw }}
    {% block scripts %}{% endblock %}
    <div id="reading-progress-container" class="reading-progress-container">
        <div id="reading-progress" class="reading-progress-bar"></div>
    </div>
    <button id="back-to-top" class="back-to-top" aria-label="回到顶部">↑</button>
    <div id="image-zoom-overlay" class="image-zoom-overlay">
        <img id="image-zoom-img" class="image-zoom-img" />
    </div>
    {% for asset in jsAssets %}
    <script src="{{ asset.src }}"{% if asset.defer_ %} defer{% endif %}{% if asset.async_ %} async{% endif %}></script>
    {% endfor %}
    {% if site.menus is not empty %}
    <script>
    (function(){
        var toggle = document.querySelector('.nav-toggle');
        var links = document.querySelector('.nav-links');
        if (!toggle || !links) return;
        toggle.addEventListener('click', function(){
            var expanded = toggle.getAttribute('aria-expanded') === 'true';
            toggle.setAttribute('aria-expanded', !expanded);
            links.classList.toggle('nav-links-open');
            links.classList.toggle('is-open');
            links.classList.toggle('open');
            toggle.classList.toggle('is-open');
            toggle.classList.toggle('active');
        });
    })();
    </script>
    {% endif %}
    <script src="scripts/custom.js"></script>
</body>
</html>
```

### 3.4 编写 index.peb（首页）

```pebble
{# 首页 #}
{% extends "base" %}

{% block content %}
{% if posts is not empty %}
<div class="post-list">
    {% for post in posts %}
    <article class="post-card">
        {% if post.feature %}
        <a href="{{ post.link }}" class="post-card-cover">
            <img src="{{ post.feature }}" alt="{{ post.title }}" loading="lazy">
        </a>
        {% endif %}
        <div class="post-card-body">
            <span class="post-date">{{ post.date }}</span>
            <h2 class="post-card-title"><a href="{{ post.link }}">{{ post.title }}</a></h2>
            <div class="post-card-abstract">{{ post.abstract | raw }}</div>
        </div>
    </article>
    {% endfor %}
</div>
{% else %}
<div class="empty">暂无文章</div>
{% endif %}

{% if pagination.total > 1 %}
<nav class="pagination">
    {% if pagination.prev %}
    <a href="{{ pagination.prev }}" class="page-prev">← 上一页</a>
    {% endif %}
    <span class="page-info">{{ pagination.current }} / {{ pagination.total }}</span>
    {% if pagination.next %}
    <a href="{{ pagination.next }}" class="page-next">下一页 →</a>
    {% endif %}
</nav>
{% endif %}
{% endblock %}
```

### 3.5 后续步骤

按本文档后续章节编写 `post.peb`、`archives.peb`、`tags.peb`、`tag.peb`、`friends.peb`、`404.peb`、`custom.css`、`custom.js`，最后按 [第 13 节](#13-打包与发布) 打包为 `.zip` 导入 APP。

---

## 4. theme.json 规范

### 4.1 顶层字段

| 字段 | 类型 | 必需 | 说明 |
|------|------|------|------|
| `id` | string | ✅ | 主题唯一标识，英文小写+连字符（如 `my-theme`） |
| `name` | string | ✅ | 主题显示名称（中文） |
| `version` | string | ✅ | 语义化版本号（如 `1.0.0`） |
| `author` | string | ✅ | 作者名 |
| `description` | string | ✅ | 一句话描述 |
| `previewImage` | string\|null | ✅ | 预览图，填 `null`（APP 自动处理） |
| `tags` | string[] | ✅ | 主题标签数组（如 `["简约", "博客"]`） |
| `isBuiltin` | boolean | ✅ | 是否内置，用户主题填 `false` |
| `customConfig` | array | ✅ | 配置项数组，见下文 |
| `assets` | array | ❌ | 附加资源声明（字体/JS库/CSS），见 4.4 |
| `jsAssets` | string[] | ❌ | CDN JS 库 URL 数组（旧格式，推荐用 `assets`） |

### 4.2 customConfig 配置项结构

每个配置项对象包含以下字段：

| 字段 | 类型 | 必需 | 说明 |
|------|------|------|------|
| `name` | string | ✅ | 配置键名，英文蛇形（如 `accent_color`），用于 `{{themePackConfig.键名}}` |
| `label` | string | ✅ | 显示标签（中文） |
| `group` | string | ✅ | 分组名（中文，如 `颜色`、`排版`、`统计`、`社交`） |
| `type` | string | ✅ | 类型，见 4.3 |
| `value` | any | ✅ | 默认值 |
| `note` | string | ❌ | 提示说明 |
| `options` | array | ❌ | select/radio 类型的选项列表 `[{label, value}]` |
| `min` | number | ❌ | slider/number 最小值 |
| `max` | number | ❌ | slider/number 最大值 |
| `step` | number | ❌ | slider 步长 |
| `placeholder` | string | ❌ | input/textarea 占位提示 |
| `language` | string | ❌ | code 类型的语法高亮语言 |
| `fallback` | string | ❌ | 当 `type` 为 APP 未识别的自定义类型时，降级使用的原子类型（如 `"color"`/`"slider"`）。见 4.4 |
| `items` | array | ❌ | compound 复合类型的子配置项列表，每项本身是一个 customConfig 对象。见 4.4 |
| `layout` | string | ❌ | compound 子项布局方向：`"row"`（水平）/ `"column"`（垂直，默认） |

### 4.3 支持的 type 类型

| type | 说明 | value 类型 | 示例 |
|------|------|-----------|------|
| `color` | 颜色选择器 | string（十六进制） | `"#c0392b"` |
| `switch` | 开关 | boolean | `true` / `false` |
| `select` | 下拉选择 | string | `"2"`（配合 `options`） |
| `slider` | 滑块 | number | `760`（配合 `min`/`max`/`step`） |
| `input` | 单行文本 | string | `""` |
| `textarea` | 多行文本 | string | - |
| `radio` | 单选按钮 | string | - |
| `number` | 数字输入 | number | - |
| `code` | 代码编辑器 | string | - |
| `multiselect` | 多选标签 | string（逗号分隔） | `"shadow,glass"`（配合 `options`） |
| `image` | 图片 URL | string | `"https://..."` |
| `compound` | 复合控件 | string（JSON 对象） | `"{\"start\":\"#fff\",\"end\":\"#000\"}"` |

### 4.4 动态适配：compound 复合控件与 fallback 智能降级

为了支持外部主题开发者自定义新控件，而无需 APP 发版更新，本系统提供两种动态适配机制：

#### 4.4.1 fallback 智能降级

当主题声明了一个 APP 未内置的自定义 `type`（如 `"gradient"`、`"icon-picker"`）时，可通过 `fallback` 字段指定降级渲染的原子类型。APP 遇到未知类型时：

1. 优先使用 `fallback` 指定的原子类型渲染（若该类型受支持）
2. 未指定 `fallback` 或 `fallback` 也不受支持时，统一降级为 `textarea`

**配置值始终可读写**，保证未知控件的配置不会丢失。

```json
{
  "name": "hero_gradient",
  "label": "首页渐变色",
  "group": "颜色",
  "type": "gradient",
  "fallback": "textarea",
  "value": "linear-gradient(135deg, #667eea, #764ba2)",
  "note": "填写 CSS 渐变表达式"
}
```

> 常用 fallback：`color`（颜色）、`input`（单行文本）、`textarea`（多行文本）、`slider`（滑块）、`switch`（开关）、`select`（下拉）、`number`（数字）。

#### 4.4.2 compound 复合控件

通过 `compound` 类型可将多个原子控件组合成一个复合控件，适用于需要多个关联字段的场景（如渐变色起始/结束色、阴影颜色+模糊半径等）。

- 子项通过 `items` 数组声明，每项是一个完整的 customConfig 对象（支持递归嵌套）
- `layout` 指定子项布局：`"row"`（水平并排）/ `"column"`（垂直堆叠，默认）
- 复合控件的值以 **JSON 对象字符串** 存储于父 `name` 下，如 `{"start":"#ffffff","end":"#000000"}`
- 模板中通过 `{{themePackConfig.父键名}}` 获取 JSON 字符串，可在 `custom.js` 中 `JSON.parse()` 解析后使用

```json
{
  "name": "gradient_pair",
  "label": "渐变配色",
  "group": "颜色",
  "type": "compound",
  "layout": "row",
  "value": "{\"start\":\"#667eea\",\"end\":\"#764ba2\"}",
  "items": [
    {
      "name": "start",
      "label": "起始色",
      "type": "color",
      "value": "#667eea"
    },
    {
      "name": "end",
      "label": "结束色",
      "type": "color",
      "value": "#764ba2"
    }
  ]
}
```

**在 custom.js 中解析复合控件值：**

```javascript
const gradient = JSON.parse('{{gradient_pair}}');
document.documentElement.style.setProperty('--grad-start', gradient.start);
document.documentElement.style.setProperty('--grad-end', gradient.end);
```

> **提示**：compound 可与 fallback 结合——即使旧版 APP 不支持 compound，也可通过 fallback 降级为 textarea 让用户手动填写 JSON。

### 4.5 assets 资源声明（可选）

若主题需要自定义字体、第三方 JS 库等，在 `theme.json` 顶层添加 `assets` 数组。支持两种格式：

**格式一：对象数组（推荐，可指定类型）**

```json
{
  "assets": [
    {
      "type": "font",
      "src": "fonts/Mona.woff2"
    },
    {
      "type": "js",
      "src": "scripts/gallery.js",
      "defer": false,
      "async": false
    },
    {
      "type": "css",
      "src": "styles/highlight.css"
    }
  ]
}
```

**格式二：字符串数组（简化，默认 type=file）**

```json
{
  "assets": ["fonts/Mona.woff2", "images/pattern.png"]
}
```

| type | 行为 |
|------|------|
| `css` | 复制文件 + 在 `<head>` 注入 `<link>`（位于 `styles/main.css` 之前） |
| `js` | 复制文件 + 在 `<body>` 末尾注入 `<script>`（位于 `scripts/custom.js` 之前） |
| `font` | 仅复制文件，供 CSS `@font-face` 引用 |
| `image` | 仅复制文件，供 CSS `url()` 引用 |
| `file` | 仅复制文件 |

> `src` 是相对于主题包根目录的路径，复制时保留目录结构到输出目录。

### 4.5 完整 theme.json 示例

```json
{
  "id": "my-theme",
  "name": "我的主题",
  "version": "1.0.0",
  "author": "作者名",
  "description": "主题描述",
  "previewImage": null,
  "tags": ["标签1", "标签2"],
  "isBuiltin": false,
  "assets": [
    { "type": "font", "src": "fonts/icon.woff2" }
  ],
  "customConfig": [
    {
      "name": "accent_color",
      "label": "强调色",
      "group": "颜色",
      "type": "color",
      "value": "#c0392b",
      "note": "用于链接、标签、按钮等交互元素"
    },
    {
      "name": "show_hero",
      "label": "首页大标题",
      "group": "排版",
      "type": "switch",
      "value": false,
      "note": "在首页顶部显示站点名称和描述"
    },
    {
      "name": "columns",
      "label": "首页列数",
      "group": "排版",
      "type": "select",
      "value": "2",
      "options": [
        { "label": "1 列", "value": "1" },
        { "label": "2 列", "value": "2" },
        { "label": "3 列", "value": "3" }
      ],
      "note": "首页文章网格列数"
    },
    {
      "name": "content_width",
      "label": "内容宽度",
      "group": "排版",
      "type": "slider",
      "value": 760,
      "min": 600,
      "max": 1000,
      "step": 20,
      "note": "文章内容的最大宽度（像素）"
    },
    {
      "name": "ga",
      "label": "Google Analytics",
      "group": "统计",
      "type": "input",
      "value": "",
      "placeholder": "G-XXXXXXXXXX"
    },
    {
      "name": "baidu",
      "label": "百度统计 Token",
      "group": "统计",
      "type": "input",
      "value": "",
      "placeholder": "百度统计的 hm.js token"
    },
    {
      "name": "tencent",
      "label": "腾讯分析 ID",
      "group": "统计",
      "type": "input",
      "value": "",
      "placeholder": "腾讯分析的项目 ID"
    },
    {
      "name": "view",
      "label": "不蒜子访客",
      "group": "统计",
      "type": "switch",
      "value": false,
      "note": "启用不蒜子访客统计"
    }
  ]
}
```

### 4.7 配置值的类型转换规则

APP 持久化配置时所有值都转为字符串。读取时根据 `effectiveType`（即 `type` 本身，未知类型则取 `fallback`，最终降级 `textarea`）还原真实类型：

| effectiveType | 还原后类型 |
|------|-----------|
| `switch` | Boolean（`"true"` → `true`） |
| `number` / `slider` | Double（按小数解析） |
| `compound` | String（JSON 对象字符串，透传） |
| 其他 | String（透传） |

> **注意**：传入模板的 `themePackConfig` 是 `Map<String, String>`（全部转成字符串）。因此模板中判断 switch 配置应写 `themePackConfig.xxx == "true"`，而不是 `themePackConfig.xxx`。而 `showHero` 这个特殊布尔变量已被预处理为真正的 Boolean，可直接用 `{% if showHero %}`。
>
> **动态适配**：当自定义 `type` 通过 `fallback` 降级为 `switch`/`number`/`slider` 时，配置值也会按降级后的类型正确还原，保证模板中的类型判断一致。

---

## 5. Pebble 模板开发

### 5.1 模板继承机制

Gridea 使用 Pebble 的模板继承功能。`base.peb` 定义基础骨架并通过 `{% block %}` 声明可覆盖区域，其他模板通过 `{% extends "base" %}` 继承并覆盖 block。

**base.peb 中声明 block：**

```pebble
<head>
    {% block head %}{% endblock %}
</head>
<body>
    {% block content %}{% endblock %}
    {% block scripts %}{% endblock %}
</body>
```

**index.peb 中继承并覆盖：**

```pebble
{% extends "base" %}

{% block content %}
<h1>首页内容</h1>
{% endblock %}

{% block scripts %}
<script>
// 仅首页需要的脚本
</script>
{% endblock %}
```

**关键规则：**
- 模板名不带 `.peb` 后缀（引擎已配置 `suffix = ".peb"`）
- `extends "base"` 引用的是 `base.peb`
- 未被覆盖的 block 保持 base 中的内容
- 一个页面模板必须 `extends "base"`，否则缺少 HTML 骨架

### 5.2 常用 Pebble 语法

**变量输出：**

```pebble
{{ site.siteName }}
{{ post.title }}
```

**自动转义与 raw 过滤器：**

引擎开启了 `autoEscaping(true)`，所有变量输出会自动 HTML 转义。输出可信 HTML（如文章正文）必须加 `| raw`：

```pebble
{# 正确：文章 HTML 内容用 raw #}
<div class="post-content">{{ post.content | raw }}</div>

{# 错误：会被转义，显示 HTML 标签原文 #}
<div class="post-content">{{ post.content }}</div>

{# htmlDataAttrs 是预组装的属性字符串，必须 raw #}
<html lang="zh-CN"{{ htmlDataAttrs | raw }}>
```

**条件判断：**

```pebble
{% if site.siteAuthor %}有作者{% endif %}
{% if posts is not empty %}有文章{% else %}无文章{% endif %}
{% if pagination.total > 1 %}多页{% endif %}
{% if themePackConfig.show_hero == "true" %}显示大标题{% endif %}
{% if post.prevPost is not null %}有上一篇{% endif %}
```

**循环：**

```pebble
{% for post in posts %}
<article>{{ post.title }}</article>
{% endfor %}

{% for tag in post.tags %}
<a href="{{ tag.link }}">{{ tag.name }}</a>
{% endfor %}

{% for year in archivesByYear %}
<h2>{{ year.year }}</h2>
{% for post in year.posts %}
<li>{{ post.title }}</li>
{% endfor %}
{% endfor %}
```

**变量赋值与切片：**

```pebble
{% set firstPost = posts[0] %}
{% set restPosts = posts | slice(1, posts | length) %}
```

**默认值：**

```pebble
{{ themePackConfig.columns | default("2") }}
```

**注释：**

```pebble
{# 这是注释，不会输出到 HTML #}
```

**verbatim 块（避免 Pebble 解析 JS 模板语法）：**

当内联 `<script>` 中含有 `{{ }}` 等 Pebble 会误解析的语法时，用 `{% verbatim %}` 包裹：

```pebble
{% block scripts %}
{% verbatim %}
<script>
(function() {
    var obj = { name: "test", value: 123 };  // 这里的 {} 不会被 Pebble 误解析
})();
</script>
{% endverbatim %}
{% endblock %}
```

### 5.3 自定义过滤器

引擎注册了两个自定义过滤器：

| 过滤器 | 作用 | 示例 |
|--------|------|------|
| `https_upgrade` | 将 `http://` 升级为 `https://` | `{{ site.siteAvatar \| https_upgrade }}` |
| `striptags` | 去除 HTML 标签（Pebble 4.x 移除了内置的） | `{{ site.siteDescription \| striptags }}` |

**内置过滤器**（Pebble 4.x 自带）也可用：`default`、`slice`、`length`、`upper`、`lower`、`trim`、`raw` 等。

### 5.4 可用上下文变量参考

以下变量在所有页面模板中可用（由 `buildBaseContext()` 注入）：

#### 通用变量

| 变量 | 类型 | 说明 |
|------|------|------|
| `site` | object | 站点信息对象 |
| `baseUrl` | string | 页面基准 URL，用于 `<base href>` |
| `title` | string | 页面标题 |
| `htmlDataAttrs` | string | `<html>` 标签的 `data-*` 属性字符串 |
| `themeVarsStyle` | string | 主题变量样式（当前为空字符串） |
| `cssAssets` | list | type=css 的资源列表，每项含 `src` |
| `jsAssets` | list | type=js 的资源列表，每项含 `src`、`defer_`、`async_` |
| `extraScripts` | string | 额外脚本（当前为空字符串） |
| `themePackConfig` | map | 主题配置值 Map，键为 customConfig.name，值为字符串 |
| `showHero` | boolean | 是否显示首页大标题（预处理后的布尔值） |

#### site 对象字段

| 字段 | 类型 | 说明 |
|------|------|------|
| `site.siteName` | string | 站点名称 |
| `site.siteDescription` | string | 站点描述 |
| `site.siteAuthor` | string | 作者名 |
| `site.siteFavicon` | string | 网站图标 URL |
| `site.siteAvatar` | string | 作者头像 URL |
| `site.footerInfo` | string | 页脚信息 |
| `site.domain` | string | 站点域名 |
| `site.menus` | list | 菜单数组，每项含 `name`、`link`、`openType` |

#### 页面特有变量

**首页 (index.peb)：**

| 变量 | 类型 | 说明 |
|------|------|------|
| `posts` | list | 当前页文章列表，见下文 PostRenderData |
| `pagination` | object | 分页对象：`prev`、`next`、`current`、`total` |

**文章详情 (post.peb)：**

| 变量 | 类型 | 说明 |
|------|------|------|
| `post` | object | 文章对象，见下文 |
| `commentHtml` | string | 评论区 HTML，用 `\| raw` 输出 |
| `ogTags` | string | Open Graph 标签（当前为空） |
| `jsonLd` | string | JSON-LD 结构化数据（当前为空） |

**归档页 (archives.peb)：**

| 变量 | 类型 | 说明 |
|------|------|------|
| `archivesByYear` | list | 按年份分组的文章列表，每项含 `year`(string)、`posts`(list) |

**标签总览页 (tags.peb)：**

| 变量 | 类型 | 说明 |
|------|------|------|
| `tags` | list | 所有标签，每项含 `name`、`slug`、`link`、`count` |

**标签详情页 (tag.peb)：**

| 变量 | 类型 | 说明 |
|------|------|------|
| `tag` | object | 当前标签对象：`name`、`slug`、`link`、`count` |
| `posts` | list | 该标签下的文章列表 |
| `pagination` | object | 分页对象（当前总是 current=1, total=1） |

**友链页 (friends.peb)：**

| 变量 | 类型 | 说明 |
|------|------|------|
| `friendLinks` | list | 友链数组，每项含 `name`、`url`、`avatar`、`description` |

#### PostRenderData 文章对象字段

| 字段 | 类型 | 说明 |
|------|------|------|
| `fileName` | string | 文件名（用作目录名） |
| `title` | string | 标题 |
| `content` | string | HTML 正文（用 `\| raw` 输出） |
| `abstract` | string | HTML 摘要（用 `\| raw` 输出） |
| `description` | string | 纯文本描述（SEO 用） |
| `date` | string | 日期（如 `2024-01-15`） |
| `tags` | list | 标签数组，每项含 `name`、`slug`、`link` |
| `feature` | string | 封面图 URL |
| `link` | string | 文章链接（相对路径，如 `post/xxx/`） |
| `hideInList` | boolean | 是否在列表中隐藏 |
| `isTop` | boolean | 是否置顶 |
| `stats` | object | 统计：`words`(字数)、`minutes`(分钟)、`time`(毫秒) |
| `toc` | string | 目录 HTML（用 `\| raw` 输出） |
| `prevPost` | object\|null | 上一篇文章（null 时无） |
| `nextPost` | object\|null | 下一篇文章（null 时无） |

### 5.5 Pebble 4.x 限制与注意事项

> ⚠️ **这是最常见的陷阱来源，务必仔细阅读！**

**限制 1：`{% if %}` 条件表达式仅接受 Boolean/String/Number**

Pebble 4.x 的 `{% if %}` **不接受 ArrayList 或对象**作为条件。直接判断列表或对象会报错或行为异常。

```pebble
{# ❌ 错误：posts 是 ArrayList，不能直接 if 判断 #}
{% if posts %}有文章{% endif %}

{# ✅ 正确：用 is not empty 检查列表 #}
{% if posts is not empty %}有文章{% else %}无文章{% endif %}

{# ❌ 错误：post.prevPost 是对象，不能直接 if 判断 #}
{% if post.prevPost %}有上一篇{% endif %}

{# ✅ 正确：用 is not null 检查对象 #}
{% if post.prevPost is not null %}有上一篇{% endif %}

{# ✅ 正确：site.menus 是列表，用 is not empty #}
{% if site.menus is not empty %}显示菜单{% endif %}

{# ✅ 正确：字符串和数字可直接判断 #}
{% if site.siteAuthor %}有作者{% endif %}
{% if pagination.total > 1 %}多页{% endif %}
```

**限制 2：switch 配置在模板中是字符串**

`themePackConfig` 的所有值在传入模板时都被转为字符串。判断 switch 类型配置：

```pebble
{# ❌ 错误：themePackConfig.show_hero 是字符串 "false"，不是 Boolean #}
{% if themePackConfig.show_hero %}显示{% endif %}

{# ✅ 正确：与字符串 "true" 比较 #}
{% if themePackConfig.show_hero == "true" %}显示{% endif %}

{# ✅ 正确：showHero 变量已被预处理为真正的 Boolean #}
{% if showHero %}显示{% endif %}
```

**限制 3：自动转义**

引擎开启 `autoEscaping(true)`，所有 `{{ }}` 输出自动转义 HTML。输出可信 HTML 必须 `| raw`。

**限制 4：换行处理**

引擎设置 `newLineTrimming(false)`，保留模板换行，避免 HTML 结构错乱。模板中的换行会原样输出。

**限制 5：模板名不带后缀**

`{% extends "base" %}` 而非 `{% extends "base.peb" %}`。引擎已配置 `suffix = ".peb"`。

---

## 6. 页面模板详解

### 6.1 base.peb — 基础骨架

职责：定义所有页面共享的 HTML 结构，包括 `<html>`/`<head>`/`<body>`、页头导航、页脚、通用组件（阅读进度条、回到顶部、图片放大）。

**必须包含的元素：**

1. `<base href="{{ baseUrl }}">` — 路径解析基准
2. `{{ htmlDataAttrs | raw }}` — `<html>` 的 data 属性
3. `{{ title }}` — `<title>` 标签
4. `{{ site.siteDescription | striptags }}` — SEO description
5. `styles/main.css` 引用 — 主题样式
6. `cssAssets` 循环 — 主题声明的 CSS 资源
7. `jsAssets` 循环 — 主题声明的 JS 资源
8. `scripts/custom.js` 引用 — 主题脚本
9. `{% block content %}{% endblock %}` — 内容区
10. `{% block head %}{% endblock %}` — head 扩展点
11. `{% block scripts %}{% endblock %}` — 脚本扩展点
12. 阅读进度条、回到顶部、图片放大三个通用组件
13. 移动端菜单切换脚本

**完整模板见 [3.3 节](#33-编写-basepeb基础骨架)。**

**htmlDataAttrs 说明：**

`htmlDataAttrs` 是根据 `themePackConfig` 自动生成的 `<html>` 标签 `data-*` 属性字符串。主题在 CSS 中可通过属性选择器响应配置。已支持的配置键到 data 属性的映射：

| 配置键 | 生成的 data 属性 |
|--------|-----------------|
| `card_style` | `data-card` |
| `content_width` | `data-width` |
| `dark_mode` | `data-theme`（true→dark，false→light） |
| `columns` | `data-columns` |
| `drop_cap` | `data-drop-cap` |
| `card_blur` | `data-blur` |
| `cursor_blink` | `data-cursor` |
| `column_count` | `data-cols` |
| `sidebar_width` | `data-sidebar-width` |
| `show_social` | `data-social` |
| `scanline` | `data-scanline` |
| `bg_tone` | `data-tone` |
| `vertical_title` | `data-vertical` |

主题也可在 `<html>` 标签上自行追加额外的 data 属性（直接写在模板里），与 `htmlDataAttrs` 共存：

```pebble
<html lang="zh-CN"{{ htmlDataAttrs | raw }} data-columns="{{ themePackConfig.columns | default("2") }}">
```

### 6.2 index.peb — 首页与分页

职责：展示文章列表（封面、标题、摘要、日期、标签），处理分页导航。

**关键点：**
- 用 `{% extends "base" %}` 继承
- 用 `{% if posts is not empty %}` 判断文章列表（注意是 `is not empty` 不是直接 `if`）
- 分页用 `pagination.prev`/`pagination.next`，它们是相对根目录的路径，**不要加 `../../` 前缀**
- 文章摘要 `post.abstract` 必须 `| raw`
- 第一篇文章可作为特色文章单独展示

**完整模板见 [3.4 节](#34-编写-indexpeb首页)。**

### 6.3 post.peb — 文章详情

职责：展示单篇文章的完整内容，包括标题、元信息（日期/字数/阅读时长）、标签、封面图、正文、目录、上下篇导航、评论区。

**关键点：**
- `post.content` 必须 `| raw`（HTML 正文）
- `post.toc` 必须 `| raw`（目录 HTML，可能为空字符串）
- `commentHtml` 必须 `| raw`（评论系统 HTML）
- `post.prevPost`/`post.nextPost` 用 `is not null` 判断
- 文章页可内联脚本实现代码复制、阅读进度等，用 `data-gridea-inline="1"` 标记避免与 custom.js 重复

**示例模板：**

```pebble
{# 文章详情 #}
{% extends "base" %}

{% block head %}
{% if ogTags %}{{ ogTags | raw }}{% endif %}
{% if jsonLd %}{{ jsonLd | raw }}{% endif %}
{% endblock %}

{% block content %}
<article class="post-detail">
    <header class="post-header">
        <div class="post-meta-top">
            <span class="post-date">{{ post.date }}</span>
            <span>{{ post.stats.words }} 字</span>
            <span>{{ post.stats.minutes }} 分钟阅读</span>
        </div>
        <h1 class="post-title">{{ post.title }}</h1>
        <div class="post-tags">
            {% for tag in post.tags %}
            <a href="{{ tag.link }}" class="tag-chip">{{ tag.name }}</a>
            {% endfor %}
        </div>
    </header>
    {% if post.feature %}
    <div class="post-feature">
        <img src="{{ post.feature }}" alt="{{ post.title }}" class="post-feature-img">
    </div>
    {% endif %}
    {% if post.toc %}{{ post.toc | raw }}{% endif %}
    <div class="post-content">
        {{ post.content | raw }}
    </div>
    <nav class="post-navigation">
        {% if post.prevPost is not null %}
        <div class="post-nav prev">
            <span class="nav-label">← 上一篇</span>
            <a href="{{ post.prevPost.link }}" class="nav-title">{{ post.prevPost.title }}</a>
        </div>
        {% endif %}
        {% if post.nextPost is not null %}
        <div class="post-nav next">
            <span class="nav-label">下一篇 →</span>
            <a href="{{ post.nextPost.link }}" class="nav-title">{{ post.nextPost.title }}</a>
        </div>
        {% endif %}
    </nav>
    {% if commentHtml %}{{ commentHtml | raw }}{% endif %}
</article>
{% endblock %}

{% block scripts %}
{% verbatim %}
<script>
(function() {
    'use strict';
    // 代码块复制按钮
    document.querySelectorAll('.post-content pre').forEach(function(pre) {
        if (pre.querySelector('.code-copy-btn')) return;
        var btn = document.createElement('button');
        btn.className = 'code-copy-btn';
        btn.type = 'button';
        btn.textContent = '复制';
        pre.appendChild(btn);
        btn.addEventListener('click', function() {
            var code = pre.querySelector('code');
            var text = code ? code.innerText : pre.innerText;
            if (navigator.clipboard) {
                navigator.clipboard.writeText(text).then(function() {
                    btn.textContent = '已复制'; btn.classList.add('copied');
                    setTimeout(function() { btn.textContent = '复制'; btn.classList.remove('copied'); }, 2000);
                });
            }
        });
    });
    // 标记：文章页内联处理阅读进度/回到顶部/图片放大，避免与 custom.js 重复
    var backTopBtn = document.getElementById('back-to-top');
    var progressBar = document.getElementById('reading-progress');
    var overlay = document.getElementById('image-zoom-overlay');
    if (backTopBtn) backTopBtn.dataset.grideaInline = '1';
    if (progressBar) progressBar.dataset.grideaInline = '1';
    if (overlay) overlay.dataset.grideaInline = '1';
    // 阅读进度 + 回到顶部逻辑...
    // 图片放大逻辑...
})();
</script>
{% endverbatim %}
{% endblock %}
```

### 6.4 archives.peb — 归档页

职责：按年份分组展示所有文章列表。

```pebble
{# 归档页 #}
{% extends "base" %}

{% block content %}
<div class="archives-page">
    <h1 class="page-title">归档</h1>
    {% if archivesByYear is not empty %}
    {% for year in archivesByYear %}
    <div class="archive-year">
        <h2 class="archive-year-title">{{ year.year }}</h2>
        <ul class="archive-list">
            {% for post in year.posts %}
            <li class="archive-item">
                <span class="archive-date">{{ post.date }}</span>
                <a href="{{ post.link }}" class="archive-title">{{ post.title }}</a>
            </li>
            {% endfor %}
        </ul>
    </div>
    {% endfor %}
    {% else %}
    <div class="empty">暂无文章</div>
    {% endif %}
</div>
{% endblock %}
```

### 6.5 tags.peb — 标签总览页

```pebble
{# 标签总览页 #}
{% extends "base" %}

{% block content %}
<div class="tags-page">
    <h1 class="page-title">标签</h1>
    <div class="tags-cloud">
        {% if tags is not empty %}
        {% for tag in tags %}
        <a href="{{ tag.link }}" class="tag-card">
            <span class="tag-name">{{ tag.name }}</span>
            <span class="tag-count">{{ tag.count }}</span>
        </a>
        {% endfor %}
        {% else %}
        <div class="empty">暂无标签</div>
        {% endif %}
    </div>
</div>
{% endblock %}
```

### 6.6 tag.peb — 标签详情页

职责：展示某标签下的文章列表，结构与首页类似。

```pebble
{# 标签详情页 #}
{% extends "base" %}

{% block content %}
<div class="tag-detail">
    <h1 class="page-title">标签: {{ tag.name }}</h1>
    {% if posts is not empty %}
    <div class="post-list">
        {% for post in posts %}
        <article class="post-card">
            {% if post.feature %}
            <a href="{{ post.link }}" class="post-card-cover">
                <img src="{{ post.feature }}" alt="{{ post.title }}" loading="lazy">
            </a>
            {% endif %}
            <div class="post-card-body">
                <span class="post-date">{{ post.date }}</span>
                <h2 class="post-card-title"><a href="{{ post.link }}">{{ post.title }}</a></h2>
                <div class="post-card-abstract">{{ post.abstract | raw }}</div>
            </div>
        </article>
        {% endfor %}
    </div>
    {% else %}
    <div class="empty">该标签下暂无文章</div>
    {% endif %}
</div>
{% endblock %}
```

### 6.7 friends.peb — 友链页

```pebble
{# 友链页 #}
{% extends "base" %}

{% block content %}
<div class="friend-links-page">
    <h1 class="page-title">友情链接</h1>
    <div class="friend-links-list">
        {% if friendLinks is not empty %}
        {% for link in friendLinks %}
        <a href="{{ link.url | https_upgrade }}" class="friend-link-card" target="_blank" rel="noopener noreferrer">
            {% if link.avatar %}
            <img src="{{ link.avatar | https_upgrade }}" alt="{{ link.name }}" class="friend-link-avatar" loading="lazy" onerror="this.style.display='none'">
            {% endif %}
            <div class="friend-link-info">
                <span class="friend-link-name">{{ link.name }}</span>
                {% if link.description %}
                <p class="friend-link-desc">{{ link.description }}</p>
                {% endif %}
            </div>
        </a>
        {% endfor %}
        {% else %}
        <div class="empty">暂无友链</div>
        {% endif %}
    </div>
</div>
{% endblock %}
```

### 6.8 404.peb — 错误页

```pebble
{# 404 页 #}
{% extends "base" %}

{% block content %}
<div class="error-page">
    <h1 class="error-code">404</h1>
    <p class="error-message">页面不存在</p>
    <p class="error-desc">抱歉，您访问的页面不存在或已被移除。</p>
    <a href="./" class="error-back-home">返回首页</a>
</div>
{% endblock %}
```

---

## 7. CSS 开发规范

### 7.1 CSS 变量约定

在 `:root` 中定义主题色系和尺寸变量，便于统一管理和用户配置替换：

```css
:root {
    /* 主色调 */
    --primary: #c0392b;
    --primary-light: rgba(192, 57, 43, 0.08);
    --primary-hover: #a02a1c;

    /* 文字色 */
    --ink: #2c3e50;          /* 标题/强调 */
    --text: #2c2c2c;         /* 正文 */
    --text-secondary: #5a5a5a;
    --text-tertiary: #8a8a8a;

    /* 背景色 */
    --bg: #fafaf7;           /* 页面背景 */
    --bg-subtle: #f3f2ec;    /* 次级背景（代码块、引用） */
    --bg-card: #ffffff;      /* 卡片背景 */

    /* 边框 */
    --border: rgba(44, 62, 80, 0.08);
    --border-strong: rgba(44, 62, 80, 0.18);

    /* 圆角 */
    --radius: 6px;
    --radius-sm: 3px;

    /* 阴影 */
    --shadow: 0 2px 8px rgba(44, 62, 80, 0.06);
    --shadow-hover: 0 8px 24px rgba(44, 62, 80, 0.12);

    /* 尺寸 */
    --max-width: 1200px;     /* 页面最大宽度 */
    --content-width: 760px;  /* 文章内容宽度 */

    /* 动画 */
    --transition: 0.3s ease;

    /* 字体 */
    --font-serif: Georgia, 'Times New Roman', 'Songti SC', 'SimSun', serif;
    --font-sans: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, 'Helvetica Neue', Arial, 'PingFang SC', 'Microsoft YaHei', sans-serif;
    --font-mono: 'SF Mono', 'Fira Code', 'Cascadia Code', Consolas, Monaco, monospace;
}
```

### 7.2 CSS 占位符替换

`custom.css` 中可使用 `{{变量名}}` 占位符，渲染时被替换为 `themePackConfig` 中对应的值：

```css
:root {
    --primary: {{accent_color}};        /* 替换为用户选择的颜色 */
    --content-width: {{content_width}}px; /* 替换为滑块值 */
}
```

**支持的占位符语法：**

1. **简单替换**：`{{key}}` → 配置值

   ```css
   .hero { color: {{accent_color}}; }
   ```

2. **布尔条件块**：`{{#key}}...{{/key}}`（值为 true 时保留内容）

   ```css
   {{#dark_mode}}
   body { background: #1a1a1a; color: #eee; }
   {{/dark_mode}}
   ```

3. **反向条件块**：`{{^key}}...{{/key}}`（值不为 true 时保留内容）

   ```css
   {{^dark_mode}}
   body { background: #fff; color: #333; }
   {{/dark_mode}}
   ```

4. **值匹配条件块**：`{{#key_value}}...{{/key_value}}`（配置值等于 value 时保留）

   ```css
   {{#bg_tone_pink}}
   body { background: #fff5f7; }
   {{/bg_tone_pink}}
   {{#bg_tone_lilac}}
   body { background: #f5f0ff; }
   {{/bg_tone_lilac}}
   ```

**特殊处理 — 字体：**

`fontFamily` 键的值会被自动映射为 CSS font-family 堆栈：

| 配置值 | 替换为 |
|--------|--------|
| `serif` | `Georgia, 'Times New Roman', 'Source Han Serif SC', 'Noto Serif CJK SC', serif` |
| `mono` | `'Courier New', Consolas, Monaco, 'Source Han Mono SC', monospace` |
| `sans` | `'Helvetica Neue', Helvetica, Arial, 'Source Han Sans SC', 'Noto Sans CJK SC', sans-serif` |
| `system` / `""` | `-apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, 'Helvetica Neue', Arial, sans-serif` |

### 7.3 多端适配（响应式断点）

必须适配三端，断点如下：

| 端 | 宽度范围 | 说明 |
|----|----------|------|
| 手机 | ≤768px | 单列、简化导航、隐藏侧边栏 |
| 平板 | 769-1024px | 双列或单列、调整字号 |
| 电脑 | >1024px | 完整布局、多列网格 |

**媒体查询模板：**

```css
/* 默认样式针对桌面端编写，再用媒体查询覆盖移动端 */

/* ===== 平板端 (769-1024px) ===== */
@media (min-width: 769px) and (max-width: 1024px) {
    .container { padding: 0 28px; }
    body { font-size: 15.5px; }
    .featured-post { grid-template-columns: 1fr; }
    html[data-columns="3"] .post-grid { grid-template-columns: repeat(2, 1fr); }
}

/* ===== 电脑端 (>1024px) ===== */
@media (min-width: 1025px) {
    .container { padding: 0 40px; }
}

/* ===== 手机端 (≤768px) ===== */
@media (max-width: 768px) {
    .container { padding: 0 20px; }
    body { font-size: 15px; }

    /* 移动端导航：汉堡菜单 */
    .nav-toggle { display: flex; }
    .nav-links {
        display: none;
        flex-direction: column;
        position: absolute;
        top: 100%;
        left: 0;
        right: 0;
        background: var(--bg-card);
        padding: 16px 20px;
        box-shadow: 0 4px 12px rgba(0,0,0,0.1);
        z-index: 100;
        gap: 14px;
    }
    /* 三种类名都要支持（兼容性） */
    .nav-links.nav-links-open,
    .nav-links.is-open,
    .nav-links.open { display: flex; }
    .site-nav { position: relative; }

    /* 单列布局 */
    .post-grid { grid-template-columns: 1fr; }
    .featured-post { grid-template-columns: 1fr; }

    /* 缩小字号 */
    .post-title { font-size: 28px; }
    .hero-title { font-size: 32px; }

    /* 隐藏侧边栏 */
    .magazine-sidebar { display: none; }
}
```

### 7.4 通用组件样式

以下组件在 `base.peb` 中被引用，必须在 CSS 中定义样式：

**容器：**

```css
.container { max-width: var(--max-width); margin: 0 auto; padding: 0 32px; }
```

**阅读进度条：**

```css
.reading-progress-container {
    position: fixed; top: 0; left: 0; right: 0;
    height: 3px; z-index: 999; pointer-events: none;
    background: rgba(0,0,0,0.06);
}
.reading-progress-bar {
    height: 100%; background: var(--primary);
    transform: scaleX(0); transform-origin: left;
    transition: transform 0.05s linear;
}
```

**回到顶部按钮：**

```css
.back-to-top {
    position: fixed; bottom: 32px; right: 32px;
    width: 44px; height: 44px; border-radius: 50%;
    background: var(--ink); color: #fff; border: none;
    font-size: 18px; cursor: pointer;
    opacity: 0; transform: translateY(10px);
    transition: opacity var(--transition), transform var(--transition);
    z-index: 998;
}
.back-to-top.visible { opacity: 1; transform: translateY(0); }
.back-to-top:hover { background: var(--primary); }
```

**图片放大遮罩：**

```css
.image-zoom-overlay {
    position: fixed; top: 0; left: 0; right: 0; bottom: 0;
    background: rgba(0,0,0,0.95);
    display: flex; align-items: center; justify-content: center;
    z-index: 9999; opacity: 0; pointer-events: none;
    transition: opacity 0.25s;
}
.image-zoom-overlay.visible { opacity: 1; pointer-events: auto; }
.image-zoom-img {
    max-width: 90vw; max-height: 90vh;
    border-radius: var(--radius);
    box-shadow: 0 8px 40px rgba(0,0,0,0.4);
}
```

**移动端汉堡菜单：**

```css
.nav-toggle {
    display: none; flex-direction: column; justify-content: space-around;
    width: 28px; height: 22px; background: transparent; border: none;
    cursor: pointer; padding: 0; flex-shrink: 0;
}
.nav-toggle-bar {
    width: 100%; height: 2px; background: var(--ink);
    border-radius: 2px; transition: all 0.25s;
}
.nav-toggle[aria-expanded="true"] .nav-toggle-bar:nth-child(1) { transform: translateY(8px) rotate(45deg); }
.nav-toggle[aria-expanded="true"] .nav-toggle-bar:nth-child(2) { opacity: 0; }
.nav-toggle[aria-expanded="true"] .nav-toggle-bar:nth-child(3) { transform: translateY(-8px) rotate(-45deg); }
```

### 7.5 页面切换动效

所有页面统一的入场/退场动画，写在 CSS 中：

```css
/* 页面入场动画 */
body {
    animation: grideaPageEnter 0.3s ease both;
}
@keyframes grideaPageEnter {
    from { opacity: 0; transform: translateY(8px); }
    to { opacity: 1; transform: translateY(0); }
}

/* 页面离开动画（点击链接时由 JS 添加 class 触发） */
body.gridea-page-leaving {
    animation: grideaPageLeave 0.2s ease forwards;
}
@keyframes grideaPageLeave {
    from { opacity: 1; }
    to { opacity: 0; }
}

/* 尊重用户的减少动画偏好 */
@media (prefers-reduced-motion: reduce) {
    body, body.gridea-page-leaving { animation: none !important; }
}
```

### 7.6 通过 data 属性响应配置

主题配置会生成 `<html>` 的 `data-*` 属性，CSS 可通过属性选择器响应：

```css
/* 默认 2 列 */
.post-grid { grid-template-columns: repeat(2, 1fr); }

/* 响应 columns 配置 */
html[data-columns="1"] .post-grid { grid-template-columns: 1fr; }
html[data-columns="3"] .post-grid { grid-template-columns: repeat(3, 1fr); }

/* 响应 drop_cap 配置 */
html[data-drop-cap="true"] .post-content > p:first-of-type::first-letter {
    font-size: 72px; float: left; line-height: 0.85;
    margin: 6px 10px 0 0; color: var(--primary);
}

/* 响应 dark_mode 配置 */
html[data-theme="dark"] body { background: #1a1a1a; color: #eee; }
html[data-theme="dark"] .post-card { background: #2a2a2a; }

/* 响应 scanline 配置（CRT 扫描线） */
html[data-scanline="true"] body::after {
    content: ''; position: fixed; top: 0; left: 0; right: 0; bottom: 0;
    background: repeating-linear-gradient(0deg, transparent, transparent 2px, rgba(0,0,0,0.1) 2px, rgba(0,0,0,0.1) 4px);
    pointer-events: none; z-index: 9999;
}
```

### 7.7 触摸优化

移动端需要禁用文字选择高亮、优化触摸滚动：

```css
* {
    -webkit-tap-highlight-color: transparent;
    touch-action: pan-y;
}

/* 允许文字选择（默认会被禁用） */
p, h1, h2, h3, h4, h5, h6, li, blockquote, code, pre, img, a {
    -webkit-user-select: text; user-select: text;
}

/* 代码块允许双向滚动 */
pre, code { touch-action: pan-x pan-y; }
```

---

## 8. JavaScript 开发规范

### 8.1 IIFE 结构

`custom.js` 必须用 IIFE（立即执行函数表达式）包裹，避免全局污染：

```javascript
(function() {
    'use strict';

    // 你的代码...

})();
```

### 8.2 通用功能实现

`custom.js` 负责非文章页的通用功能。文章页由 `post.peb` 的内联脚本处理，通过 `data-gridea-inline="1"` 标记跳过 custom.js 中的重复逻辑。

**完整 custom.js 模板：**

```javascript
(function() {
    'use strict';

    // ===== 阅读进度条 + 回到顶部（非文章页） =====
    var backTopBtn = document.getElementById('back-to-top');
    var progressBar = document.getElementById('reading-progress');

    // 文章页已由内联脚本处理，跳过
    var inlineHandled = (backTopBtn && backTopBtn.dataset.grideaInline === '1') ||
                        (progressBar && progressBar.dataset.grideaInline === '1');

    if (!inlineHandled) {
        var backTopVisible = false;
        var ticking = false;
        var cachedScrollHeight = document.documentElement.scrollHeight;
        var cachedClientHeight = document.documentElement.clientHeight;
        var lastPercent = -1;

        function recalcScrollMetrics() {
            cachedScrollHeight = document.documentElement.scrollHeight;
            cachedClientHeight = document.documentElement.clientHeight;
            lastPercent = -1;
        }

        function onScrollUpdate() {
            var scrollTop = window.scrollY || document.documentElement.scrollTop;
            // 回到顶部按钮显示/隐藏
            if (backTopBtn) {
                var shouldShow = scrollTop > 300;
                if (shouldShow !== backTopVisible) {
                    backTopVisible = shouldShow;
                    if (shouldShow) backTopBtn.classList.add('visible');
                    else backTopBtn.classList.remove('visible');
                }
            }
            // 阅读进度条
            if (progressBar) {
                var scrollRange = cachedScrollHeight - cachedClientHeight;
                var percent = scrollRange > 0 ? (scrollTop / scrollRange) : 0;
                // 量化以减少不必要的重绘
                var quantized = Math.round(percent * 200) / 200;
                if (quantized !== lastPercent) {
                    lastPercent = quantized;
                    progressBar.style.transform = 'scaleX(' + quantized + ')';
                }
            }
            ticking = false;
        }

        // 滚动事件用 rAF 节流
        window.addEventListener('scroll', function() {
            if (!ticking) {
                window.requestAnimationFrame(onScrollUpdate);
                ticking = true;
            }
        }, { passive: true });

        // 窗口尺寸变化时重算
        var resizeTimer = null;
        window.addEventListener('resize', function() {
            if (resizeTimer) clearTimeout(resizeTimer);
            resizeTimer = setTimeout(function() {
                recalcScrollMetrics();
                if (!ticking) { window.requestAnimationFrame(onScrollUpdate); ticking = true; }
            }, 200);
        }, { passive: true });

        onScrollUpdate();

        // 回到顶部点击
        if (backTopBtn) {
            backTopBtn.addEventListener('click', function() {
                window.scrollTo({ top: 0, behavior: 'smooth' });
            });
        }
    }

    // ===== 图片放大（非文章页） =====
    var overlay = document.getElementById('image-zoom-overlay');
    var zoomImg = document.getElementById('image-zoom-img');
    if (overlay && overlay.dataset.grideaInline !== '1' && overlay && zoomImg) {
        document.querySelectorAll('.post-content img, .post-feature img, .featured-cover img, .post-card-cover img').forEach(function(img) {
            img.addEventListener('click', function(e) {
                e.preventDefault();
                e.stopPropagation();
                zoomImg.src = img.src;
                zoomImg.alt = img.alt || '';
                overlay.classList.add('visible');
                overlay.setAttribute('aria-hidden', 'false');
                document.body.style.overflow = 'hidden';
            });
        });
        function closeZoom() {
            overlay.classList.remove('visible');
            overlay.setAttribute('aria-hidden', 'true');
            zoomImg.src = '';
            document.body.style.overflow = '';
        }
        overlay.addEventListener('click', closeZoom);
        document.addEventListener('keydown', function(e) {
            if (e.key === 'Escape') closeZoom();
        });
    }

    // ===== 外部链接新窗口打开 =====
    document.querySelectorAll('a[href^="http"]').forEach(function(a) {
        if (a.target === '_blank') return;
        try {
            var href = a.href;
            if (href && !href.includes(window.location.hostname) && !a.hasAttribute('target')) {
                a.target = '_blank';
                a.rel = 'noopener noreferrer';
            }
        } catch (e) {}
    });

    // ===== 页面切换淡出动效（统一） =====
    if (!document.body.dataset.grideaPageTransition) {
        document.body.dataset.grideaPageTransition = '1';
        document.addEventListener('click', function(e) {
            var link = e.target.closest('a');
            if (!link || link.target === '_blank') return;
            var href = link.getAttribute('href');
            if (!href || href.charAt(0) === '#' || href.indexOf('mailto:') === 0 || href.indexOf('javascript:') === 0) return;
            if (link.hasAttribute('download')) return;
            try {
                var url = new URL(link.href, window.location.href);
                if (url.origin !== window.location.origin) return;
                if (url.href === window.location.href) return;
            } catch (err) { return; }
            e.preventDefault();
            document.body.classList.add('gridea-page-leaving');
            window.setTimeout(function() {
                window.location.href = link.href;
            }, 200);
        }, true);
    }
})();
```

### 8.3 移动端菜单交互

移动端汉堡菜单的切换脚本已在 `base.peb` 中内联（无需在 custom.js 重复）。关键点：

```javascript
// 必须同时切换三个类名，确保跨主题兼容
links.classList.toggle('nav-links-open');
links.classList.toggle('is-open');
links.classList.toggle('open');
toggle.classList.toggle('is-open');
toggle.classList.toggle('active');
```

CSS 中也必须同时支持这三个类名：

```css
.nav-links.nav-links-open,
.nav-links.is-open,
.nav-links.open { display: flex; }
```

### 8.4 文章页内联脚本

文章详情页（post.peb）可在 `{% block scripts %}` 中内联脚本，实现：
- 代码块复制按钮
- 阅读进度条（与 custom.js 相同逻辑，但标记 `data-gridea-inline="1"` 避免重复）
- 回到顶部
- 图片放大

**关键标记：**

```javascript
// 标记这些元素已被内联脚本接管，custom.js 跳过
var backTopBtn = document.getElementById('back-to-top');
var progressBar = document.getElementById('reading-progress');
var overlay = document.getElementById('image-zoom-overlay');
if (backTopBtn) backTopBtn.dataset.grideaInline = '1';
if (progressBar) progressBar.dataset.grideaInline = '1';
if (overlay) overlay.dataset.grideaInline = '1';
```

**用 `{% verbatim %}` 包裹含特殊语法的脚本：**

如果内联脚本中含有 `{`、`}` 等 Pebble 可能误解析的字符，用 `{% verbatim %}` 包裹整个 `<script>` 块。

### 8.5 JS 占位符

`custom.js` 中可使用 `{{变量名}}` 占位符读取主题配置：

```javascript
(function() {
    var accentColor = '{{accent_color}}';  // 替换为用户选择的颜色
    var enableAnim = '{{card_blur}}' !== '0';
})();
```

**特殊占位符 `{{customJs}}`：**

用户在主题配置中填写的自定义 JS 代码会替换 `{{customJs}}` 占位符。APP 会自动剥离误粘贴的 `<script>` 标签并用 try-catch 包裹，防止单个错误导致整个脚本崩溃。

```javascript
(function() {
    'use strict';
    // 主题逻辑...

    {{customJs}}  // 用户自定义代码注入点
})();
```

---

## 9. 路径与分页

### 9.1 baseUrl 机制

每个页面通过 `<base href="{{ baseUrl }}">` 设置基准 URL，所有相对路径基于此解析。`baseUrl` 的值由页面在输出目录中的深度决定：

| 页面 | 输出路径 | baseUrl | 说明 |
|------|----------|---------|------|
| 首页第 1 页 | `index.html` | `./` | 根目录 |
| 首页第 N 页 | `page/N/index.html` | `../../` | 两层深 |
| 归档页 | `archives/index.html` | `../` | 一层深 |
| 标签总览 | `tags/index.html` | `../` | 一层深 |
| 文章详情 | `post/xxx/index.html` | `../../` | 两层深 |
| 标签详情 | `tag/xxx/index.html` | `../../` | 两层深 |
| 友链页 | `links.html` | `./` | 根目录 |
| 404 页 | `404.html` | `./` | 根目录 |

**模板中固定使用 `{{ baseUrl }}`，不要硬编码路径：**

```pebble
{# ✅ 正确 #}
<base href="{{ baseUrl }}">
<a href="./" class="site-logo">首页</a>
<link rel="stylesheet" href="styles/main.css">

{# ❌ 错误：硬编码会导致不同深度页面资源 404 #}
<base href="./">
```

### 9.2 资源路径规范

所有静态资源使用相对 baseUrl 的路径：

| 资源 | 路径 | 说明 |
|------|------|------|
| 主样式 | `styles/main.css` | APP 生成到 `styles/` 目录 |
| 主题脚本 | `scripts/custom.js` | APP 生成到 `scripts/` 目录 |
| 文章图片 | `post-images/xxx.png` | APP 复制到 `post-images/` |
| 站点图片 | `images/xxx.png` | avatar 等，复制到 `images/` |
| CSS 资源 | `{{ asset.src }}` | assets 声明的，保留原路径 |

### 9.3 链接路径规范

- 首页：`./`
- 归档页：`archives/`（受 `theme.archivesPath` 控制，默认 `archives`）
- 标签总览：`tags/`
- 文章详情：`post/fileName/`（受 `theme.postPath` 控制，默认 `post`）
- 标签详情：`tag/slug/`（受 `theme.tagPath` 控制，默认 `tag`）
- 友链：`links.html`

> 这些链接由 APP 生成到 `site.menus`、`post.link`、`tag.link` 等变量中，模板直接使用即可，**不要自行拼接路径**。

### 9.4 分页路径规范

`pagination.prev` 和 `pagination.next` 是**相对根目录的路径**，不是相对当前页的路径：

```pebble
{# ✅ 正确：直接使用，不加前缀 #}
<a href="{{ pagination.prev }}">上一页</a>
<a href="{{ pagination.next }}">下一页</a>

{# ❌ 错误：加 ../../ 前缀会导致路径二次回退 #}
<a href="../../{{ pagination.prev }}">上一页</a>
```

**分页路径值示例：**

| 当前页 | prev | next |
|--------|------|------|
| 第 1 页 | `""`（空） | `page/2/` |
| 第 2 页 | `./` | `page/3/` |
| 第 3 页 | `page/2/` | `page/4/`（或 `""` 若是最后一页） |

**判断逻辑：**

```pebble
{% if pagination.total > 1 %}
<nav class="pagination">
    {% if pagination.prev %}
    <a href="{{ pagination.prev }}" class="page-prev">← 上一页</a>
    {% endif %}
    <span class="page-info">{{ pagination.current }} / {{ pagination.total }}</span>
    {% if pagination.next %}
    <a href="{{ pagination.next }}" class="page-next">下一页 →</a>
    {% endif %}
</nav>
{% endif %}
```

> `pagination.prev` 为空字符串时 `{% if pagination.prev %}` 为 false（空字符串在 Pebble 中为假），不会渲染链接。

---

## 10. 多端适配检查清单

### 手机端（≤768px）

- [ ] 导航变为汉堡菜单，点击展开下拉
- [ ] 文章网格变为单列
- [ ] 侧边栏隐藏（如有）
- [ ] 容器内边距减小（如 20px）
- [ ] 标题字号缩小（如 hero 标题 32px）
- [ ] 友链列表单列
- [ ] 文章导航上下篇堆叠（flex-direction: column）
- [ ] 回到顶部按钮位置调整（bottom: 20px, right: 20px）
- [ ] 代码块横向滚动正常（touch-action: pan-x）
- [ ] 图片不超过屏幕宽度（max-width: 100%）

### 平板端（769-1024px）

- [ ] 网格列数适当减少（如 3 列变 2 列）
- [ ] 特色文章变为单列
- [ ] 容器内边距调整（如 28px）
- [ ] 字号微调（如 15.5px）
- [ ] 侧边栏隐藏（如有）

### 电脑端（>1024px）

- [ ] 完整多列布局
- [ ] 侧边栏显示（如有）
- [ ] 容器内边距充足（如 40px）
- [ ] hover 交互效果生效
- [ ] 卡片悬停动画

### 触摸优化

- [ ] 禁用 tap-highlight（`-webkit-tap-highlight-color: transparent`）
- [ ] 允许文字选择（`-webkit-user-select: text`）
- [ ] 代码块允许双向滚动（`touch-action: pan-x pan-y`）
- [ ] 点击区域足够大（按钮 ≥ 44px）

---

## 11. 常见陷阱与解决方案

### 11.1 Pebble 4.x 类型限制

**陷阱：** `{% if %}` 条件表达式仅接受 Boolean/String/Number，直接判断 ArrayList 或对象会报错。

**解决方案：**

```pebble
{# 列表用 is not empty / is empty #}
{% if posts is not empty %}...{% endif %}
{% if site.menus is not empty %}...{% endif %}
{% if tags is not empty %}...{% endif %}
{% if friendLinks is not empty %}...{% endif %}
{% if archivesByYear is not empty %}...{% endif %}

{# 对象用 is not null / is null #}
{% if post.prevPost is not null %}...{% endif %}
{% if post.nextPost is not null %}...{% endif %}

{# 字符串/Boolean/Number 可直接判断 #}
{% if site.siteAuthor %}...{% endif %}
{% if post.feature %}...{% endif %}
{% if pagination.total > 1 %}...{% endif %}
{% if pagination.prev %}...{% endif %}  {# 空字符串为假 #}
```

### 11.2 模板路径叠加问题

**陷阱：** 分页页面的 `baseUrl` 是 `../../`，如果在链接前再加 `../../` 会导致路径二次回退，资源 404。

**错误示例：**

```pebble
{# ❌ baseUrl 已是 ../../，再加 ../../ 会回退四级 #}
<link rel="stylesheet" href="../../styles/main.css">
<a href="../../{{ pagination.prev }}">上一页</a>
```

**正确做法：**

```pebble
{# ✅ 资源路径相对 baseUrl，不加前缀 #}
<link rel="stylesheet" href="styles/main.css">

{# ✅ 分页路径相对根目录，baseUrl 已处理回退 #}
<a href="{{ pagination.prev }}">上一页</a>
```

### 11.3 分页 prev/next 路径与 base href 冲突

**陷阱：** `pagination.prev`/`next` 是相对根目录的路径（如 `page/2/`），而分页页面的 `<base href="../../">` 已经回退到根目录。如果再给链接加 `../../` 前缀，浏览器会基于 baseUrl 再次回退，导致路径错误（file:// 下尤为明显）。

**解决方案：** 直接使用 `{{ pagination.prev }}` / `{{ pagination.next }}`，不加任何前缀。

### 11.4 移动端菜单类名不统一

**陷阱：** 不同主题使用了不同的类名（`nav-links-open`、`is-open`、`open`），导致切换不生效。

**解决方案：** base.peb 的切换脚本同时 toggle 三个类名，CSS 也同时支持三个：

```css
.nav-links.nav-links-open,
.nav-links.is-open,
.nav-links.open { display: flex; }
```

### 11.5 use-motion 类导致元素隐藏

**陷阱：** 若 CSS 中用 `use-motion` 类配合 opacity:0 初始隐藏元素，但 JS 未正确移除该类，元素永远不可见。

**解决方案：** 避免使用依赖 JS 才能显示的初始隐藏样式。如需入场动画，用 CSS animation 的 `both` 填充模式，确保动画结束元素可见：

```css
/* ✅ 安全：动画结束元素可见 */
.featured-post {
    animation: fadeInUp 0.5s ease both;
}

/* ❌ 危险：依赖 JS 移除类才能显示 */
.use-motion .featured-post { opacity: 0; }
```

### 11.6 ZIP 路径分隔符问题

**陷阱：** Windows 工具（如 PowerShell `Compress-Archive`）打包 ZIP 时可能使用反斜杠 `\` 作为路径分隔符。Android（Linux 内核）不识别 `\`，导致子目录文件被创建为扁平文件名，目录结构丢失。

**解决方案：** APP 导入时已自动将 `\` 替换为 `/`（见 `ThemePackRepository.importTheme`）。但打包时仍推荐使用标准正斜杠。若用命令行打包：

```bash
# 推荐使用标准工具（自动用正斜杠）
cd my-theme
zip -r ../my-theme.zip .

# 或用 7-Zip
7z a -tzip ../my-theme.zip *
```

### 11.7 主题 CSS 为空导致白屏

**陷阱：** `custom.css` 内容为空或解析失败时，`processThemeCss` 会回退到内置默认 CSS，可能与主题预期不符。

**解决方案：** 确保 `custom.css` 至少包含基础样式（`:root` 变量、`body` 样式、`.container` 等）。

### 11.8 自动转义导致 HTML 显示为文本

**陷阱：** 输出含 HTML 标签的变量（如文章正文）未加 `| raw`，导致标签被转义显示为文本。

**解决方案：**

```pebble
{# ✅ 可信 HTML 用 raw #}
{{ post.content | raw }}
{{ post.abstract | raw }}
{{ post.toc | raw }}
{{ commentHtml | raw }}
{{ htmlDataAttrs | raw }}
{{ extraScripts | raw }}
{{ ogTags | raw }}
{{ jsonLd | raw }}
{{ themeVarsStyle | raw }}

{# 纯文本不用 raw（自动转义更安全） #}
{{ post.title }}
{{ site.siteName }}
{{ site.siteDescription }}  {# 但若要去除 Markdown 残留 HTML，加 striptags #}
{{ site.siteDescription | striptags }}
```

### 11.9 配置值类型混淆

**陷阱：** `themePackConfig` 中所有值都是字符串，判断 switch 类型配置时直接 `if` 会出错（字符串 `"false"` 为真）。

**解决方案：**

```pebble
{# ✅ switch 配置与 "true" 字符串比较 #}
{% if themePackConfig.show_hero == "true" %}...{% endif %}
{% if themePackConfig.dark_mode == "true" %}...{% endif %}
{% if themePackConfig.view == "true" %}...{% endif %}

{# ✅ select 配置直接比较值 #}
{% if themePackConfig.columns == "2" %}...{% endif %}
{% if themePackConfig.bg_tone == "pink" %}...{% endif %}

{# ✅ slider/number 配置可比较数字 #}
{% if themePackConfig.content_width > 800 %}...{% endif %}
```

### 11.10 scheme CSS 覆盖

**陷阱：** APP 会自动追加 scheme CSS（如 `main.muse.css`）到 `main.css` 末尾，可能覆盖主题自定义样式。

**解决方案：** 主题样式优先级要足够高，或避免与 scheme CSS 选择器冲突。若不需要 scheme，可不提供对应资源文件（APP 读取失败会跳过）。

---

## 12. 测试清单

发布前必须逐项检查：

### 文件完整性

- [ ] `theme.json` 存在且 JSON 格式合法
- [ ] `custom.css` 存在且非空
- [ ] `custom.js` 存在（可为空注释）
- [ ] `base.peb` 存在且含 `{% block content %}{% endblock %}`
- [ ] `index.peb` 存在且 `extends "base"`
- [ ] `post.peb` 存在且 `extends "base"`
- [ ] `archives.peb` 存在且 `extends "base"`
- [ ] `tags.peb` 存在且 `extends "base"`
- [ ] `tag.peb` 存在且 `extends "base"`
- [ ] `friends.peb` 存在且 `extends "base"`
- [ ] `404.peb` 存在且 `extends "base"`
- [ ] `preview.jpg` 存在

### theme.json 验证

- [ ] `id` 唯一且为英文小写+连字符
- [ ] `name`、`version`、`author`、`description` 非空
- [ ] `isBuiltin` 为 `false`
- [ ] `customConfig` 每项含 `name`、`label`、`group`、`type`、`value`
- [ ] `select` 类型含 `options` 数组
- [ ] `slider` 类型含 `min`、`max`、`step`

### 模板验证

- [ ] 所有模板 `extends "base"`
- [ ] 输出 HTML 的变量加了 `| raw`
- [ ] 列表用 `is not empty` 判断
- [ ] 对象用 `is not null` 判断
- [ ] switch 配置用 `== "true"` 判断
- [ ] `<base href="{{ baseUrl }}">` 存在
- [ ] 分页链接不加 `../../` 前缀
- [ ] `styles/main.css` 和 `scripts/custom.js` 被引用

### CSS 验证

- [ ] `:root` 定义了基本变量
- [ ] 三端媒体查询齐全（≤768px、769-1024px、>1024px）
- [ ] 移动端导航三种类名都支持
- [ ] 页面切换动效 CSS 存在
- [ ] `prefers-reduced-motion` 支持
- [ ] 触摸优化样式存在

### JS 验证

- [ ] IIFE 结构包裹
- [ ] 阅读进度条逻辑正确
- [ ] 回到顶部逻辑正确
- [ ] 图片放大逻辑正确
- [ ] 外部链接新窗口打开
- [ ] 页面切换动效拦截
- [ ] 文章页内联脚本标记 `data-gridea-inline="1"`

### 功能测试

- [ ] 首页文章列表正确显示
- [ ] 分页导航正常跳转
- [ ] 文章详情页正文、目录、评论显示
- [ ] 归档页按年份分组正确
- [ ] 标签页显示标签云
- [ ] 标签详情页显示对应文章
- [ ] 友链页显示友链卡片
- [ ] 404 页显示
- [ ] 移动端汉堡菜单可展开/收起
- [ ] 阅读进度条随滚动更新
- [ ] 回到顶部按钮滚动后出现
- [ ] 图片点击放大可关闭
- [ ] 代码块复制按钮工作
- [ ] 页面切换有淡入淡出动画
- [ ] 配置项变更后样式正确响应

### 路径测试

- [ ] 首页资源加载正常（baseUrl=`./`）
- [ ] 分页页资源加载正常（baseUrl=`../../`）
- [ ] 文章页资源加载正常（baseUrl=`../../`）
- [ ] 归档页资源加载正常（baseUrl=`../`）
- [ ] 所有内部链接可正确跳转

---

## 13. 打包与发布

### 13.1 打包规范

主题包打包为 `.zip` 文件，ZIP 内直接包含所有文件（不要有额外的顶层目录）：

```
my-theme.zip
├── theme.json
├── custom.css
├── custom.js
├── base.peb
├── index.peb
├── post.peb
├── archives.peb
├── tags.peb
├── tag.peb
├── friends.peb
├── 404.peb
├── preview.jpg
└── (可选) fonts/、images/、scripts/ 等资源目录
```

**ZIP 结构要求：**
- `theme.json` 必须在 ZIP 根目录或一级子目录（APP 会递归查找）
- 路径分隔符使用正斜杠 `/`（标准 ZIP 规范）
- 不包含 `.DS_Store`、`Thumbs.db` 等系统文件

### 13.2 打包命令

```bash
# 进入主题目录
cd my-theme

# 使用 zip 命令（推荐）
zip -r ../my-theme.zip . -x "*.DS_Store" -x "*Thumbs.db"

# 或使用 7-Zip
7z a -tzip ../my-theme.zip *

# 或使用 PowerShell（注意：可能使用反斜杠，APP 会自动转换）
Compress-Archive -Path * -DestinationPath ../my-theme.zip
```

### 13.3 导入 APP

1. 将 `.zip` 文件传输到 Android 设备
2. 在 Gridea APP 的主题管理页面选择"导入主题"
3. 选择 `.zip` 文件，APP 自动解压到 `filesDir/themes/{themeId}/`
4. 导入后在主题列表中选择该主题并配置

### 13.4 预览图

`preview.jpg` 是主题预览缩略图，建议：
- 尺寸：800×600 或 16:10 比例
- 内容：主题首页截图，能体现主题风格
- 大小：≤ 200KB

> 用户主题包支持 `preview.jpg`（jpg 格式），APP 会以 `file://` 协议加载。

---

## 14. AI 助手开发指引

本节专为 AI 助手（如 GLM、Claude、GPT 等）提供开发要点，确保生成的主题代码符合规范。

### 14.1 开发前检查

1. **确认目标平台**：Gridea Android 客户端，Pebble 4.x 模板引擎
2. **确认主题风格**：从用户描述中提取设计关键词（配色、布局、字体、特色）
3. **规划配置项**：确定哪些设计元素可配置（颜色、宽度、开关等）

### 14.2 代码生成要点

#### theme.json

- `id` 用英文蛇形或连字符（如 `magazine`、`my-cool-theme`）
- `group` 用中文（`颜色`、`排版`、`统计`）
- 统计配置项（`ga`、`baidu`、`tencent`、`view`）固定包含，保持跨主题一致
- `type` 与 `value` 类型匹配（switch→boolean，slider→number，color→string）

#### Pebble 模板

- **必须** `extends "base"`（除 base.peb 自身）
- **必须** 用 `is not empty` 判断列表（`posts`、`tags`、`site.menus`、`friendLinks`、`archivesByYear`）
- **必须** 用 `is not null` 判断对象（`post.prevPost`、`post.nextPost`）
- **必须** 给 HTML 内容变量加 `| raw`（`post.content`、`post.abstract`、`post.toc`、`commentHtml`、`htmlDataAttrs` 等）
- **禁止** 直接 `if` 判断列表/对象
- **禁止** 硬编码路径（用 `{{ baseUrl }}`、`{{ post.link }}`、`{{ tag.link }}`）
- **禁止** 给分页链接加 `../../` 前缀
- 内联 `<script>` 含 `{`、`}` 字符时用 `{% verbatim %}` 包裹

#### CSS

- **必须** 定义 `:root` 变量集
- **必须** 包含三端媒体查询
- **必须** 支持移动端菜单三种类名（`nav-links-open`、`is-open`、`open`）
- **必须** 包含页面切换动效（`grideaPageEnter`、`gridea-page-leaving`）
- **必须** 支持 `prefers-reduced-motion`
- **必须** 包含触摸优化样式
- 用 `{{变量名}}` 占位符读取配置值
- 用 `html[data-xxx="value"]` 属性选择器响应 data 属性配置

#### JavaScript

- **必须** 用 IIFE 包裹
- **必须** 实现：阅读进度条、回到顶部、图片放大、外部链接新窗口、页面切换动效
- **必须** 检查 `data-gridea-inline` 标记避免与文章页内联脚本重复
- **必须** 用 `requestAnimationFrame` 节流滚动事件
- **必须** 用 `{ passive: true }` 优化滚动监听
- 阅读进度用 `transform: scaleX()` 而非 `width`（性能更好）
- 进度值量化（`Math.round(percent * 200) / 200`）减少重绘

### 14.3 AI 助手自检清单

生成主题代码后，AI 助手必须逐项自检：

**Pebble 模板自检：**
- [ ] 所有子模板都 `extends "base"`？
- [ ] 列表判断都用 `is not empty`？
- [ ] 对象判断都用 `is not null`？
- [ ] HTML 输出变量都加了 `| raw`？
- [ ] 分页链接没有 `../../` 前缀？
- [ ] `{{ baseUrl }}` 用于 `<base href>`？
- [ ] switch 配置用 `== "true"` 判断？
- [ ] 含特殊字符的内联脚本用 `{% verbatim %}` 包裹？

**CSS 自检：**
- [ ] `:root` 变量定义完整？
- [ ] 三端媒体查询都存在？
- [ ] 移动端菜单三种类名都支持？
- [ ] 页面切换动效 CSS 存在？
- [ ] `prefers-reduced-motion` 支持？
- [ ] 触摸优化样式存在？
- [ ] 配置占位符 `{{}}` 语法正确？

**JavaScript 自检：**
- [ ] IIFE 包裹？
- [ ] 五大通用功能都实现？
- [ ] `data-gridea-inline` 检查存在？
- [ ] 滚动事件用 rAF 节流？
- [ ] 事件监听用 `{ passive: true }`？

**theme.json 自检：**
- [ ] `id` 唯一且格式正确？
- [ ] 所有配置项含必需字段？
- [ ] `select` 类型有 `options`？
- [ ] `slider` 类型有 `min`/`max`/`step`？
- [ ] `isBuiltin` 为 `false`？
- [ ] 统计配置项（ga/baidu/tencent/view）包含？

### 14.4 常见错误模式（AI 易犯）

| 错误 | 正确做法 |
|------|----------|
| `{% if posts %}` | `{% if posts is not empty %}` |
| `{% if post.prevPost %}` | `{% if post.prevPost is not null %}` |
| `{{ post.content }}` | `{{ post.content \| raw }}` |
| `<a href="../../{{ pagination.next }}">` | `<a href="{{ pagination.next }}">` |
| `<base href="./">` | `<base href="{{ baseUrl }}">` |
| `{% if themePackConfig.show_hero %}` | `{% if themePackConfig.show_hero == "true" %}` |
| `links.classList.toggle('open')` 单一类名 | 同时 toggle `nav-links-open`、`is-open`、`open` |
| `progressBar.style.width = percent + '%'` | `progressBar.style.transform = 'scaleX(' + percent + ')'` |
| 未用 IIFE 包裹 JS | `(function() { 'use strict'; ... })();` |
| 滚动事件未节流 | `window.requestAnimationFrame` + `ticking` 标志 |

### 14.5 推荐开发流程（AI）

1. **分析需求**：提取用户描述中的风格关键词
2. **生成 theme.json**：定义 id、名称、配置项（颜色/排版/统计）
3. **生成 base.peb**：参考 [3.3 节](#33-编写-basepeb基础骨架) 模板，按需调整布局
4. **生成 index.peb**：实现文章列表，呼应主题风格
5. **生成 post.peb**：实现文章详情，含内联脚本
6. **生成 archives.peb、tags.peb、tag.peb、friends.peb、404.peb**：参考 [第 6 节](#6-页面模板详解)
7. **生成 custom.css**：定义变量、组件样式、响应式、动效
8. **生成 custom.js**：实现五大通用功能
9. **自检**：按 [14.3 节](#143-ai-助手自检清单) 逐项检查
10. **打包**：按 [第 13 节](#13-打包与发布) 生成 .zip

### 14.6 设计风格参考

以下设计元素可配置化，便于用户调整：

- **配色方案**：主色（accent_color）、背景色调（bg_tone）
- **布局**：列数（columns）、内容宽度（content_width）、侧边栏宽度（sidebar_width）
- **排版**：字体选择、首字下沉（drop_cap）、首页大标题（show_hero）
- **特效**：CRT 扫描线（scanline）、光标闪烁（cursor_blink）、玻璃模糊（card_blur）
- **功能**：暗色模式（dark_mode）、社交链接（show_social）、竖排标题（vertical_title）

通过 `<html>` 的 `data-*` 属性 + CSS 属性选择器实现配置响应，参考 [7.6 节](#76-通过-data-属性响应配置)。

---

## 附录：变量速查表

### 模板变量完整列表

| 变量路径 | 类型 | 所在页面 | 说明 |
|----------|------|----------|------|
| `site.siteName` | string | 全部 | 站点名 |
| `site.siteDescription` | string | 全部 | 站点描述 |
| `site.siteAuthor` | string | 全部 | 作者 |
| `site.siteFavicon` | string | 全部 | favicon URL |
| `site.siteAvatar` | string | 全部 | 头像 URL |
| `site.footerInfo` | string | 全部 | 页脚信息 |
| `site.domain` | string | 全部 | 域名 |
| `site.menus` | list | 全部 | 菜单数组 |
| `menu.name` | string | 全部 | 菜单名 |
| `menu.link` | string | 全部 | 菜单链接 |
| `menu.openType` | string | 全部 | 打开方式（`External` 时新窗口） |
| `baseUrl` | string | 全部 | base href 值 |
| `title` | string | 全部 | 页面标题 |
| `htmlDataAttrs` | string | 全部 | html data 属性（需 raw） |
| `themePackConfig.*` | string | 全部 | 主题配置值 |
| `showHero` | boolean | 全部 | 是否显示首页大标题 |
| `cssAssets` | list | 全部 | CSS 资源 |
| `jsAssets` | list | 全部 | JS 资源 |
| `posts` | list | index/tag | 文章列表 |
| `post.fileName` | string | index/tag | 文件名 |
| `post.title` | string | index/tag/post | 标题 |
| `post.content` | string | post | HTML 正文（需 raw） |
| `post.abstract` | string | index/tag | HTML 摘要（需 raw） |
| `post.description` | string | 全部 | 纯文本描述 |
| `post.date` | string | index/tag/post | 日期 |
| `post.tags` | list | index/tag/post | 标签数组 |
| `tag.name` | string | 全部 | 标签名 |
| `tag.slug` | string | 全部 | 标签 slug |
| `tag.link` | string | 全部 | 标签链接 |
| `tag.count` | number | tags | 文章数 |
| `post.feature` | string | index/tag/post | 封面图 URL |
| `post.link` | string | index/tag/post | 文章链接 |
| `post.hideInList` | boolean | index | 是否隐藏 |
| `post.isTop` | boolean | index | 是否置顶 |
| `post.stats.words` | number | post | 字数 |
| `post.stats.minutes` | number | post | 阅读分钟 |
| `post.toc` | string | post | 目录 HTML（需 raw） |
| `post.prevPost` | object\|null | post | 上一篇 |
| `post.nextPost` | object\|null | post | 下一篇 |
| `pagination.prev` | string | index | 上一页链接 |
| `pagination.next` | string | index | 下一页链接 |
| `pagination.current` | number | index | 当前页码 |
| `pagination.total` | number | index | 总页数 |
| `post` | object | post | 当前文章（含上述 post 字段） |
| `commentHtml` | string | post | 评论 HTML（需 raw） |
| `archivesByYear` | list | archives | 年份分组 |
| `year.year` | string | archives | 年份 |
| `year.posts` | list | archives | 该年文章 |
| `tags` | list | tags/index | 标签列表 |
| `tag` | object | tag | 当前标签 |
| `friendLinks` | list | friends | 友链数组 |
| `link.name` | string | friends | 友链名 |
| `link.url` | string | friends | 友链 URL |
| `link.avatar` | string | friends | 友链头像 |
| `link.description` | string | friends | 友链描述 |

### 过滤器速查

| 过滤器 | 用法 | 说明 |
|--------|------|------|
| `raw` | `{{ var \| raw }}` | 输出不转义的原始 HTML |
| `striptags` | `{{ var \| striptags }}` | 去除 HTML 标签 |
| `https_upgrade` | `{{ url \| https_upgrade }}` | http:// 升级为 https:// |
| `default` | `{{ var \| default("x") }}` | 变量为空时使用默认值 |
| `slice` | `{{ list \| slice(1, 5) }}` | 列表切片 |
| `length` | `{{ list \| length }}` | 列表/字符串长度 |

---

**文档版本**：1.0.0  
**最后更新**：2026-08-02  
**适用 Gridea 版本**：基于 Pebble 4.x 的 Android 客户端  

如有疑问，请参考 `themes/prototypes/` 下的现有主题实现作为完整示例。
