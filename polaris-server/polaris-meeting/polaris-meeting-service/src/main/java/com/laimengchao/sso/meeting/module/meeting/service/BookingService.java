package com.laimengchao.sso.meeting.module.meeting.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.laimengchao.sso.audit.client.api.AuditApi;
import com.laimengchao.sso.audit.client.dto.AuditEventRequest;
import com.laimengchao.sso.common.BizException;
import com.laimengchao.sso.common.ErrorCode;
import com.laimengchao.sso.common.R;
import com.laimengchao.sso.meeting.common.MeetingErrorCode;
import com.laimengchao.sso.meeting.module.meeting.dto.MeetingRequests;
import com.laimengchao.sso.meeting.module.meeting.dto.MeetingVOs;
import com.laimengchao.sso.meeting.module.meeting.entity.MeetingBooking;
import com.laimengchao.sso.meeting.module.meeting.entity.MeetingBookingAttendee;
import com.laimengchao.sso.meeting.module.meeting.entity.MeetingRoom;
import com.laimengchao.sso.meeting.module.meeting.entity.MeetingRoomDevice;
import com.laimengchao.sso.meeting.module.meeting.mapper.MeetingBookingAttendeeMapper;
import com.laimengchao.sso.meeting.module.meeting.mapper.MeetingBookingMapper;
import com.laimengchao.sso.meeting.module.meeting.mapper.MeetingRoomDeviceMapper;
import com.laimengchao.sso.settings.client.api.NoticeApi;
import com.laimengchao.sso.settings.client.dto.NoticeBizHandledDTO;
import com.laimengchao.sso.settings.client.dto.NoticeSendDTO;
import com.laimengchao.sso.utils.AccountContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

/** 预定核心：冲突检测、时间规则、我的预定数据范围、邀请通知与调度释放。 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BookingService {

    public static final String PENDING = "PENDING";
    public static final String CHECKED_IN = "CHECKED_IN";
    public static final String COMPLETED = "COMPLETED";
    public static final String CANCELLED = "CANCELLED";
    public static final String RELEASED = "RELEASED";
    private static final List<String> OCCUPYING = List.of(PENDING, CHECKED_IN);
    private static final DateTimeFormatter SLOT = DateTimeFormatter.ofPattern("HH:mm");
    private static final DateTimeFormatter NOTICE_TIME = DateTimeFormatter.ofPattern("MM-dd HH:mm");

    private final MeetingBookingMapper bookingMapper;
    private final MeetingBookingAttendeeMapper attendeeMapper;
    private final com.laimengchao.sso.meeting.module.meeting.mapper.MeetingRoomMapper roomMapper;
    private final MeetingRoomDeviceMapper deviceMapper;
    private final RoomService roomService;
    private final MeetingSupportService support;
    private final NoticeApi noticeApi;
    private final AuditApi auditApi;

    @Value("${meeting.detail-base-url:http://localhost:5187}")
    private String detailBaseUrl;

    /** 时间轴：按房间 × 30 分钟格返回状态，过去/占用/不可预约由服务端判定 */
    public MeetingVOs.ScheduleVO schedule(LocalDate date, String city, Long roomId) {
        if (date == null) {
            date = LocalDate.now();
        }
        List<MeetingRoom> rooms = roomId == null
                ? roomService.roomsOfCity(city == null || city.isBlank() ? support.currentCity() : city)
                : List.of(roomService.require(roomId));
        if (rooms.isEmpty()) {
            MeetingVOs.ScheduleVO empty = new MeetingVOs.ScheduleVO();
            empty.setDate(date);
            empty.setRooms(List.of());
            return empty;
        }
        List<Long> roomIds = rooms.stream().map(MeetingRoom::getId).toList();
        LocalDateTime dayStart = date.atStartOfDay();
        LocalDateTime dayEnd = date.plusDays(1).atStartOfDay();
        List<MeetingBooking> bookings = bookingMapper.selectList(new LambdaQueryWrapper<MeetingBooking>()
                .in(MeetingBooking::getRoomId, roomIds)
                .in(MeetingBooking::getStatus, OCCUPYING)
                .lt(MeetingBooking::getStartTime, dayEnd)
                .gt(MeetingBooking::getEndTime, dayStart));
        Map<Long, List<MeetingBooking>> byRoom = new HashMap<>();
        Set<Long> creatorIds = new HashSet<>();
        for (MeetingBooking b : bookings) {
            byRoom.computeIfAbsent(b.getRoomId(), k -> new ArrayList<>()).add(b);
            if (b.getCreatorId() != null) {
                creatorIds.add(b.getCreatorId());
            }
        }
        Map<String, String> creatorNames = support.employeeNames(creatorIds);
        Long current = AccountContext.getAccountId();
        LocalDateTime now = LocalDateTime.now().withSecond(0).withNano(0);
        List<MeetingVOs.RoomScheduleVO> out = new ArrayList<>();
        for (MeetingRoom room : rooms) {
            MeetingVOs.RoomScheduleVO vo = new MeetingVOs.RoomScheduleVO();
            vo.setRoomId(String.valueOf(room.getId()));
            vo.setName(room.getName());
            vo.setStatus(room.getStatus());
            vo.setDate(date);
            vo.setMaxCapacity(room.getMaxCapacity());
            vo.setComfortCapacity(room.getComfortCapacity());
            // require(roomId) 分支未 decorate，这里补查设备，供时间轴会议室名下方展示
            List<MeetingRoomDevice> roomDevices = room.getDevices() != null ? room.getDevices()
                    : deviceMapper.selectList(new LambdaQueryWrapper<MeetingRoomDevice>()
                            .eq(MeetingRoomDevice::getRoomId, room.getId())
                            .orderByAsc(MeetingRoomDevice::getId));
            vo.setDevices(roomDevices.stream().map(MeetingRoomDevice::getDeviceCode).distinct().toList());
            List<MeetingVOs.SlotVO> slots = new ArrayList<>(48);
            List<MeetingBooking> roomBookings = byRoom.getOrDefault(room.getId(), List.of());
            for (int i = 0; i < 48; i++) {
                LocalDateTime start = dayStart.plusMinutes(30L * i);
                LocalDateTime end = start.plusMinutes(30);
                MeetingVOs.SlotVO slot = new MeetingVOs.SlotVO();
                slot.setStart(SLOT.format(start));
                // 最后一格结束于次日 00:00，按业务口径展示为 24:00（不算跨天）
                slot.setEnd(i == 47 ? "24:00" : SLOT.format(end));
                if (!RoomService.AVAILABLE.equals(room.getStatus())) {
                    slot.setState("DISABLED");
                } else if (date.isBefore(LocalDate.now())
                        || (date.isEqual(LocalDate.now()) && !start.isAfter(now))) {
                    slot.setState("PAST");
                } else {
                    MeetingBooking hit = roomBookings.stream()
                            .filter(b -> b.getStartTime().isBefore(end) && b.getEndTime().isAfter(start))
                            .findFirst().orElse(null);
                    if (hit == null) {
                        slot.setState("FREE");
                    } else {
                        slot.setState(current != null && current.equals(hit.getCreatorId()) ? "MINE" : "OTHERS");
                        slot.setTitle(hit.getTitle());
                        slot.setCreatorName(creatorNames.getOrDefault(String.valueOf(hit.getCreatorId()), "未知预约人"));
                        slot.setBookingId(String.valueOf(hit.getId()));
                    }
                }
                slots.add(slot);
            }
            vo.setSlots(slots);
            out.add(vo);
        }
        MeetingVOs.ScheduleVO result = new MeetingVOs.ScheduleVO();
        result.setDate(date);
        result.setRooms(out);
        return result;
    }

    /** 新建预定：所有校验在服务端重做一遍，前端着色只做体验 */
    @Transactional(rollbackFor = Exception.class)
    public MeetingBooking create(MeetingRequests.BookingSave req) {
        Long creator = support.requireAccountId();
        String city = support.currentCity();
        if (city.isBlank()) {
            throw new BizException(MeetingErrorCode.CITY_MISMATCH, "未获取到当前人员城市，无法预定");
        }
        MeetingRoom room = roomService.requireAvailable(req.getRoomId());
        if (room.getCity() == null) {
            roomService.decorate(room);
        }
        if (!city.equals(room.getCity())) {
            throw new BizException(MeetingErrorCode.CITY_MISMATCH);
        }
        assertNoShowAllowed(creator);
        validateTime(req.getStartTime(), req.getEndTime(), true);
        validateCapacity(req.getAttendeeCount(), room);
        assertNoConflict(room.getId(), req.getStartTime(), req.getEndTime(), null);

        MeetingBooking booking = new MeetingBooking();
        booking.setBookingNo(nextBookingNo());
        booking.setRoomId(room.getId());
        booking.setTitle(req.getTitle() == null || req.getTitle().isBlank()
                ? "会议" : req.getTitle().trim());
        booking.setStartTime(req.getStartTime());
        booking.setEndTime(req.getEndTime());
        booking.setAttendeeCount(req.getAttendeeCount());
        booking.setStatus(PENDING);
        booking.setCity(city);
        booking.setCreatorId(creator);
        booking.setOrgCode(support.orgCode());
        booking.setRemark(req.getRemark() == null ? "" : req.getRemark().trim());
        booking.setCreatedAt(LocalDateTime.now());
        bookingMapper.insert(booking);

        List<Long> invited = replaceAttendees(booking, req.getAttendeeIds(), creator);
        notifyInvited(booking, room, invited, creator);
        return booking;
    }

    /** 修改：仅本人且未开始；增删参会人时补发/关闭通知 */
    @Transactional(rollbackFor = Exception.class)
    public void update(Long id, MeetingRequests.BookingSave req) {
        Long account = support.requireAccountId();
        MeetingBooking booking = requireMine(id, account);
        if (!PENDING.equals(booking.getStatus())) {
            throw new BizException(MeetingErrorCode.BOOKING_STATUS_INVALID, "仅待开始的预定可以修改");
        }
        String city = support.currentCity();
        MeetingRoom room = roomService.requireAvailable(req.getRoomId());
        if (room.getCity() == null) {
            roomService.decorate(room);
        }
        if (city.isBlank() || !city.equals(room.getCity())) {
            throw new BizException(MeetingErrorCode.CITY_MISMATCH);
        }
        assertNoShowAllowed(account);
        validateTime(req.getStartTime(), req.getEndTime(), true);
        validateCapacity(req.getAttendeeCount(), room);
        assertNoConflict(room.getId(), req.getStartTime(), req.getEndTime(), booking.getId());

        List<Long> before = attendeeIds(id);
        booking.setRoomId(room.getId());
        booking.setTitle(req.getTitle() == null || req.getTitle().isBlank() ? booking.getTitle() : req.getTitle().trim());
        booking.setStartTime(req.getStartTime());
        booking.setEndTime(req.getEndTime());
        booking.setAttendeeCount(req.getAttendeeCount());
        booking.setCity(city);
        if (req.getRemark() != null) {
            booking.setRemark(req.getRemark().trim());
        }
        booking.setUpdatedAt(LocalDateTime.now());
        bookingMapper.updateById(booking);

        List<Long> invited = replaceAttendees(booking, req.getAttendeeIds(), account);
        for (Long removed : before) {
            if (!invited.contains(removed)) {
                closeNotice(booking);
            }
        }
        notifyInvited(booking, room, invited, account);
    }

    /** 签到：开始前 15 分钟至开始后宽限期内开放 */
    @Transactional(rollbackFor = Exception.class)
    public void checkin(Long id) {
        Long account = support.requireAccountId();
        MeetingBooking booking = requireMine(id, account);
        if (!PENDING.equals(booking.getStatus())) {
            throw new BizException(MeetingErrorCode.BOOKING_STATUS_INVALID);
        }
        LocalDateTime now = LocalDateTime.now();
        int grace = support.noShowGraceMinutes();
        if (now.isBefore(booking.getStartTime().minusMinutes(15))
                || now.isAfter(booking.getStartTime().plusMinutes(grace))) {
            throw new BizException(MeetingErrorCode.CHECKIN_NOT_OPEN,
                    "签到在会议开始前 15 分钟开放，最迟开始后 " + grace + " 分钟");
        }
        LocalDateTime at = booking.getCheckinAt();
        booking.setStatus(CHECKED_IN);
        booking.setCheckinAt(at == null ? now : at);
        booking.setUpdatedAt(now);
        bookingMapper.updateById(booking);
    }

    /** 延时：签到后申请，重新跑冲突检测 */
    @Transactional(rollbackFor = Exception.class)
    public void extend(Long id, MeetingRequests.ActionRequest req) {
        Long account = support.requireAccountId();
        MeetingBooking booking = requireMine(id, account);
        if (!CHECKED_IN.equals(booking.getStatus())) {
            throw new BizException(MeetingErrorCode.BOOKING_STATUS_INVALID, "仅进行中的预定可以延时");
        }
        if (req == null || req.getEndTime() == null || !req.getEndTime().isAfter(booking.getEndTime())) {
            throw new BizException(MeetingErrorCode.BOOKING_TIME_INVALID, "结束时间必须晚于当前结束时间");
        }
        validateTime(booking.getStartTime(), req.getEndTime(), false);
        assertNoConflict(booking.getRoomId(), booking.getStartTime(), req.getEndTime(), booking.getId());
        booking.setEndTime(req.getEndTime());
        booking.setUpdatedAt(LocalDateTime.now());
        bookingMapper.updateById(booking);
    }

    @Transactional(rollbackFor = Exception.class)
    public void cancel(Long id) {
        Long account = support.requireAccountId();
        MeetingBooking booking = requireMine(id, account);
        if (!PENDING.equals(booking.getStatus())) {
            throw new BizException(MeetingErrorCode.BOOKING_STATUS_INVALID, "仅待开始的预定可以取消");
        }
        booking.setStatus(CANCELLED);
        booking.setUpdatedAt(LocalDateTime.now());
        bookingMapper.updateById(booking);
        closeNotice(booking);
    }

    /** 提前结束释放时段（与超时未签到释放区分：原因 MANUAL） */
    @Transactional(rollbackFor = Exception.class)
    public void release(Long id) {
        Long account = support.requireAccountId();
        MeetingBooking booking = requireMine(id, account);
        if (!PENDING.equals(booking.getStatus()) && !CHECKED_IN.equals(booking.getStatus())) {
            throw new BizException(MeetingErrorCode.BOOKING_STATUS_INVALID);
        }
        booking.setStatus(RELEASED);
        booking.setReleaseReason("MANUAL");
        booking.setUpdatedAt(LocalDateTime.now());
        bookingMapper.updateById(booking);
        closeNotice(booking);
    }

    /** 我的预定：服务端强制按 creator_id 过滤，前端不能伪造创建人 */
    public R.Page<MeetingBooking> myBookings(String tab, String city, String status, String keyword,
                                             LocalDate from, LocalDate to, int page, int size) {
        Long account = support.requireAccountId();
        LambdaQueryWrapper<MeetingBooking> qw = myQuery(account, city, status, keyword, from, to)
                .in(true, MeetingBooking::getStatus, tabStatuses(tab))
                .orderByDesc(MeetingBooking::getStartTime);
        Page<MeetingBooking> p = bookingMapper.selectPage(new Page<>(Math.max(page, 1), Math.max(size, 1)), qw);
        for (MeetingBooking b : p.getRecords()) {
            decorate(b);
        }
        return R.Page.of(p.getRecords(), p.getTotal());
    }

    /** 列表与页签角标共用筛选口径：创建人 + 城市 + 状态 + 关键词 + 日期范围 */
    private LambdaQueryWrapper<MeetingBooking> myQuery(Long account, String city, String status, String keyword,
                                                        LocalDate from, LocalDate to) {
        return new LambdaQueryWrapper<MeetingBooking>()
                .eq(MeetingBooking::getCreatorId, account)
                .eq(city != null && !city.isBlank(), MeetingBooking::getCity, city)
                .eq(status != null && !status.isBlank(), MeetingBooking::getStatus, status)
                .and(keyword != null && !keyword.isBlank(), w -> w
                        .like(MeetingBooking::getTitle, keyword.trim())
                        .or().like(MeetingBooking::getBookingNo, keyword.trim()))
                .ge(from != null, MeetingBooking::getStartTime,
                        from == null ? null : from.atStartOfDay())
                .lt(to != null, MeetingBooking::getStartTime,
                        to == null ? null : to.plusDays(1).atStartOfDay());
    }

    /** 页签角标：与列表同一套筛选（含日期范围），范围内无数据则各页签均为 0 */
    public Map<String, Long> myBookingCounts(String city, String status, String keyword,
                                             LocalDate from, LocalDate to) {
        Long account = support.requireAccountId();
        List<MeetingBooking> all = bookingMapper.selectList(
                myQuery(account, city, status, keyword, from, to).select(MeetingBooking::getStatus));
        Map<String, Long> out = new LinkedHashMap<>();
        out.put("booked", all.stream().filter(b -> PENDING.equals(b.getStatus())).count());
        out.put("started", all.stream().filter(b -> CHECKED_IN.equals(b.getStatus())).count());
        out.put("ended", all.stream().filter(b -> COMPLETED.equals(b.getStatus())).count());
        out.put("cancelled", all.stream()
                .filter(b -> CANCELLED.equals(b.getStatus()) || RELEASED.equals(b.getStatus())).count());
        return out;
    }

    private List<String> tabStatuses(String tab) {
        if (tab == null || tab.isBlank() || "all".equals(tab)) {
            return List.of(PENDING, CHECKED_IN, COMPLETED, CANCELLED, RELEASED);
        }
        return switch (tab) {
            case "booked" -> List.of(PENDING);
            case "started" -> List.of(CHECKED_IN);
            case "ended" -> List.of(COMPLETED);
            case "cancelled" -> List.of(CANCELLED, RELEASED);
            default -> List.of(PENDING, CHECKED_IN, COMPLETED, CANCELLED, RELEASED);
        };
    }

    /** 详情：创建人本人或被邀请人（需前端仍具备基础查看权限，由 @Permit 保证） */
    public MeetingBooking detail(Long id) {
        Long account = support.requireAccountId();
        MeetingBooking booking = require(id);
        boolean creator = account.equals(booking.getCreatorId());
        boolean invited = attendeeMapper.selectCount(new LambdaQueryWrapper<MeetingBookingAttendee>()
                .eq(MeetingBookingAttendee::getBookingId, id)
                .eq(MeetingBookingAttendee::getAccountId, account)) > 0;
        if (!creator && !invited) {
            throw new BizException(MeetingErrorCode.BOOKING_FORBIDDEN);
        }
        decorate(booking);
        return booking;
    }

    /** 概览：统计卡按所选日期取全平台口径；月历与日程为本人创建或被邀请 */
    public MeetingVOs.OverviewVO overview(LocalDate date, String month) {
        Long account = support.requireAccountId();
        LocalDate day = date == null ? LocalDate.now() : date;
        LocalDate monthDay = parseMonth(month, day);
        LocalDateTime now = LocalDateTime.now();
        List<Long> invitedIds = invitedBookingIds(account);
        MeetingVOs.OverviewVO vo = new MeetingVOs.OverviewVO();
        vo.setDate(day);
        vo.setMaxDays(support.maxAdvanceDays());
        vo.setMeetingCount(bookingMapper.selectCount(dayQuery(day)
                .notIn(MeetingBooking::getStatus, CANCELLED, RELEASED)));
        LocalDateTime checkinFrom = day.isAfter(LocalDate.now()) ? day.atStartOfDay() : now;
        vo.setToCheckinCount(bookingMapper.selectCount(dayQuery(day)
                .eq(MeetingBooking::getStatus, PENDING)
                .ge(MeetingBooking::getStartTime, checkinFrom)));
        vo.setInProgressCount(bookingMapper.selectCount(dayQuery(day)
                .eq(MeetingBooking::getStatus, CHECKED_IN)
                .le(MeetingBooking::getStartTime, now)
                .ge(MeetingBooking::getEndTime, now)));
        vo.setAvailableRooms(roomMapperAvailableCount());
        Map<String, Long> calendar = new LinkedHashMap<>();
        LocalDateTime monthStart = monthDay.withDayOfMonth(1).atStartOfDay();
        LocalDateTime monthEnd = monthDay.withDayOfMonth(monthDay.lengthOfMonth()).plusDays(1).atStartOfDay();
        List<MeetingBooking> monthBookings = bookingMapper.selectList(relatedQuery(account, invitedIds)
                .ge(MeetingBooking::getStartTime, monthStart)
                .lt(MeetingBooking::getStartTime, monthEnd)
                .select(MeetingBooking::getStartTime));
        for (MeetingBooking b : monthBookings) {
            calendar.merge(b.getStartTime().toLocalDate().toString(), 1L, Long::sum);
        }
        vo.setCalendar(calendar);
        List<MeetingBooking> agenda = bookingMapper.selectList(relatedQuery(account, invitedIds)
                .ge(MeetingBooking::getStartTime, day.atStartOfDay())
                .lt(MeetingBooking::getStartTime, day.plusDays(1).atStartOfDay())
                .orderByAsc(MeetingBooking::getStartTime));
        Set<Long> creatorIds = new HashSet<>();
        for (MeetingBooking b : agenda) {
            decorate(b);
            boolean creator = account.equals(b.getCreatorId());
            b.setRelation(creator ? "CREATOR" : "INVITEE");
            if (!creator && b.getCreatorId() != null) {
                creatorIds.add(b.getCreatorId());
            }
        }
        if (!creatorIds.isEmpty()) {
            Map<String, String> names = support.employeeNames(creatorIds);
            for (MeetingBooking b : agenda) {
                if ("INVITEE".equals(b.getRelation())) {
                    b.setCreatorName(names.get(String.valueOf(b.getCreatorId())));
                }
            }
        }
        vo.setAgenda(agenda);
        return vo;
    }

    /** 月历取数月份：month=yyyy-MM，非法时回退到所选日期所在月 */
    private LocalDate parseMonth(String month, LocalDate fallback) {
        if (month == null || month.isBlank()) {
            return fallback;
        }
        try {
            return YearMonth.parse(month.trim()).atDay(1);
        } catch (Exception e) {
            return fallback;
        }
    }

    /** 被邀请人的预约 ID；只用于概览聚合，不改变「我的预定」列表口径 */
    private List<Long> invitedBookingIds(Long account) {
        return attendeeMapper.selectList(new LambdaQueryWrapper<MeetingBookingAttendee>()
                        .eq(MeetingBookingAttendee::getAccountId, account)
                        .select(MeetingBookingAttendee::getBookingId))
                .stream().map(MeetingBookingAttendee::getBookingId)
                .filter(java.util.Objects::nonNull).toList();
    }

    /** 所选自然日的预约范围（按 start_time 归属日期） */
    private LambdaQueryWrapper<MeetingBooking> dayQuery(LocalDate day) {
        return new LambdaQueryWrapper<MeetingBooking>()
                .ge(MeetingBooking::getStartTime, day.atStartOfDay())
                .lt(MeetingBooking::getStartTime, day.plusDays(1).atStartOfDay());
    }

    /** 创建人或被邀请人可见的预约范围 */
    private LambdaQueryWrapper<MeetingBooking> relatedQuery(Long account, List<Long> invitedIds) {
        LambdaQueryWrapper<MeetingBooking> qw = new LambdaQueryWrapper<>();
        qw.and(w -> {
            w.eq(MeetingBooking::getCreatorId, account);
            if (!invitedIds.isEmpty()) {
                w.or().in(MeetingBooking::getId, invitedIds);
            }
        });
        return qw;
    }

    private long roomMapperAvailableCount() {
        return roomMapper.selectCount(new LambdaQueryWrapper<com.laimengchao.sso.meeting.module.meeting.entity.MeetingRoom>()
                .eq(com.laimengchao.sso.meeting.module.meeting.entity.MeetingRoom::getStatus, RoomService.AVAILABLE));
    }

    private void decorate(MeetingBooking booking) {
        if (booking.getRoomId() != null) {
            MeetingRoom room = roomService.require(booking.getRoomId());
            booking.setRoomName(room.getName());
            if (room.getCity() == null) {
                roomService.decorate(room);
            }
            booking.setLocation(String.join("·",
                    nvl(room.getCity()), nvl(room.getBuildingName()), nvl(room.getFloorNo()), nvl(room.getRoomNo())));
        }
        booking.setAttendeeIds(attendeeIds(booking.getId()));
    }

    private String nvl(String s) {
        return s == null ? "" : s;
    }

    private MeetingBooking require(Long id) {
        MeetingBooking booking = id == null ? null : bookingMapper.selectById(id);
        if (booking == null) {
            throw new BizException(MeetingErrorCode.BOOKING_NOT_FOUND);
        }
        return booking;
    }

    private MeetingBooking requireMine(Long id, Long account) {
        MeetingBooking booking = require(id);
        if (!account.equals(booking.getCreatorId())) {
            throw new BizException(MeetingErrorCode.BOOKING_FORBIDDEN);
        }
        return booking;
    }

    /** 爽约状态：按会议开始时间统计窗口内的 NO_SHOW 释放，达到阈值后从最近一次爽约起禁约 banDays 天 */
    public MeetingVOs.NoShowStatusVO noShowStatus(Long account) {
        MeetingVOs.NoShowStatusVO vo = new MeetingVOs.NoShowStatusVO();
        if (account == null) {
            return vo;
        }
        MeetingSupportService.NoShowRule rule = support.noShowRule();
        if (!rule.enabled()) {
            return vo;
        }
        LocalDateTime from = LocalDateTime.now().minusDays(rule.windowDays());
        List<MeetingBooking> rows = bookingMapper.selectList(new LambdaQueryWrapper<MeetingBooking>()
                .select(MeetingBooking::getId, MeetingBooking::getStartTime)
                .eq(MeetingBooking::getCreatorId, account)
                .eq(MeetingBooking::getStatus, RELEASED)
                .eq(MeetingBooking::getReleaseReason, "NO_SHOW")
                .ge(MeetingBooking::getStartTime, from)
                .orderByDesc(MeetingBooking::getStartTime));
        vo.setCount(rows == null ? 0 : rows.size());
        if (rows != null && rows.size() >= rule.threshold()) {
            LocalDateTime bannedUntil = rows.get(0).getStartTime().plusDays(rule.banDays());
            if (bannedUntil.isAfter(LocalDateTime.now())) {
                vo.setBannedUntil(bannedUntil);
            }
        }
        return vo;
    }

    private void assertNoShowAllowed(Long account) {
        MeetingVOs.NoShowStatusVO status = noShowStatus(account);
        if (status.getBannedUntil() != null) {
            throw new BizException(MeetingErrorCode.NO_SHOW_BOOKING_BANNED,
                    "近 " + support.noShowRule().windowDays() + " 天内未签到释放次数达到限制，"
                            + "暂不可预约会议室（至 " + status.getBannedUntil() + "）");
        }
    }

    private void validateTime(LocalDateTime start, LocalDateTime end, boolean checkPast) {
        // 结束时刻可到 24:00（存为次日 00:00），仍视为同一自然日的预约，不算跨天
        boolean midnightEnd = start != null && end != null
                && end.equals(start.toLocalDate().plusDays(1).atStartOfDay());
        if (start == null || end == null || !end.isAfter(start)
                || !(start.toLocalDate().equals(end.toLocalDate()) || midnightEnd)
                || (start.getMinute() != 0 && start.getMinute() != 30)
                || (end.getMinute() != 0 && end.getMinute() != 30)
                || start.getSecond() != 0 || end.getSecond() != 0
                || (!midnightEnd && end.toLocalTime().isAfter(LocalTime.of(23, 30)))) {
            throw new BizException(MeetingErrorCode.BOOKING_TIME_INVALID);
        }
        int maxMinutes = support.maxBookingMinutes();
        if (maxMinutes > 0 && Duration.between(start, end).toMinutes() > maxMinutes) {
            throw new BizException(MeetingErrorCode.BOOKING_DURATION_EXCEEDED,
                    "单次预约最长 " + maxMinutes + " 分钟");
        }
        if (!checkPast) {
            return;
        }
        LocalDate today = LocalDate.now();
        int maxDays = support.maxAdvanceDays();
        if (start.toLocalDate().isBefore(today)
                || start.toLocalDate().isAfter(today.plusDays(maxDays))) {
            throw new BizException(MeetingErrorCode.BOOKING_DATE_OUT_OF_RANGE,
                    "只能预约今天至 " + today.plusDays(maxDays) + " 的会议室");
        }
        if (start.toLocalDate().isEqual(today) && start.isBefore(LocalDateTime.now())) {
            throw new BizException(MeetingErrorCode.BOOKING_TIME_INVALID, "已过去的时段不可预约");
        }
    }

    private void validateCapacity(Integer count, MeetingRoom room) {
        int people = count == null ? 0 : count;
        if (people < 1) {
            throw new BizException(ErrorCode.PARAM_ERROR, "参会人数至少 1 人");
        }
        if (room.getMaxCapacity() != null && people > room.getMaxCapacity()) {
            throw new BizException(MeetingErrorCode.CAPACITY_EXCEEDED);
        }
    }

    private void assertNoConflict(Long roomId, LocalDateTime start, LocalDateTime end, Long excludeId) {
        Long hits = bookingMapper.selectCount(new LambdaQueryWrapper<MeetingBooking>()
                .eq(MeetingBooking::getRoomId, roomId)
                .in(MeetingBooking::getStatus, OCCUPYING)
                .lt(MeetingBooking::getStartTime, end)
                .gt(MeetingBooking::getEndTime, start)
                .ne(excludeId != null, MeetingBooking::getId, excludeId));
        if (hits != null && hits > 0) {
            throw new BizException(MeetingErrorCode.BOOKING_CONFLICT);
        }
    }

    private String nextBookingNo() {
        return "MT" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"))
                + String.format("%04d", ThreadLocalRandom.current().nextInt(10000));
    }

    private List<Long> replaceAttendees(MeetingBooking booking, Collection<Long> attendeeIds, Long creator) {
        attendeeMapper.delete(new LambdaQueryWrapper<MeetingBookingAttendee>()
                .eq(MeetingBookingAttendee::getBookingId, booking.getId()));
        List<Long> invited = new ArrayList<>();
        for (Long id : support.parseAccountIds(attendeeIds)) {
            if (id.equals(creator)) {
                continue;
            }
            MeetingBookingAttendee a = new MeetingBookingAttendee();
            a.setBookingId(booking.getId());
            a.setAccountId(id);
            a.setOrgCode(booking.getOrgCode());
            a.setStatus("INVITED");
            a.setNotified(false);
            a.setCreatedAt(LocalDateTime.now());
            attendeeMapper.insert(a);
            invited.add(id);
        }
        return invited;
    }

    private List<Long> attendeeIds(Long bookingId) {
        return attendeeMapper.selectList(new LambdaQueryWrapper<MeetingBookingAttendee>()
                        .eq(MeetingBookingAttendee::getBookingId, bookingId)
                        .select(MeetingBookingAttendee::getAccountId))
                .stream().map(MeetingBookingAttendee::getAccountId).toList();
    }

    private void notifyInvited(MeetingBooking booking, MeetingRoom room, List<Long> invited, Long inviter) {
        if (invited.isEmpty()) {
            return;
        }
        String inviterName = nvl(AccountContext.getUsername());
        String location = String.join("·", nvl(room.getCity()), nvl(room.getBuildingName()),
                nvl(room.getFloorNo()), nvl(room.getRoomNo()));
        Map<String, String> vars = new LinkedHashMap<>();
        vars.put("inviterName", inviterName);
        vars.put("title", booking.getTitle());
        vars.put("roomName", nvl(room.getName()));
        vars.put("location", location);
        vars.put("startTime", booking.getStartTime().format(NOTICE_TIME));
        vars.put("endTime", booking.getEndTime().format(NOTICE_TIME));
        vars.put("bookingNo", booking.getBookingNo());
        vars.put("attendeeCount", String.valueOf(booking.getAttendeeCount()));
        vars.put("link", detailBaseUrl + "/my-bookings?detail=" + booking.getId());
        NoticeSendDTO dto = new NoticeSendDTO();
        dto.setRuleCode("meeting.booking.invite");
        dto.setAccountIds(invited);
        dto.setBizType("meeting_booking");
        dto.setBizId(String.valueOf(booking.getId()));
        dto.setBizNo(booking.getBookingNo());
        dto.setApplicantName(inviterName);
        dto.setModule("会议室管理");
        dto.setLink(vars.get("link"));
        dto.setVars(vars);
        try {
            var res = noticeApi.sendBatch(dto);
            boolean ok = res != null && res.ok();
            if (ok) {
                attendeeMapper.update(null, new LambdaUpdateWrapper<MeetingBookingAttendee>()
                        .eq(MeetingBookingAttendee::getBookingId, booking.getId())
                        .in(MeetingBookingAttendee::getAccountId, invited)
                        .set(MeetingBookingAttendee::getNotified, true));
            } else {
                log.error("会议邀请通知未成功：bookingId={} resp={}", booking.getId(), res);
            }
        } catch (Exception e) {
            // 通知失败不影响预约主流程
            log.error("会议邀请通知发送失败：bookingId={} {}", booking.getId(), e.getMessage());
        }
    }

    private void closeNotice(MeetingBooking booking) {
        NoticeBizHandledDTO dto = new NoticeBizHandledDTO();
        dto.setBizType("meeting_booking");
        dto.setBizId(String.valueOf(booking.getId()));
        try {
            noticeApi.bizHandled(dto);
        } catch (Exception e) {
            log.error("关闭会议邀请待办失败：bookingId={} {}", booking.getId(), e.getMessage());
        }
    }

    /** job 中心派发：超时未签到释放 + 已结束单据自动完成；宽限期来自埋点，主键游标分批 + 条件更新保证幂等 */
    public int scanExpired() {
        LocalDateTime now = LocalDateTime.now();
        int grace = support.noShowGraceMinutes();
        int released = 0;
        long lastId = 0L;
        while (true) {
            List<MeetingBooking> batch = bookingMapper.selectList(new LambdaQueryWrapper<MeetingBooking>()
                    .eq(MeetingBooking::getStatus, PENDING)
                    .gt(MeetingBooking::getId, lastId)
                    .lt(MeetingBooking::getStartTime, now.minusMinutes(grace))
                    .orderByAsc(MeetingBooking::getId)
                    .last("limit 200"));
            if (batch.isEmpty()) {
                break;
            }
            lastId = batch.get(batch.size() - 1).getId();
            for (MeetingBooking booking : batch) {
                int updated = bookingMapper.update(null, new LambdaUpdateWrapper<MeetingBooking>()
                        .eq(MeetingBooking::getId, booking.getId())
                        .eq(MeetingBooking::getStatus, PENDING)
                        .set(MeetingBooking::getStatus, RELEASED)
                        .set(MeetingBooking::getReleaseReason, "NO_SHOW")
                        .set(MeetingBooking::getUpdatedAt, now));
                if (updated > 0) {
                    released++;
                    closeNotice(booking);
                    auditNoShowRelease(booking);
                }
            }
        }
        int completed = bookingMapper.update(null, new LambdaUpdateWrapper<MeetingBooking>()
                .eq(MeetingBooking::getStatus, CHECKED_IN)
                .lt(MeetingBooking::getEndTime, now)
                .set(MeetingBooking::getStatus, COMPLETED)
                .set(MeetingBooking::getUpdatedAt, now));
        log.info("会议室超时扫描：宽限期 {} 分钟，释放 {} 条，完成 {} 条", grace, released, completed);
        return released;
    }

    /** 调度释放无登录主体，按 SYSTEM 显式留痕；审计失败不影响业务 */
    private void auditNoShowRelease(MeetingBooking booking) {
        try {
            AuditEventRequest req = new AuditEventRequest();
            req.setApp("meeting");
            req.setEventCategory("SENSITIVE");
            req.setAction("meeting.booking.noShowRelease");
            req.setOperatorType("SYSTEM");
            req.setOperatorName("SYSTEM");
            req.setTargetType("MEETING_BOOKING");
            req.setTargetId(String.valueOf(booking.getId()));
            req.setDetail("result=success;releaseReason=NO_SHOW;title=" + booking.getTitle());
            req.setResult("success");
            auditApi.report(req);
        } catch (Exception e) {
            log.warn("上报未签到释放审计失败（已忽略）：bookingId={} {}", booking.getId(), e.getMessage());
        }
    }
}
