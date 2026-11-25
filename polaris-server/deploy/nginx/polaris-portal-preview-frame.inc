# CMS 后台「门户设置」通过 iframe 内嵌本站 /portal/ 做实时预览。
# 全局 X-Frame-Options: DENY 与 CSP frame-ancestors 'none' 会让浏览器拒绝渲染。
# 注意：location 内 add_header 会覆盖 http 级全部 add_header，必须在此重声明全部安全头。
add_header X-Frame-Options "SAMEORIGIN" always;
add_header X-Content-Type-Options "nosniff" always;
add_header Referrer-Policy "strict-origin-when-cross-origin" always;
add_header Content-Security-Policy "object-src 'none'; base-uri 'self'; frame-ancestors 'self'" always;
add_header Strict-Transport-Security "max-age=31536000" always;
