package com.laimengchao.sso.finance;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/** 财务服务：管理端 /api/finance/** 走网关注入身份头 + @Permit；服务间 /internal/** 走内部令牌。 */
@MapperScan("com.laimengchao.sso.finance.module.**.mapper")
@SpringBootApplication(scanBasePackages = {"com.laimengchao.sso.finance", "com.laimengchao.sso.common"})
public class FinanceApplication {

    public static void main(String[] args) {
        SpringApplication.run(FinanceApplication.class, args);
    }
}
