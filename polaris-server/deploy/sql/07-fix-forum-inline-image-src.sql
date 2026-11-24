-- ============================================================================
-- 修复历史评论插图 src 丢失（幂等，可重复执行）
-- ============================================================================
-- 背景：论坛正文/评论此前用 Safelist.basicWithImages() 清洗，它对 img[src]
--       强制 http/https 协议白名单，preserveRelativeLinks(true) 只阻止把
--       相对地址绝对化、并不会放行相对路径，于是统一文件服务的插图地址
--       /api/forum/media/file/{fileId}.{ext} 被剥离，库里只剩 <img alt="...">，
--       前端渲染成破图。后端已改为手动 Safelist + URL 白名单校验，本脚本
--       修复已经被写坏的存量数据。
--
-- 幂等：WHERE 带旧内容精确匹配，重复执行第二次影响 0 行。
-- 对应附件：forum.forum_attachment id=2124924220070625282
--           usage_type=INLINE, file_id=383314035510218752
-- ============================================================================

UPDATE forum.forum_comment
SET content = '<p><img src="/api/forum/media/file/383314035510218752.jpg" alt="gh_5d92b3fd27e5_1280 (1).jpg"></p><p>111</p>'
WHERE id = 2124924291868721153
  AND content = '<p><img alt="gh_5d92b3fd27e5_1280 (1).jpg"></p><p>111</p>';
