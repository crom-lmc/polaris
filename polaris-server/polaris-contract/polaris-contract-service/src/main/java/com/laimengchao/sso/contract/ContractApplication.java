package com.laimengchao.sso.contract;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/** 合同服务：管理端 /api/contract/** 走网关注入身份头 + @Permit；服务间 /internal/** 走内部令牌。 */
@MapperScan("com.laimengchao.sso.contract.module.**.mapper")
@SpringBootApplication(scanBasePackages = {"com.laimengchao.sso.contract", "com.laimengchao.sso.common"})
public class ContractApplication {

    public static void main(String[] args) {
        SpringApplication.run(ContractApplication.class, args);
    }
}
