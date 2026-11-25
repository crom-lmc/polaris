package com.laimengchao.sso.meeting;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/** 会议室服务：管理端 /api/meetingroom/** 走网关注入身份头 + @Permit；服务间 /internal/** 走内部令牌。 */
@MapperScan("com.laimengchao.sso.meeting.module.**.mapper")
@SpringBootApplication(scanBasePackages = {"com.laimengchao.sso.meeting", "com.laimengchao.sso.common"})
public class MeetingApplication {

    public static void main(String[] args) {
        SpringApplication.run(MeetingApplication.class, args);
    }
}
