package com.laimengchao.sso.ehr.module.attendance.service;

import com.laimengchao.sso.ehr.module.attendance.dto.WorkCalendarItem;
import com.laimengchao.sso.ehr.module.attendance.vo.WorkCalendarVO;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

public interface AttendanceCalendarService {

    List<WorkCalendarVO> month(String month);

    void save(List<WorkCalendarItem> items);

    /** 日类型：WORKDAY 工作日 / REST 休息日 / LEGAL_HOLIDAY 法定节假日。 */
    String resolveDayType(LocalDate date);

    /**
     * CSV 批量导入工作日历；首行可为表头。日期支持 yyyy-MM-dd，也支持 2026/10/1。
     * 是否工作日接受 1/0、是/否、工作日/休息、Y/N。
     *
     * @return 成功写入的行数
     */
    int importCsv(MultipartFile file);

    /**
     * 下一年工作日历状态：每年 11 月起，若下一年尚未更新则前端展示「更新下一年工作日历」按钮。
     */
    Map<String, Object> nextYearStatus();

    /**
     * 一键更新下一年工作日历：把该年度的法定节假日与调休上班日预设写入工作日历（不覆盖已有显式配置），
     * 记录年度更新标记，并关闭相关维护待办（按钮随之隐藏）。
     */
    Map<String, Object> applyNextYear(String operator);

    /**
     * 每日提醒：11 月起若下一年工作日历尚未更新，向指定接收人（或 HR 部门）发送维护待办。
     * 完成后调用方通过 bizHandled 关闭；同一年度同一天重复发送由 settings 侧按业务去重。
     */
    void remindNextYearCalendarIfNeeded();
}
