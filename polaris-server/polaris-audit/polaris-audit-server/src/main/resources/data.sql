-- 审计中心无业务种子数据。数据迁移见 deploy/sql/06-audit-migration.sql。
-- Spring Boot 在 sql.init.mode=always 时拒绝空脚本，保留一条无副作用语句。
SELECT 1;
