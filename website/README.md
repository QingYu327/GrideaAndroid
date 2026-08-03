# Gridea Android 官网

> Gridea Android 项目的官方网站源码，包含首页、主题画廊、文档三大页面。

纯静态站点，无构建步骤，HTML + CSS + 原生 JavaScript，可直接部署到 Vercel、EdgeOne、Netlify、GitHub Pages 等静态托管平台。

---

## 目录结构

```
website/
├── index.html              # 首页（产品介绍、核心功能、主题预览、下载入口）
├── themes.html             # 主题画廊（6 款内置主题展示与下载）
├── vercel.json             # Vercel 部署配置（Clean URLs + 路由重写）
├── README.md               # 本文件
├── css/
│   └── style.css           # 全局样式（设计令牌、组件、响应式、暗色模式）
├── js/
│   └── main.js             # 全局脚本（主题切换、滚动渐入、文档目录、scrollspy）
├── assets/                 # 静态资源
│   ├── icon.png            # 站点图标
│   ├── icons/
│   │   └── app-icon.png    # App 图标（导航栏使用）
│   └── THEME_DEVELOPMENT_GUIDE.md  # 主题开发完整指南（可下载）
├── docs/
│   └── index.html          # 文档页面（快速开始、写作、预览、主题、部署、设置、FAQ）
└── Download/               # 下载资源目录（主题包、APK）
    ├── app-release.apk     # Android 安装包
    ├── magazine.zip        # Magazine 主题包
    ├── retro.zip           # Retro 主题包
    ├── masonry.zip         # Masonry 主题包
    ├── sidebar.zip         # Sidebar 主题包
    ├── terminal.zip        # Terminal 主题包
    └── ink.zip             # Ink 主题包
```

---

## 页面说明

### 首页（index.html）
- Hero 区域：产品标语 + 编辑器模拟界面
- 核心功能：6 张功能卡片（写作、预览、主题、部署、隐私、体验）
- 精选主题：6 款内置主题预览
- 页脚：产品 / 文档 / 社区三栏导航

### 主题画廊（themes.html）
- 6 款主题卡片展示（预览图 + 标题 + 描述 + 标签）
- 每张卡片右下角提供下载按钮，点击可下载对应主题包
- 主题包下载源为 GitHub Release，由 GitHub CDN 加速

### 文档（docs/index.html）
- 左侧目录抽屉（手机端可滑出，支持独立滚动 + scrollspy 动态定位）
- 右侧内容区，涵盖：快速开始、写作、预览、主题、部署、设置、FAQ
- 含主题开发指南下载入口

---

## 设计规范

- **主色调**：淡紫 `#9C8FDA`（渐变 `#9C8FDA → #B7A8F0`）
- **字体**：华文中宋（STZhongsong），手机端回退到思源宋体（Noto Serif SC，jsDelivr CDN 加载）
- **圆角**：10px / 16px / 22px 三级
- **响应式断点**：手机 ≤768px / 平板 769-1024px / 电脑 >1024px
- **暗色模式**：通过 `data-theme="dark"` 属性切换，支持手动切换 + 跟随系统

---

## 本地预览

无需安装任何依赖，用任意静态服务器即可运行：

```bash
# Python
python -m http.server 8000

# Node.js (需安装 http-server)
npx http-server -p 8000
```

然后浏览器访问 `http://localhost:8000`。

---

## 部署

### Vercel
1. 将 `website/` 目录上传到 GitHub 仓库
2. 在 Vercel 导入该仓库，Root Directory 设为 `website`
3. `vercel.json` 已配置好 Clean URLs 和路由重写，无需额外设置

### 腾讯云 EdgeOne
1. 将 `website/` 目录推送到 Gitee/GitHub 仓库
2. 在 EdgeOne 静态托管中接入该仓库
3. 按提示绑定域名

### GitHub Pages
1. 将 `website/` 内容推送到仓库的 `gh-pages` 分支或 `docs/` 目录
2. 在仓库 Settings → Pages 中开启

---

## 技术栈

- HTML5（语义化标签）
- CSS3（自定义属性、Flexbox、Grid、`clamp()` 响应式排版）
- 原生 JavaScript（无框架、无构建工具）
- Critical CSS 内联（避免 FOUC）
- jsDelivr CDN（思源宋体加载）

---

## 相关文档

- [Gridea Android 主仓库](https://github.com/QingYu327/GrideaAndroid)
- [主题开发指南](./assets/THEME_DEVELOPMENT_GUIDE.md)
- [Release 下载](https://github.com/QingYu327/GrideaAndroid/releases)

---

## 许可证

本官网源码随主项目采用 GPL-3.0 许可证。
