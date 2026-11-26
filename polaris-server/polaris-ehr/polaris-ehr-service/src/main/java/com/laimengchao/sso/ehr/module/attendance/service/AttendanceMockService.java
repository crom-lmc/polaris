package com.laimengchao.sso.ehr.module.attendance.service;

import java.time.LocalDate;

public interface AttendanceMockService {

    /**
     * 生成指定日期的模拟考勤；受配置中心埋点 ehr.attendance.mock.enabled 开关控制，
     * 关闭时抛出明确提示，避免生产环境误生成数据。
     *
     * @return 生成的打卡条数
     */
    int run(LocalDate date);
}
