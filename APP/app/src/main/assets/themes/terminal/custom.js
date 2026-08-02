/* ============================================================
   08-Terminal custom.js
   非文章页的阅读进度条、回到顶部、图片放大
   文章页由 post.peb 内联脚本处理（通过 data-gridea-inline 标记跳过）
   ============================================================ */
(function() {
    'use strict';

    // ===== 阅读进度条 + 回到顶部（非文章页） =====
    var backTopBtn = document.getElementById('back-to-top');
    var progressBar = document.getElementById('reading-progress');

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
            if (backTopBtn) {
                var shouldShow = scrollTop > 300;
                if (shouldShow !== backTopVisible) {
                    backTopVisible = shouldShow;
                    if (shouldShow) backTopBtn.classList.add('visible');
                    else backTopBtn.classList.remove('visible');
                }
            }
            if (progressBar) {
                var scrollRange = cachedScrollHeight - cachedClientHeight;
                var percent = scrollRange > 0 ? (scrollTop / scrollRange) : 0;
                var quantized = Math.round(percent * 200) / 200;
                if (quantized !== lastPercent) {
                    lastPercent = quantized;
                    progressBar.style.transform = 'scaleX(' + quantized + ')';
                }
            }
            ticking = false;
        }

        window.addEventListener('scroll', function() {
            if (!ticking) {
                window.requestAnimationFrame(onScrollUpdate);
                ticking = true;
            }
        }, { passive: true });

        var resizeTimer = null;
        window.addEventListener('resize', function() {
            if (resizeTimer) clearTimeout(resizeTimer);
            resizeTimer = setTimeout(function() {
                recalcScrollMetrics();
                if (!ticking) { window.requestAnimationFrame(onScrollUpdate); ticking = true; }
            }, 200);
        }, { passive: true });

        onScrollUpdate();

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
        document.querySelectorAll('.post-content img, .post-feature img').forEach(function(img) {
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
