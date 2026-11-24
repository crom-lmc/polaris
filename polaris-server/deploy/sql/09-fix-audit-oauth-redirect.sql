-- ============================================================================
-- 修复统一审计中心单点登录：redirect_uri 指向专用回调页（幂等，可重复执行）
-- ============================================================================
-- 背景：审计服务是最后一个新建的应用，当时复用 sso-web 直连登录脚手架，
--       没接入标准 OAuth 单点。sso_application 里 audit 的 redirect_uri 配成了
--       根路径 "/"，导致从「我的应用」点进来时授权码回跳到首页，前端路由守卫
--       bootstrap 失败后又跳 /login?redirect=，code 被带到登录页却无人消费，
--       表现为「已登录但单点不成功，停在登录页」。
--       其他业务应用（file/cms/ehr/settings/...）的 redirect_uri 都是 /callback。
--
-- 配套代码改动（本轮已改，需一并部署）：
--   - audit-server: 新增 /api/audit/sso/oauth/exchange 换票端点 + sso-client 依赖
--   - sso-gateway:  白名单放行 /api/audit/sso/**
--   - audit-web:    新增 /callback 页消费授权码；守卫未登录改跳 SSO /authorize
--   - appSecret:    由容器环境变量 SSO_AUDIT_APP_SECRET 注入（值取本表 app_secret）
--
-- 幂等：WHERE 带旧值精确匹配，重复执行第二次影响 0 行。
-- ============================================================================

UPDATE sso_ms.sso_application
SET redirect_uri = 'https://polaris-audit.laimengchao.site/callback',
    updated_at = NOW()
WHERE code = 'audit'
  AND redirect_uri = 'https://polaris-audit.laimengchao.site/';
