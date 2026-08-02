package com.gridea.android.renderer

import com.gridea.android.data.model.Theme

/**
 * 默认 CSS 生成器：根据 [Theme] 配置生成站点默认样式表。
 */
object DefaultCssGenerator {

    fun getDefaultCss(theme: Theme): String {
        // 字体族映射
        val fontFamilyStack = when (theme.fontFamily) {
            "system" -> "-apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, 'Helvetica Neue', Arial, sans-serif"
            "serif" -> "Georgia, 'Times New Roman', 'Source Han Serif SC', 'Noto Serif CJK SC', serif"
            "mono" -> "'Courier New', Consolas, Monaco, 'Source Han Mono SC', monospace"
            "sans" -> "'Helvetica Neue', Helvetica, Arial, 'Source Han Sans SC', 'Noto Sans CJK SC', sans-serif"
            else -> "-apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, 'Helvetica Neue', Arial, sans-serif"
        }
        val width = theme.contentWidth.coerceIn(480, 1400)
        val radius = theme.borderRadius.coerceIn(0, 32)
        val primary = theme.primaryColor.ifBlank { "#42b983" }
        val textColor = theme.textColor.ifBlank { "#2c3e50" }
        val bgColor = theme.backgroundColor.ifBlank { "#ffffff" }

        return """
        :root {
            --primary: $primary;
            --text: $textColor;
            --bg: $bgColor;
            --bg-card: $bgColor;
            --radius: ${radius}px;
            --max-width: ${width}px;
        }

        * { margin: 0; padding: 0; box-sizing: border-box; }
        /* 关键：移动端禁用 tap highlight（避免 300ms 延迟和重绘开销）
           禁用文本选择高亮（长按不会出现蓝色高亮） */
        * {
            -webkit-tap-highlight-color: transparent;
            -webkit-user-select: none;
            user-select: none;
            /* 关键：全局只允许垂直滚动，避免手势冲突
               这是"边缘流畅，文章内容卡"的修复：文章卡片内的链接/图片在滑动时
               会触发点击判断，touch-action: pan-y 让浏览器直接处理垂直滑动 */
            touch-action: pan-y;
        }
        /* 允许文本和图片选择（用户复制文章内容） */
        p, h1, h2, h3, h4, h5, h6, li, blockquote, code, pre, img, a {
            -webkit-user-select: text; user-select: text;
        }
        /* 代码块允许水平滚动 */
        pre, code { touch-action: pan-x pan-y; }

        body {
            font-family: $fontFamilyStack;
            color: var(--text);
            background: var(--bg);
            line-height: 1.6;
            -webkit-font-smoothing: antialiased;
        }

        a { color: var(--primary); text-decoration: none; }
        a:hover { text-decoration: underline; }

        .container { max-width: var(--max-width); margin: 0 auto; padding: 0 20px; }

        .site-header {
            padding: 40px 0 30px;
            border-bottom: 1px solid rgba(0,0,0,0.06);
            text-align: center;
        }
        .site-logo {
            font-size: 28px;
            font-weight: bold;
            color: var(--text);
        }
        .site-avatar {
            width: 72px;
            height: 72px;
            border-radius: 50%;
            object-fit: cover;
            margin: 0 auto 12px;
            display: block;
            box-shadow: 0 2px 8px rgba(0,0,0,0.08);
        }
        .site-description {
            color: rgba(0,0,0,0.5);
            font-size: 14px;
            margin-top: 8px;
        }
        .site-author {
            color: rgba(0,0,0,0.7);
            font-size: 13px;
            margin-top: 6px;
        }
        .site-nav {
            margin-top: 16px;
            display: flex;
            align-items: center;
            gap: 4px;
        }
        .nav-links {
            display: flex;
            gap: 20px;
            flex-wrap: nowrap;
            overflow-x: auto;
            -webkit-overflow-scrolling: touch;
            scrollbar-width: none;
            flex: 1;
        }
        .nav-links::-webkit-scrollbar { display: none; }
        .nav-link { font-size: 14px; color: var(--text); white-space: nowrap; }
        .nav-link:hover { color: var(--primary); }
        /* 溢出菜单按钮：桌面端隐藏，手机端显示 */
        .nav-toggle {
            display: none;
            flex-direction: column;
            justify-content: space-around;
            width: 30px;
            height: 24px;
            background: transparent;
            border: none;
            cursor: pointer;
            padding: 0;
        }
        .nav-toggle-bar {
            width: 100%;
            height: 2px;
            background: var(--text);
            border-radius: 2px;
            transition: all 0.2s;
        }
        .nav-toggle[aria-expanded="true"] .nav-toggle-bar:nth-child(1) { transform: translateY(8px) rotate(45deg); }
        .nav-toggle[aria-expanded="true"] .nav-toggle-bar:nth-child(2) { opacity: 0; }
        .nav-toggle[aria-expanded="true"] .nav-toggle-bar:nth-child(3) { transform: translateY(-8px) rotate(-45deg); }
        @media (max-width: 768px) {
            .nav-toggle { display: flex; }
            .nav-links {
                display: none;
                flex-direction: column;
                position: absolute;
                top: 100%;
                left: 0;
                right: 0;
                background: var(--bg-card, #fff);
                padding: 12px 20px;
                box-shadow: 0 4px 12px rgba(0,0,0,0.08);
                z-index: 100;
                gap: 12px;
            }
            .nav-links.nav-links-open { display: flex; }
            .site-nav { position: relative; }
        }

        main { padding: 40px 0; min-height: 60vh; }

        .post-card {
            background: var(--bg-card);
            border-radius: var(--radius);
            overflow: hidden;
            margin-bottom: 24px;
            border: 1px solid rgba(0,0,0,0.06);
            /* 让浏览器跳过屏幕外卡片的渲染，文章多时显著降低 GPU 负担 */
            content-visibility: auto;
            contain-intrinsic-size: 400px;
            /* CSS Containment 隔离重排范围 */
            contain: layout style paint;
            /* touch-action: pan-y 让浏览器只处理垂直滚动，避免手势冲突 */
            touch-action: pan-y;
        }
        .post-card-feature img {
            width: 100%;
            height: 200px;
            object-fit: cover;
            display: block;
            /* 占位背景：避免图片懒加载完成前出现空白闪烁 */
            background: #f0f0f0;
        }
        .post-card-body { padding: 20px 24px; }
        .post-card-title { font-size: 20px; margin-bottom: 8px; }
        .post-card-title a { color: var(--text); }
        .post-card-meta { font-size: 13px; color: rgba(0,0,0,0.45); margin-bottom: 12px; }
        /* 摘要限制 3 行：长摘要不再一次性渲染大量文字，减少每张卡片的绘制成本 */
        .post-card-abstract {
            font-size: 14px;
            color: rgba(0,0,0,0.6);
            margin-bottom: 12px;
            display: -webkit-box;
            -webkit-line-clamp: 3;
            -webkit-box-orient: vertical;
            overflow: hidden;
            text-overflow: ellipsis;
        }
        .post-card-tags { margin-bottom: 8px; }
        .read-more { font-size: 14px; font-weight: 500; }

        .post-detail { padding: 0; }
        .post-header { margin-bottom: 32px; }
        .post-title { font-size: 28px; margin-bottom: 12px; }
        .post-meta { font-size: 13px; color: rgba(0,0,0,0.45); display: flex; gap: 16px; }
        .post-tags { margin-top: 8px; }
        .tag-chip {
            display: inline-block;
            padding: 2px 10px;
            border-radius: 12px;
            font-size: 12px;
            background: rgba(66,185,131,0.1);
            color: var(--primary);
            margin-right: 4px;
        }
        .post-feature img { width: 100%; max-height: 400px; object-fit: cover; border-radius: var(--radius); margin-bottom: 24px; }

        .post-content {
            font-size: 16px;
            line-height: 1.8;
        }
        .post-content h1, .post-content h2, .post-content h3 { margin: 24px 0 12px; }
        .post-content p { margin-bottom: 16px; }
        .post-content pre {
            background: #1e1e2e;
            color: #cdd6f4;
            padding: 16px;
            border-radius: var(--radius);
            overflow-x: auto;
            margin: 16px 0;
            font-size: 13px;
            line-height: 1.6;
            position: relative;
            -webkit-overflow-scrolling: touch;
        }
        .post-content code {
            font-family: 'Courier New', Consolas, Monaco, monospace;
        }
        /* 行内代码：浅色背景突出显示 */
        .post-content :not(pre) > code {
            background: rgba(0,0,0,0.06);
            padding: 2px 6px;
            border-radius: 4px;
            font-size: 0.9em;
        }
        .post-content img { max-width: 100%; border-radius: var(--radius); margin: 16px 0; cursor: zoom-in; }
        .post-content blockquote {
            border-left: 4px solid var(--primary);
            padding-left: 16px;
            color: rgba(0,0,0,0.5);
            margin: 16px 0;
        }
        .post-content ul, .post-content ol { padding-left: 24px; margin-bottom: 16px; }

        .post-navigation {
            display: flex;
            justify-content: space-between;
            margin-top: 48px;
            padding-top: 24px;
            border-top: 1px solid rgba(0,0,0,0.06);
            flex-wrap: wrap;
            gap: 16px;
        }
        .post-nav .nav-label { display: block; font-size: 12px; color: rgba(0,0,0,0.4); margin-bottom: 4px; }
        .comment-section { margin-top: 48px; }
        /* 评论预览占位：预览模式下不加载评论系统 CDN，只显示提示文字 */
        .comment-preview-placeholder {
            margin-top: 48px;
            padding: 24px;
            border: 1px dashed rgba(0,0,0,0.15);
            border-radius: var(--radius);
            text-align: center;
            background: rgba(0,0,0,0.02);
        }
        .comment-placeholder-text {
            color: rgba(0,0,0,0.5);
            font-size: 14px;
            margin: 0;
        }

        .pagination {
            display: flex;
            justify-content: center;
            align-items: center;
            gap: 16px;
            margin-top: 32px;
        }
        .page-prev, .page-next {
            padding: 6px 16px;
            border-radius: var(--radius);
            border: 1px solid rgba(0,0,0,0.1);
            font-size: 14px;
        }
        .page-info { font-size: 14px; color: rgba(0,0,0,0.5); }

        .archives-page, .tags-page, .tag-detail { padding: 0; }
        .page-title { font-size: 24px; margin-bottom: 24px; }
        .archive-year { margin-bottom: 32px; }
        .archive-year-title { font-size: 20px; margin-bottom: 12px; color: var(--primary); }
        .archive-list { list-style: none; }
        .archive-item { padding: 8px 0; border-bottom: 1px solid rgba(0,0,0,0.04); display: flex; flex-wrap: wrap; align-items: baseline; gap: 8px; }
        .archive-date { font-size: 13px; color: rgba(0,0,0,0.4); flex-shrink: 0; white-space: nowrap; }
        .archive-title { font-size: 15px; color: var(--text-primary); text-decoration: none; flex: 1; min-width: 0; }
        .archive-title:hover { color: var(--primary); }

        .tags-cloud { display: flex; flex-wrap: wrap; gap: 12px; }
        .tag-card {
            display: inline-flex;
            align-items: center;
            gap: 6px;
            padding: 8px 16px;
            border-radius: var(--radius);
            background: rgba(66,185,131,0.08);
            color: var(--text);
            font-size: 14px;
        }
        .tag-card:hover { background: rgba(66,185,131,0.15); text-decoration: none; }
        .tag-count { font-size: 12px; color: rgba(0,0,0,0.4); }

        .empty { text-align: center; color: rgba(0,0,0,0.3); padding: 60px 0; }

        /* 友情链接页 */
        .friend-links-page { padding: 0; }
        .friend-links-list {
            display: grid;
            grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
            gap: 16px;
        }
        .friend-link-card {
            display: flex;
            align-items: center;
            gap: 14px;
            padding: 16px 20px;
            border-radius: var(--radius);
            background: var(--bg-card);
            /* 同样用 border 替代 box-shadow，避免独立合成层 */
            border: 1px solid rgba(0,0,0,0.06);
            color: var(--text);
        }
        .friend-link-card:hover {
            border-color: var(--primary);
            text-decoration: none;
        }
        .friend-link-avatar {
            width: 48px;
            height: 48px;
            border-radius: 50%;
            object-fit: cover;
            flex-shrink: 0;
        }
        .friend-link-info { min-width: 0; overflow: hidden; }
        .friend-link-name {
            display: block;
            font-size: 16px;
            font-weight: 600;
            color: var(--text);
            overflow: hidden;
            text-overflow: ellipsis;
            white-space: nowrap;
        }
        .friend-link-desc {
            font-size: 13px;
            color: rgba(0,0,0,0.5);
            margin-top: 4px;
            overflow: hidden;
            text-overflow: ellipsis;
            display: -webkit-box;
            -webkit-line-clamp: 2;
            -webkit-box-orient: vertical;
        }

        /* TOC 目录样式 */
        .toc {
            background: rgba(0,0,0,0.03);
            border-left: 3px solid var(--primary);
            border-radius: var(--radius);
            padding: 16px 20px;
            margin: 24px 0;
            font-size: 14px;
        }
        .toc-title { font-weight: 600; margin-bottom: 8px; color: var(--text); }
        .toc ul { list-style: none; padding-left: 12px; margin: 0; }
        .toc-list { padding-left: 0; }
        .toc li { padding: 4px 0; }
        .toc a { color: var(--text); text-decoration: none; opacity: 0.8; }
        .toc a:hover { color: var(--primary); opacity: 1; }
        .toc-level-1 { font-weight: 600; }
        .toc-level-2 { padding-left: 16px; }
        .toc-level-3 { padding-left: 32px; font-size: 13px; }
        .toc-level-4, .toc-level-5, .toc-level-6 { padding-left: 48px; font-size: 12px; opacity: 0.7; }

        /* 任务列表样式 */
        .post-content .task-list-item { list-style: none; }
        .post-content .task-list-item input[type="checkbox"] { margin-right: 8px; }

        /* 高亮、上下标样式 */
        .post-content mark { background: rgba(255,235,59,0.4); padding: 2px 4px; border-radius: 2px; }
        .post-content sup, .post-content sub { font-size: 0.75em; }

        /* 代码块语法高亮 class（配合 Prism.js CSS） */
        .post-content pre code[class*="language-"] { display: block; }
        .post-content code[class*="language-"] { font-family: 'Courier New', Consolas, Monaco, monospace; }

        .site-footer {
            padding: 40px 0;
            border-top: 1px solid rgba(0,0,0,0.06);
            text-align: center;
            font-size: 13px;
            color: rgba(0,0,0,0.4);
        }

        /* ===== 阅读进度条 ===== */
        .reading-progress-container {
            position: fixed;
            top: 0;
            left: 0;
            width: 100%;
            height: 3px;
            background: rgba(0,0,0,0.05);
            z-index: 9999;
        }
        .reading-progress-bar {
            height: 100%;
            width: 0;
            background: var(--primary);
            /* 用 transform 触发 GPU 合成层，避免 width 修改触发重排 */
            will-change: transform;
            transform-origin: left center;
        }
        /* 进度条外层用 fixed + transform，避免父元素影响 */
        .reading-progress-container {
            transform: translateZ(0);
        }

        /* ===== 代码块复制按钮 ===== */
        .code-copy-btn {
            position: absolute;
            top: 8px;
            right: 8px;
            padding: 4px 10px;
            font-size: 12px;
            line-height: 1;
            color: var(--text);
            background: rgba(255,255,255,0.6);
            border: 1px solid rgba(0,0,0,0.1);
            border-radius: 4px;
            cursor: pointer;
            opacity: 0;
            transition: opacity 0.2s, background 0.2s, color 0.2s;
            z-index: 1;
        }
        .post-content pre:hover .code-copy-btn { opacity: 1; }
        .code-copy-btn:hover {
            background: rgba(255,255,255,0.95);
            border-color: var(--primary);
        }
        .code-copy-btn.copied {
            color: var(--primary);
            border-color: var(--primary);
        }

        /* ===== 回到顶部按钮 ===== */
        .back-to-top {
            position: fixed;
            right: 24px;
            bottom: 24px;
            width: 44px;
            height: 44px;
            border: none;
            border-radius: 50%;
            background: var(--primary);
            color: #fff;
            font-size: 20px;
            line-height: 44px;
            text-align: center;
            cursor: pointer;
            opacity: 0;
            visibility: hidden;
            transform: translateZ(0) translateY(10px);
            transition: opacity 0.3s, visibility 0.3s, transform 0.3s;
            box-shadow: 0 2px 8px rgba(0,0,0,0.2);
            z-index: 9998;
            padding: 0;
            /* 提升到独立合成层，避免滑动时整个 WebView 重绘 */
            will-change: transform, opacity;
            backface-visibility: hidden;
        }
        .back-to-top.visible {
            opacity: 1;
            visibility: visible;
            transform: translateZ(0) translateY(0);
        }
        .back-to-top:hover { filter: brightness(1.1); }
        .back-to-top-icon { pointer-events: none; }

        /* ===== 图片点击放大遮罩 ===== */
        .image-zoom-overlay {
            position: fixed;
            top: 0;
            left: 0;
            width: 100%;
            height: 100%;
            background: rgba(0,0,0,0.85);
            display: flex;
            align-items: center;
            justify-content: center;
            opacity: 0;
            visibility: hidden;
            transition: opacity 0.3s, visibility 0.3s;
            z-index: 10000;
            cursor: zoom-out;
            padding: 20px;
            /* 提升到独立合成层，避免对正常滑动造成重绘开销 */
            will-change: opacity;
            transform: translateZ(0);
        }
        .image-zoom-overlay.visible {
            opacity: 1;
            visibility: visible;
        }
        .image-zoom-img {
            max-width: 90%;
            max-height: 90%;
            border-radius: 4px;
            object-fit: contain;
            box-shadow: 0 4px 24px rgba(0,0,0,0.4);
        }

        /* ===== 暗色主题适配 ===== */
        @media (prefers-color-scheme: dark) {
            .reading-progress-container { background: rgba(255,255,255,0.08); }
            .code-copy-btn {
                background: rgba(255,255,255,0.1);
                color: rgba(255,255,255,0.85);
                border-color: rgba(255,255,255,0.15);
            }
            .code-copy-btn:hover {
                background: rgba(255,255,255,0.2);
            }
        }

        @media (max-width: 600px) {
            .post-card-title { font-size: 18px; }
            .post-title { font-size: 24px; }
            .post-content { font-size: 15px; }
            .site-logo { font-size: 24px; }
            .post-card-feature img { height: 180px; }
            .back-to-top { right: 16px; bottom: 16px; width: 40px; height: 40px; line-height: 40px; }
            .page-title { font-size: 20px; margin-bottom: 16px; }
            .archive-year-title { font-size: 17px; }
            .archive-item { padding: 6px 0; }
            .archive-date { font-size: 12px; }
            .archive-title { font-size: 14px; }
        }
        """.trimIndent()
    }
}
