package com.laimengchao.sso.audit;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 统一审计中心：网关 ACCESS、账号 LOGIN/SENSITIVE、EHR SENSITIVE 统一落 audit_event 单表。
 * <p>
 * 身份来源两条：管理端由 sso-gateway 验签注入身份头，服务间由 X-Internal-Token 准入；
 * 本服务不签发 JWT，也不直接查询账号库。
 */
@MapperScan("com.laimengchao.sso.audit.module.**.mapper")
@SpringBootApplication(scanBasePackages = {"com.laimengchao.sso.audit", "com.laimengchao.sso.common"})
@EnableScheduling
public class AuditApplication {

    public static void main(String[] args) {
        SpringApplication.run(AuditApplication.class, args);
    }
}
