package com.laimengchao.sso.meeting.module.meeting.service.impl;

import com.laimengchao.sso.meeting.module.meeting.service.MeetingStatsService;
import com.laimengchao.sso.meeting.module.meeting.service.MeetingSupportService;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.laimengchao.sso.common.BizException;
import com.laimengchao.sso.common.ErrorCode;
import com.laimengchao.sso.common.R;
import com.laimengchao.sso.meeting.module.meeting.dto.MeetingVOs;
import com.laimengchao.sso.meeting.module.meeting.entity.MeetingBooking;
import com.laimengchao.sso.meeting.module.meeting.entity.MeetingBuilding;
import com.laimengchao.sso.meeting.module.meeting.entity.MeetingFloor;
import com.laimengchao.sso.meeting.module.meeting.entity.MeetingRoom;
import com.laimengchao.sso.meeting.module.meeting.mapper.MeetingBookingMapper;
import com.laimengchao.sso.meeting.module.meeting.mapper.MeetingBuildingMapper;
import com.laimengchao.sso.meeting.module.meeting.mapper.MeetingFloorMapper;
import com.laimengchao.sso.meeting.module.meeting.mapper.MeetingRoomMapper;
import com.laimengchao.sso.utils.AccountContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** 爽约记录与会议室使用率：均从 meeting_booking 派生，不新建统计表。 */
@Service
@RequiredArgsConstructor
public class MeetingStatsServiceImpl implements MeetingStatsService {

    private static final String NO_SHOW = "NO_SHOW";
    private static final LocalTime WORK_START = LocalTime.of(9, 0);
    private static final LocalTime WORK_END = LocalTime.of(18, 0);
    private static final long WORK_MINUTES_PER_DAY = Duration.between(WORK_START, WORK_END).toMinutes();
    private static final List<String> COUNTED_STATUS = List.of(
            BookingServiceImpl.PENDING, BookingServiceImpl.CHECKED_IN, BookingServiceImpl.COMPLETED);

    private final MeetingBookingMapper bookingMapper;
    private final MeetingRoomMapper roomMapper;
    private final MeetingFloorMapper floorMapper;
    private final MeetingBuildingMapper buildingMapper;
    private final MeetingSupportService support;

    /** 爽约记录：mine 强制按当前账号过滤；all 由 Controller 的管理员权限码保证 */
    public R.Page<MeetingVOs.NoShowVO> noShows(boolean all, LocalDate from, LocalDate to,
                                               String city, int page, int size) {
        // 日期可清空：from/to 为空即不加过滤（查全部），与「我的预定」列表口径一致；
        // 只有两端都传时才校验先后，避免清空后误报参数错误。
        if (from != null && to != null && from.isAfter(to)) {
            throw new BizException(ErrorCode.PARAM_ERROR, "开始日期不能晚于结束日期");
        }
        Long current = AccountContext.getAccountId();
        if (!all && current == null) {
            return R.Page.of(List.of(), 0);
        }
        LambdaQueryWrapper<MeetingBooking> qw = new LambdaQueryWrapper<MeetingBooking>()
                .eq(MeetingBooking::getStatus, BookingServiceImpl.RELEASED)
                .eq(MeetingBooking::getReleaseReason, NO_SHOW)
                .ge(from != null, MeetingBooking::getStartTime, from == null ? null : from.atStartOfDay())
                .lt(to != null, MeetingBooking::getStartTime, to == null ? null : to.plusDays(1).atStartOfDay())
                .eq(!all, MeetingBooking::getCreatorId, current)
                .eq(city != null && !city.isBlank(), MeetingBooking::getCity, city == null ? null : city.trim())
                .orderByDesc(MeetingBooking::getStartTime);
        Page<MeetingBooking> p = bookingMapper.selectPage(
                new Page<>(Math.max(page, 1), Math.min(Math.max(size, 1), 100)), qw);
        List<MeetingBooking> rows = p.getRecords() == null ? List.of() : p.getRecords();

        Map<Long, MeetingFloor> floors = floorMap();
        Map<Long, MeetingBuilding> buildings = buildingMap();
        Map<Long, MeetingRoom> rooms = roomMap(rows.stream().map(MeetingBooking::getRoomId).toList(), floors, buildings);
        Set<Long> creatorIds = new HashSet<>();
        for (MeetingBooking row : rows) {
            if (row.getCreatorId() != null) {
                creatorIds.add(row.getCreatorId());
            }
        }
        Map<String, String> names = creatorIds.isEmpty() ? Map.of() : support.employeeNames(creatorIds);
        List<MeetingVOs.NoShowVO> list = new ArrayList<>();
        for (MeetingBooking row : rows) {
            MeetingVOs.NoShowVO vo = new MeetingVOs.NoShowVO();
            vo.setBookingId(row.getId());
            vo.setBookingNo(row.getBookingNo());
            vo.setTitle(row.getTitle());
            vo.setRoomId(row.getRoomId());
            MeetingRoom room = rooms.get(row.getRoomId());
            vo.setRoomName(room == null ? row.getRoomName() : room.getName());
            vo.setLocation(room == null ? "" : locationOf(room, floors, buildings));
            vo.setCity(row.getCity());
            vo.setStartTime(row.getStartTime());
            vo.setEndTime(row.getEndTime());
            vo.setReleasedAt(row.getUpdatedAt());
            vo.setCreatorId(row.getCreatorId());
            vo.setCreatorName(names.get(String.valueOf(row.getCreatorId())));
            list.add(vo);
        }
        return R.Page.of(list, p.getTotal());
    }

    /** 分地区、按会议室统计 9:00–18:00 使用率；重叠预约按分钟累加，故理论上可超过 100% */
    public MeetingVOs.UsageVO usage(LocalDate from, LocalDate to, String city) {
        LocalDate end = to == null ? LocalDate.now() : to;
        LocalDate start = from == null ? end.minusDays(6) : from;
        if (start.isAfter(end)) {
            throw new BizException(ErrorCode.PARAM_ERROR, "开始日期不能晚于结束日期");
        }
        long days = ChronoUnit.DAYS.between(start, end) + 1;
        if (days > 366) {
            throw new BizException(ErrorCode.PARAM_ERROR, "使用率统计区间不能超过一年");
        }
        MeetingVOs.UsageVO out = new MeetingVOs.UsageVO();
        out.setCity(city == null || city.isBlank() ? "" : city.trim());
        out.setFrom(start);
        out.setTo(end);
        out.setWorkStart(WORK_START.toString());
        out.setWorkEnd(WORK_END.toString());
        out.setWorkMinutesPerRoom(days * WORK_MINUTES_PER_DAY);
        out.setItems(List.of());

        Map<Long, MeetingFloor> floors = floorMap();
        Map<Long, MeetingBuilding> buildings = buildingMap();
        String filterCity = out.getCity();
        List<MeetingRoom> rooms = new ArrayList<>();
        for (MeetingRoom room : roomMapper.selectList(new LambdaQueryWrapper<MeetingRoom>()
                .orderByAsc(MeetingRoom::getId))) {
            if (matchesCity(room, filterCity, floors, buildings)) {
                rooms.add(room);
            }
        }
        if (rooms.isEmpty()) {
            return out;
        }
        out.setTotalRooms(rooms.size());
        Map<Long, Long> used = new HashMap<>();
        Map<Long, Integer> counts = new HashMap<>();
        Set<Long> roomIds = new HashSet<>();
        for (MeetingRoom room : rooms) {
            roomIds.add(room.getId());
            used.put(room.getId(), 0L);
            counts.put(room.getId(), 0);
        }
        LocalDateTime fromTs = start.atStartOfDay();
        LocalDateTime toTs = end.plusDays(1).atStartOfDay();
        List<MeetingBooking> bookings = bookingMapper.selectList(new LambdaQueryWrapper<MeetingBooking>()
                .in(MeetingBooking::getRoomId, roomIds)
                .in(MeetingBooking::getStatus, COUNTED_STATUS)
                .lt(MeetingBooking::getStartTime, toTs)
                .gt(MeetingBooking::getEndTime, fromTs));
        for (MeetingBooking booking : bookings) {
            long minutes = workMinutes(booking, start, end);
            if (minutes <= 0) {
                continue;
            }
            used.merge(booking.getRoomId(), minutes, Long::sum);
            counts.merge(booking.getRoomId(), 1, Integer::sum);
        }
        List<MeetingVOs.UsageItemVO> items = new ArrayList<>();
        double denominator = out.getWorkMinutesPerRoom();
        for (MeetingRoom room : rooms) {
            MeetingVOs.UsageItemVO item = new MeetingVOs.UsageItemVO();
            item.setRoomId(room.getId());
            item.setRoomName(room.getName());
            item.setLocation(locationOf(room, floors, buildings));
            MeetingFloor floor = floors.get(room.getFloorId());
            MeetingBuilding building = floor == null ? null : buildings.get(floor.getBuildingId());
            item.setCity(building == null ? "" : building.getCity());
            item.setBookings(counts.getOrDefault(room.getId(), 0));
            item.setUsedMinutes(used.getOrDefault(room.getId(), 0L));
            item.setRate(denominator <= 0 ? 0 : Math.round(item.getUsedMinutes() * 1000.0 / denominator) / 10.0);
            items.add(item);
        }
        items.sort(Comparator.comparing((MeetingVOs.UsageItemVO i) -> nvl(i.getCity()))
                .thenComparing(Comparator.comparing(MeetingVOs.UsageItemVO::getRate).reversed()));
        out.setItems(items);
        return out;
    }

    /** 逐日裁剪到 9:00–18:00；结束时间存次日 00:00 时自动被裁掉，不会算成跨天占用 */
    private long workMinutes(MeetingBooking booking, LocalDate start, LocalDate end) {
        if (booking.getStartTime() == null || booking.getEndTime() == null) {
            return 0;
        }
        LocalDate day = booking.getStartTime().toLocalDate().isBefore(start)
                ? start : booking.getStartTime().toLocalDate();
        LocalDate last = booking.getEndTime().toLocalDate().isAfter(end)
                ? end : booking.getEndTime().toLocalDate();
        long total = 0;
        for (; !day.isAfter(last); day = day.plusDays(1)) {
            LocalDateTime s = booking.getStartTime().isBefore(day.atTime(WORK_START))
                    ? day.atTime(WORK_START) : booking.getStartTime();
            LocalDateTime e = booking.getEndTime().isAfter(day.atTime(WORK_END))
                    ? day.atTime(WORK_END) : booking.getEndTime();
            if (e.isAfter(s)) {
                total += Duration.between(s, e).toMinutes();
            }
        }
        return total;
    }

    private boolean matchesCity(MeetingRoom room, String city,
                                Map<Long, MeetingFloor> floors, Map<Long, MeetingBuilding> buildings) {
        if (city == null || city.isBlank()) {
            return true;
        }
        MeetingFloor floor = floors.get(room.getFloorId());
        MeetingBuilding building = floor == null ? null : buildings.get(floor.getBuildingId());
        return building != null && city.equals(building.getCity());
    }

    private Map<Long, MeetingRoom> roomMap(Collection<Long> ids,
                                           Map<Long, MeetingFloor> floors, Map<Long, MeetingBuilding> buildings) {
        Map<Long, MeetingRoom> out = new HashMap<>();
        if (ids == null || ids.isEmpty()) {
            return out;
        }
        Set<Long> unique = new HashSet<>(ids);
        unique.remove(null);
        if (unique.isEmpty()) {
            return out;
        }
        for (MeetingRoom room : roomMapper.selectBatchIds(unique)) {
            out.put(room.getId(), room);
        }
        return out;
    }

    private Map<Long, MeetingFloor> floorMap() {
        Map<Long, MeetingFloor> out = new HashMap<>();
        for (MeetingFloor floor : floorMapper.selectList(null)) {
            out.put(floor.getId(), floor);
        }
        return out;
    }

    private Map<Long, MeetingBuilding> buildingMap() {
        Map<Long, MeetingBuilding> out = new HashMap<>();
        for (MeetingBuilding building : buildingMapper.selectList(null)) {
            out.put(building.getId(), building);
        }
        return out;
    }

    private String locationOf(MeetingRoom room,
                              Map<Long, MeetingFloor> floors, Map<Long, MeetingBuilding> buildings) {
        MeetingFloor floor = floors.get(room.getFloorId());
        MeetingBuilding building = floor == null ? null : buildings.get(floor.getBuildingId());
        List<String> parts = new ArrayList<>();
        if (building != null) {
            parts.add(nvl(building.getCity()));
            parts.add(nvl(building.getName()));
        }
        if (floor != null) {
            parts.add(nvl(floor.getFloorNo()));
        }
        parts.add(nvl(room.getRoomNo()));
        parts.removeIf(String::isBlank);
        return String.join("·", parts);
    }

    private String nvl(String value) {
        return value == null ? "" : value;
    }
}
