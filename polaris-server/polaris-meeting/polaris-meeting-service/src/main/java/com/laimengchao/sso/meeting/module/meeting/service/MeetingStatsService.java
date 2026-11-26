package com.laimengchao.sso.meeting.module.meeting.service;

import com.laimengchao.sso.common.R;
import com.laimengchao.sso.meeting.module.meeting.dto.MeetingVOs;

import java.time.LocalDate;

public interface MeetingStatsService {

    /** 爽约记录：mine 强制按当前账号过滤；all 由 Controller 的管理员权限码保证 */
    R.Page<MeetingVOs.NoShowVO> noShows(boolean all, LocalDate from, LocalDate to, String city, int page, int size);

    /** 分地区、按会议室统计 9:00–18:00 使用率；重叠预约按分钟累加，故理论上可超过 100% */
    MeetingVOs.UsageVO usage(LocalDate from, LocalDate to, String city);
}
