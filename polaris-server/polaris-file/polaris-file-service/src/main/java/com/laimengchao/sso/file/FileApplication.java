package com.laimengchao.sso.file;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 统一文件服务：CMS/EHR/forum/openapi 的文件存取统一收口。
 * <p>
 * 身份来源分两条：管理端由 sso-gateway 验签注入身份头，服务间由 X-Internal-Token 准入，
 * 公开文件的签名直链由服务端下发的短时效令牌校验，均不在本服务自签 JWT。
 */
@MapperScan("com.laimengchao.sso.file.module.**.mapper")
@SpringBootApplication(scanBasePackages = {"com.laimengchao.sso.file", "com.laimengchao.sso.common"})
public class FileApplication {

    public static void main(String[] args) {
        SpringApplication.run(FileApplication.class, args);
    }
}
