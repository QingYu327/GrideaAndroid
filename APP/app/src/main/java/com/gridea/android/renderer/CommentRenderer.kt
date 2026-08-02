package com.gridea.android.renderer

import com.gridea.android.data.model.CommentPlatform
import com.gridea.android.data.model.CommentSetting

/**
 * 评论渲染器：根据 [CommentSetting] 生成各评论系统
 * （Gitalk / Giscus / Disqus / Valine / Twikoo / Waline）的 HTML 片段。
 */
object CommentRenderer {

    fun renderCommentHtml(
        commentSetting: CommentSetting?,
        post: PostRenderData,
        domain: String,
        isPreview: Boolean = false
    ): String = renderComment(commentSetting, post, domain, isPreview)

    /**
     * 渲染评论区
     * 对应旧版 commentSetting 在 post.ejs 中的渲染
     * @param domain 站点域名（评论系统需要绝对 URL）
     * @param isPreview 是否为预览模式。预览模式下不注入任何评论系统 CDN 脚本，只返回占位 div。
     *                  原因：评论系统 CDN（Gitalk/Giscus/Disqus/Valine/Twikoo/Waline）是同步 `<script>` 标签，
     *                  会阻塞主线程直到下载+执行完毕，预览时实测拖慢 onPageFinished 9-10 秒。
     *                  预览只验证布局，发布时由部署平台加载评论。
     */
    private fun renderComment(
        commentSetting: CommentSetting?,
        post: PostRenderData,
        domain: String,
        isPreview: Boolean = false
    ): String {
        if (commentSetting == null || !commentSetting.showComment) return ""

        // 预览模式：返回占位 div，不注入任何 CDN 脚本
        if (isPreview) {
            val platformName = when (commentSetting.commentPlatform) {
                CommentPlatform.GITALK -> "Gitalk"
                CommentPlatform.GISCUS -> "Giscus"
                CommentPlatform.DISQUS -> "Disqus"
                CommentPlatform.VALINE -> "Valine"
                CommentPlatform.TWIKOO -> "Twikoo"
                CommentPlatform.WALINE -> "Waline"
                else -> "评论"
            }
            return """
            <div class="comment-section comment-preview-placeholder">
                <p class="comment-placeholder-text">评论区（$platformName）— 预览模式不加载评论系统</p>
            </div>
            """.trimIndent()
        }

        // 评论系统需要绝对 URL
        val postAbsoluteUrl = if (domain.isNotEmpty()) "${domain}/${post.link}" else post.link

        return when (commentSetting.commentPlatform) {
            CommentPlatform.GITALK -> {
                val g = commentSetting.gitalkSetting
                // redirectURI：当 domain 非空时用站点域名拼接，确保 OAuth 回调地址与 GitHub App 注册一致
                // id：Gitalk 用 GitHub Issue label 存储，有 50 字符限制，超长截断避免创建 issue 失败
                val redirectUri = if (domain.isNotEmpty()) domain else ""
                val gitalkId = post.fileName.take(50)
                """
                <div id="gitalk-container" class="comment-section"></div>
                <link rel="stylesheet" href="https://unpkg.com/gitalk/dist/gitalk.css">
                <script src="https://unpkg.com/gitalk/dist/gitalk.min.js"></script>
                <script>
                var gitalk = new Gitalk({
                    clientID: '${g.clientId}',
                    clientSecret: '${g.clientSecret}',
                    repo: '${g.repository}',
                    owner: '${g.owner}',
                    admin: ['${g.owner}'],
                    id: '${gitalkId}',
                    distractionFreeMode: false${if (redirectUri.isNotEmpty()) ",\n                    redirectURI: '$redirectUri'" else ""}
                });
                gitalk.render('gitalk-container');
                </script>
                """.trimIndent()
            }
            CommentPlatform.GISCUS -> {
                val gi = commentSetting.giscusSetting
                """
                <div class="comment-section">
                <script src="https://giscus.app/client.js"
                    data-repo="${gi.repo}"
                    data-repo-id="${gi.repoId}"
                    data-category="${gi.category}"
                    data-category-id="${gi.categoryId}"
                    data-mapping="${gi.mapping}"
                    data-theme="${gi.theme}"
                    crossorigin="anonymous"
                    async>
                </script>
                </div>
                """.trimIndent()
            }
            CommentPlatform.DISQUS -> {
                val d = commentSetting.disqusSetting
                """
                <div id="disqus_thread" class="comment-section"></div>
                <script>
                var disqus_config = function() {
                    this.page.url = '$postAbsoluteUrl';
                    this.page.identifier = '${post.fileName}';
                };
                (function() {
                    var d = document, s = d.createElement('script');
                    s.src = 'https://${d.shortname}.disqus.com/embed.js';
                    s.setAttribute('data-timestamp', +new Date());
                    (d.head || d.body).appendChild(s);
                })();
                </script>
                """.trimIndent()
            }
            CommentPlatform.VALINE -> {
                val v = commentSetting.valineSetting
                """
                <div id="vcomments" class="comment-section"></div>
                <script src="https://unpkg.com/valine/dist/Valine.min.js"></script>
                <script>
                new Valine({
                    el: '#vcomments',
                    appId: '${v.appId}',
                    appKey: '${v.appKey}',
                    path: window.location.pathname
                });
                </script>
                """.trimIndent()
            }
            CommentPlatform.TWIKOO -> {
                val t = commentSetting.twikooSetting
                """
                <div id="tcomment" class="comment-section"></div>
                <script src="https://cdn.jsdelivr.net/npm/twikoo/dist/twikoo.min.js"></script>
                <script>
                twikoo.init({
                    envId: '${t.envId}',
                    el: '#tcomment'
                });
                </script>
                """.trimIndent()
            }
            CommentPlatform.WALINE -> {
                val w = commentSetting.walineSetting
                """
                <div id="waline" class="comment-section"></div>
                <script type="module">
                import { init } from 'https://unpkg.com/@waline/client@v3/dist/waline.js';
                import '@waline/client@v3/dist/waline.css';
                init({
                    el: '#waline',
                    serverURL: '${w.serverURL}',
                    path: window.location.pathname
                });
                </script>
                """.trimIndent()
            }
            else -> ""
        }
    }
}
