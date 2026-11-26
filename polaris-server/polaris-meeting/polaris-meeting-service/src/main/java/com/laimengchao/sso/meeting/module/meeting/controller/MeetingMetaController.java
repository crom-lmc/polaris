package com.laimengchao.sso.meeting.module.meeting.controller;

import com.laimengchao.sso.common.R;
import com.laimengchao.sso.common.security.PermissionCodes;
import com.laimengchao.sso.meeting.module.meeting.dto.MeetingVOs;
import com.laimengchao.sso.meeting.module.meeting.service.BookingService;
import com.laimengchao.sso.meeting.module.meeting.service.MeetingSupportService;
import com.laimengchao.sso.meeting.security.Permit;
import com.laimengchao.sso.utils.AccountContext;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/** 当前人、字典、埋点配置、员工搜索、概览与时间轴等页面基础数据。 */
@RestController
@RequestMapping("/api/meetingroom")
@RequiredArgsConstructor
public class MeetingMetaController {

    private final MeetingSupportService support;
    private final BookingService bookingService;

    @GetMapping("/me")
    @Permit({PermissionCodes.MEETING_OVERVIEW_VIEW, PermissionCodes.MEETING_ROOM_LIST,
            PermissionCodes.MEETING_BOOKING_VIEW, PermissionCodes.MEETING_BOOKING_LIST})
    public R<MeetingVOs.MeVO> me() {
        MeetingVOs.MeVO vo = new MeetingVOs.MeVO();
        vo.setAccountId(String.valueOf(support.requireAccountId()));
        vo.setUsername(AccountContext.getUsername());
        vo.setName(AccountContext.getUsername());
        vo.setCity(support.currentCity());
        vo.setSuperAdmin(AccountContext.isSuperAdmin());
        vo.setAppCode(AccountContext.getAppCode());
        vo.setPermissions(java.util.List.copyOf(AccountContext.getPermissions()));
        return R.ok(vo);
    }

    @GetMapping("/config/booking")
    @Permit({PermissionCodes.MEETING_BOOKING_VIEW, PermissionCodes.MEETING_BOOKING_CREATE})
    public R<MeetingVOs.BookingConfigVO> bookingConfig() {
        MeetingVOs.BookingConfigVO vo = new MeetingVOs.BookingConfigVO();
        vo.setMaxDays(support.maxAdvanceDays());
        vo.setGraceMinutes(support.noShowGraceMinutes());
        vo.setMaxMinutes(support.maxBookingMinutes());
        MeetingSupportService.NoShowRule rule = support.noShowRule();
        vo.setNoShowWindowDays(rule.windowDays());
        vo.setNoShowThreshold(rule.threshold());
        vo.setNoShowBanDays(rule.banDays());
        MeetingVOs.NoShowStatusVO status = bookingService.noShowStatus(support.requireAccountId());
        vo.setNoShowCount(status.getCount());
        vo.setBannedUntil(status.getBannedUntil());
        return R.ok(vo);
    }

    @GetMapping("/dicts")
    @Permit({PermissionCodes.MEETING_OVERVIEW_VIEW, PermissionCodes.MEETING_ROOM_LIST,
            PermissionCodes.MEETING_BOOKING_VIEW, PermissionCodes.MEETING_BOOKING_LIST})
    public R<Map<String, List<MeetingVOs.DictItemVO>>> dicts() {
        return R.ok(support.dicts());
    }

    @GetMapping("/employees/search")
    @Permit(PermissionCodes.MEETING_BOOKING_CREATE)
    public R<java.util.List<MeetingVOs.EmployeeVO>> employees(
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "limit", defaultValue = "50") int limit) {
        return R.ok(support.searchEmployees(keyword, Math.min(Math.max(limit, 1), 100)));
    }

    @GetMapping("/employees/names")
    @Permit({PermissionCodes.MEETING_BOOKING_VIEW, PermissionCodes.MEETING_BOOKING_LIST,
            PermissionCodes.MEETING_BOOKING_CREATE})
    public R<Map<String, String>> employeeNames(@RequestParam List<Long> ids) {
        return R.ok(support.employeeNames(ids));
    }

    @GetMapping("/overview")
    @Permit(PermissionCodes.MEETING_OVERVIEW_VIEW)
    public R<MeetingVOs.OverviewVO> overview(
            @RequestParam(value = "date", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(value = "month", required = false) String month,
            @RequestParam(value = "city", required = false) String city) {
        return R.ok(bookingService.overview(date, month, city));
    }

    @GetMapping("/schedule")
    @Permit(PermissionCodes.MEETING_BOOKING_VIEW)
    public R<MeetingVOs.ScheduleVO> schedule(
            @RequestParam(value = "date", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(value = "city", required = false) String city,
            @RequestParam(value = "roomId", required = false) Long roomId) {
        return R.ok(bookingService.schedule(date, city, roomId));
    }
}
