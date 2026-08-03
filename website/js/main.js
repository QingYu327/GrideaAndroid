/* ============================================================
   Gridea Android — 官网共享脚本
   主题切换 / 移动菜单 / 滚动渐入 / 页面切换 / 波纹 / 懒加载
   ============================================================ */
(function () {
    'use strict';

    var STORAGE_KEY = 'gridea-theme';

    /* ---------- 主题初始化（尽早执行，避免闪烁） ---------- */
    function getPreferredTheme() {
        var saved = localStorage.getItem(STORAGE_KEY);
        if (saved === 'dark' || saved === 'light') return saved;
        return window.matchMedia('(prefers-color-scheme: dark)').matches ? 'dark' : 'light';
    }
    function applyTheme(theme) {
        document.documentElement.setAttribute('data-theme', theme);
        var btn = document.querySelector('.theme-toggle');
        if (btn) btn.setAttribute('aria-label', theme === 'dark' ? '切换到亮色' : '切换到暗色');
    }
    applyTheme(getPreferredTheme());

    window.toggleTheme = function () {
        var current = document.documentElement.getAttribute('data-theme') === 'dark' ? 'dark' : 'light';
        var next = current === 'dark' ? 'light' : 'dark';
        applyTheme(next);
        localStorage.setItem(STORAGE_KEY, next);
    };

    /* ---------- 跟随系统变化（仅在用户未手动选择时） ---------- */
    try {
        window.matchMedia('(prefers-color-scheme: dark)').addEventListener('change', function (e) {
            if (!localStorage.getItem(STORAGE_KEY)) applyTheme(e.matches ? 'dark' : 'light');
        });
    } catch (err) { /* 老浏览器忽略 */ }

    function ready(fn) {
        if (document.readyState !== 'loading') fn();
        else document.addEventListener('DOMContentLoaded', fn);
    }

    ready(function () {
        var body = document.body;

        /* ---------- FOUC 防护：外部 CSS 加载完成后淡入 body ---------- */
        // 配合 <link rel="preload" as="style" onload> 使用：
        // preload 的 onload 把 rel 切回 stylesheet 后，浏览器才会应用该样式。
        // 这里轮询检测样式表是否已应用（document.styleSheets 包含目标文件），
        // 应用后给 body 加 css-ready 类触发淡入，超时 3s 强制显示兜底。
        function markCssReady() { body.classList.add('css-ready'); }
        function checkCssLoaded() {
            var sheets = document.styleSheets;
            for (var i = 0; i < sheets.length; i++) {
                var href = sheets[i].href || '';
                if (href.indexOf('style.css') !== -1) {
                    try { sheets[i].cssRules; /* 触发跨域检查 */ return true; }
                    catch (e) { return true; }
                }
            }
            return false;
        }
        if (checkCssLoaded()) {
            markCssReady();
        } else {
            var cssTimer = setInterval(function () {
                if (checkCssLoaded()) { clearInterval(cssTimer); markCssReady(); }
            }, 30);
            setTimeout(function () { clearInterval(cssTimer); markCssReady(); }, 3000);
        }

        /* ---------- 页面进入动画 ---------- */
        if (body.classList.contains('page-transition')) {
            body.classList.add('is-entering');
            requestAnimationFrame(function () {
                requestAnimationFrame(function () { body.classList.remove('is-entering'); });
            });
        }

        /* ---------- 主题切换按钮 ---------- */
        var themeBtn = document.querySelector('.theme-toggle');
        if (themeBtn) themeBtn.addEventListener('click', window.toggleTheme);

        /* ---------- Navbar 滚动样式 + 顶部滚动进度条 ---------- */
        var navbar = document.querySelector('.navbar');
        var progressBar = document.createElement('div');
        progressBar.className = 'scroll-progress';
        document.body.appendChild(progressBar);

        var onScroll = function () {
            if (navbar) {
                if (window.scrollY > 8) navbar.classList.add('scrolled');
                else navbar.classList.remove('scrolled');
            }
            var scrollTop = window.scrollY;
            var docHeight = document.documentElement.scrollHeight - window.innerHeight;
            var pct = docHeight > 0 ? (scrollTop / docHeight) * 100 : 0;
            progressBar.style.width = pct + '%';
        };
        onScroll();
        window.addEventListener('scroll', onScroll, { passive: true });
        window.addEventListener('resize', onScroll, { passive: true });

        /* ---------- 移动端菜单 ---------- */
        var menuToggle = document.querySelector('.menu-toggle');
        var navLinks = document.querySelector('.navbar-links');
        if (menuToggle && navLinks) {
            menuToggle.addEventListener('click', function () {
                var open = navLinks.classList.toggle('open');
                menuToggle.classList.toggle('open', open);
                menuToggle.setAttribute('aria-expanded', open ? 'true' : 'false');
            });
            navLinks.querySelectorAll('a').forEach(function (a) {
                a.addEventListener('click', function () {
                    navLinks.classList.remove('open');
                    menuToggle.classList.remove('open');
                    menuToggle.setAttribute('aria-expanded', 'false');
                });
            });
            document.addEventListener('click', function (e) {
                if (!navLinks.contains(e.target) && !menuToggle.contains(e.target)) {
                    navLinks.classList.remove('open');
                    menuToggle.classList.remove('open');
                }
            });
        }

        /* ---------- 滚动渐入（IntersectionObserver + 错峰） ---------- */
        var revealEls = document.querySelectorAll('.reveal');
        if ('IntersectionObserver' in window && revealEls.length) {
            var io = new IntersectionObserver(function (entries) {
                entries.forEach(function (entry) {
                    if (entry.isIntersecting) {
                        var el = entry.target;
                        var delay = el.dataset.delay || 0;
                        setTimeout(function () { el.classList.add('is-visible'); }, delay);
                        io.unobserve(el);
                    }
                });
            }, { threshold: 0.12, rootMargin: '0px 0px -40px 0px' });
            revealEls.forEach(function (el, i) {
                // 同一容器内的元素自动错峰
                var sibs = el.parentElement ? Array.prototype.indexOf.call(el.parentElement.children, el) : 0;
                el.dataset.delay = (sibs % 4) * 80;
                io.observe(el);
            });
        } else {
            revealEls.forEach(function (el) { el.classList.add('is-visible'); });
        }

        /* ---------- 波纹效果 ---------- */
        function addRipple(e) {
            var el = e.currentTarget;
            var rect = el.getBoundingClientRect();
            var size = Math.max(rect.width, rect.height);
            var x = (e.clientX || rect.left + rect.width / 2) - rect.left - size / 2;
            var y = (e.clientY || rect.top + rect.height / 2) - rect.top - size / 2;
            var span = document.createElement('span');
            span.className = 'ripple';
            span.style.width = span.style.height = size + 'px';
            span.style.left = x + 'px';
            span.style.top = y + 'px';
            el.appendChild(span);
            setTimeout(function () { span.remove(); }, 650);
        }
        document.querySelectorAll('.btn, .feature-card, .theme-card').forEach(function (el) {
            el.addEventListener('click', addRipple);
        });

        /* ---------- 平滑滚动（锚点） ---------- */
        document.querySelectorAll('a[href^="#"]').forEach(function (a) {
            a.addEventListener('click', function (e) {
                var id = a.getAttribute('href');
                if (id.length < 2) return;
                var target = document.querySelector(id);
                if (target) {
                    e.preventDefault();
                    target.scrollIntoView({ behavior: 'smooth', block: 'start' });
                    history.replaceState(null, '', id);
                }
            });
        });

        /* ---------- 主题卡片 iframe 懒加载 ---------- */
        var frames = document.querySelectorAll('iframe[data-src]');
        if ('IntersectionObserver' in window && frames.length) {
            var fio = new IntersectionObserver(function (entries) {
                entries.forEach(function (entry) {
                    if (entry.isIntersecting) {
                        var f = entry.target;
                        f.src = f.dataset.src;
                        f.removeAttribute('data-src');
                        fio.unobserve(f);
                    }
                });
            }, { rootMargin: '200px 0px' });
            frames.forEach(function (f) { fio.observe(f); });
        } else {
            frames.forEach(function (f) { f.src = f.dataset.src; f.removeAttribute('data-src'); });
        }

        /* ---------- 页面切换动画：拦截内部链接 ---------- */
        function isInternal(a) {
            if (!a) return false;
            if (a.target === '_blank' || a.hasAttribute('download')) return false;
            if (a.host && a.host !== window.location.host) return false;
            var href = a.getAttribute('href');
            if (!href || href.length < 2) return false;
            if (href.startsWith('#')) return false;
            if (href.startsWith('mailto:') || href.startsWith('tel:') || href.startsWith('javascript:')) return false;
            // 仅拦截本站 .html 链接与目录链接
            return /\.html($|[?#])/i.test(href) || /\/$/.test(href) || /^(\.?\/)?(index|themes|docs)/i.test(href);
        }
        document.addEventListener('click', function (e) {
            if (e.defaultPrevented || e.metaKey || e.ctrlKey || e.shiftKey || e.altKey) return;
            if (e.button !== 0) return;
            var a = e.target.closest('a');
            if (!a || !isInternal(a)) return;
            // 已是当前页面则不动画
            var url = a.href;
            if (url === window.location.href || url === window.location.href.split('#')[0]) return;
            if (!body.classList.contains('page-transition')) return;
            e.preventDefault();
            body.classList.add('is-leaving');
            window.setTimeout(function () { window.location.href = url; }, 380);
        });

        /* ---------- 文档侧边栏 scrollspy（自动滚动定位） ---------- */
        var headings = document.querySelectorAll('.docs-content h2[id], .docs-content h3[id]');
        var sideLinks = document.querySelectorAll('.docs-sidebar a[href^="#"]');
        // 滚动容器：桌面端是 .docs-sidebar，移动端抽屉打开时是 .docs-sidebar-body
        var sidebarEl = document.querySelector('.docs-sidebar');
        var sidebarBodyEl = document.querySelector('.docs-sidebar-body');

        function getScrollContainer() {
            // 优先返回实际可滚动的容器
            if (sidebarBodyEl && sidebarBodyEl.scrollHeight > sidebarBodyEl.clientHeight) {
                return sidebarBodyEl;
            }
            return sidebarEl;
        }

        // 将 active 链接滚动到侧边栏可视区域
        function scrollLinkIntoSidebar(link) {
            var container = getScrollContainer();
            if (!container || !link) return;
            var linkRect = link.getBoundingClientRect();
            var containerRect = container.getBoundingClientRect();
            // 链接已在容器可视区域内，无需滚动
            if (linkRect.top >= containerRect.top && linkRect.bottom <= containerRect.bottom) return;
            // 计算目标 scrollTop：让 active 链接位于容器上 1/3 处
            var offset = linkRect.top - containerRect.top + container.scrollTop;
            offset -= container.clientHeight / 3;
            container.scrollTo({ top: Math.max(0, offset), behavior: 'smooth' });
        }

        if (headings.length && sideLinks.length && 'IntersectionObserver' in window) {
            var spy = new IntersectionObserver(function (entries) {
                entries.forEach(function (entry) {
                    if (entry.isIntersecting) {
                        var id = entry.target.id;
                        sideLinks.forEach(function (l) {
                            var isActive = l.getAttribute('href') === '#' + id;
                            l.classList.toggle('active', isActive);
                            if (isActive) scrollLinkIntoSidebar(l);
                        });
                    }
                });
            }, { rootMargin: '-30% 0px -60% 0px' });
            headings.forEach(function (h) { spy.observe(h); });
        }

        /* ---------- 浏览器前进/后退恢复时重置离开态 ---------- */
        window.addEventListener('pageshow', function (e) {
            if (e.persisted) body.classList.remove('is-leaving', 'is-entering');
        });
    });
})();
