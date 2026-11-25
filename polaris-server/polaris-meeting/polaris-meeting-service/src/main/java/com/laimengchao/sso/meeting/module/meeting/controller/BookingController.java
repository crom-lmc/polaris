package com.laimengchao.sso.meeting.module.meeting.controller;

import com.laimengchao.sso.common.R;
import com.laimengchao.sso.common.security.PermissionCodes;
import com.laimengchao.sso.meeting.module.meeting.dto.MeetingRequests;
import com.laimengchao.sso.meeting.module.meeting.entity.MeetingBooking;
import com.laimengchao.sso.meeting.module.meeting.service.BookingService;
import com.laimengchao.sso.meeting.security.Permit;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.Map;

/** 预定与我的预定：列表数据范围由服务端按创建人强制过滤。 */
@RestController
@RequestMapping("/api/meetingroom/bookings")
@RequiredArgsConstructor
public class BookingController {

    private final BookingService bookingService;

    @GetMapping
    @Permit(PermissionCodes.MEETING_BOOKING_LIST)
    public R<R.Page<MeetingBooking>> list(
            @RequestParam(value = "tab", required = false) String tab,
            @RequestParam(value = "city", required = false) String city,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "from", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(value = "to", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "size", defaultValue = "20") int size) {
        return R.ok(bookingService.myBookings(tab, city, status, keyword, from, to, page, size));
    }

    @GetMapping("/counts")
    @Permit(PermissionCodes.MEETING_BOOKING_LIST)
    public R<Map<String, Long>> counts(
            @RequestParam(value = "city", required = false) String city,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "from", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(value = "to", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return R.ok(bookingService.myBookingCounts(city, status, keyword, from, to));
    }

    /** 被邀请人凭消息链接看详情仍需基础查看权限（booking:list 或 booking:view） */
    @GetMapping("/{id}")
    @Permit({PermissionCodes.MEETING_BOOKING_LIST, PermissionCodes.MEETING_BOOKING_VIEW})
    public R<MeetingBooking> detail(@PathVariable("id") Long id) {
        return R.ok(bookingService.detail(id));
    }

    @PostMapping
    @Permit(PermissionCodes.MEETING_BOOKING_CREATE)
    public R<MeetingBooking> create(@RequestBody MeetingRequests.BookingSave req) {
        return R.ok(bookingService.create(req));
    }

    @PutMapping("/{id}")
    @Permit(PermissionCodes.MEETING_BOOKING_CREATE)
    public R<Void> update(@PathVariable("id") Long id, @RequestBody MeetingRequests.BookingSave req) {
        bookingService.update(id, req);
        return R.ok();
    }

    @PostMapping("/{id}/checkin")
    @Permit(PermissionCodes.MEETING_BOOKING_CHECKIN)
    public R<Void> checkin(@PathVariable("id") Long id) {
        bookingService.checkin(id);
        return R.ok();
    }

    @PostMapping("/{id}/extend")
    @Permit(PermissionCodes.MEETING_BOOKING_EXTEND)
    public R<Void> extend(@PathVariable("id") Long id, @RequestBody MeetingRequests.ActionRequest req) {
        bookingService.extend(id, req);
        return R.ok();
    }

    @PostMapping("/{id}/cancel")
    @Permit(PermissionCodes.MEETING_BOOKING_CANCEL)
    public R<Void> cancel(@PathVariable("id") Long id) {
        bookingService.cancel(id);
        return R.ok();
    }

    @PostMapping("/{id}/release")
    @Permit(PermissionCodes.MEETING_BOOKING_RELEASE)
    public R<Void> release(@PathVariable("id") Long id) {
        bookingService.release(id);
        return R.ok();
    }
}
