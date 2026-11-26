package com.laimengchao.sso.meeting.module.meeting.controller;

import com.laimengchao.sso.common.R;
import com.laimengchao.sso.common.security.PermissionCodes;
import com.laimengchao.sso.meeting.module.meeting.dto.MeetingVOs;
import com.laimengchao.sso.meeting.module.meeting.service.MeetingStatsService;
import com.laimengchao.sso.meeting.security.Permit;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

/** 爽约记录与会议室使用率：全部爽约/使用率由独立权限码限制，前端隐藏不能代替服务端鉴权。 */
@RestController
@RequestMapping("/api/meetingroom/stats")
@RequiredArgsConstructor
public class MeetingStatsController {

    private final MeetingStatsService statsService;

    /** 我的爽约：服务端强制按当前账号过滤，忽略前端传来的 accountId */
    @GetMapping("/no-shows/mine")
    @Permit(PermissionCodes.MEETING_NOSHOW_VIEW)
    public R<R.Page<MeetingVOs.NoShowVO>> myNoShows(
            @RequestParam(value = "from", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(value = "to", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(value = "city", required = false) String city,
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "size", defaultValue = "5") int size) {
        return R.ok(statsService.noShows(false, from, to, city, page, size));
    }

    /** 全部爽约：仅会议室管理员（meeting:noshow:all） */
    @GetMapping("/no-shows/all")
    @Permit(PermissionCodes.MEETING_NOSHOW_ALL)
    public R<R.Page<MeetingVOs.NoShowVO>> allNoShows(
            @RequestParam(value = "from", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(value = "to", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(value = "city", required = false) String city,
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "size", defaultValue = "5") int size) {
        return R.ok(statsService.noShows(true, from, to, city, page, size));
    }

    /** 会议室使用率：仅会议室管理员（meeting:usage:view） */
    @GetMapping("/usage")
    @Permit(PermissionCodes.MEETING_USAGE_VIEW)
    public R<MeetingVOs.UsageVO> usage(
            @RequestParam(value = "from", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(value = "to", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(value = "city", required = false) String city) {
        return R.ok(statsService.usage(from, to, city));
    }
}
