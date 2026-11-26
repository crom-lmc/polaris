package com.laimengchao.sso.meeting.module.meeting.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.laimengchao.sso.meeting.module.meeting.entity.MeetingBooking;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/** 会议应用视图对象集合。雪花 ID 由 BaseEntity 的 ToStringSerializer 统一转字符串。 */
public final class MeetingVOs {

    private MeetingVOs() {
    }

    @Data
    public static class MeVO {
        private String accountId;
        private String username;
        private String name;
        private String city;
        private boolean superAdmin;
        private String appCode;
        private List<String> permissions;
    }

    @Data
    public static class BookingConfigVO {
        /** 最多可提前预约的自然日数；0=只能约当天 */
        private int maxDays;
        /** 未签到自动释放宽限期（分钟）；0=会议开始即释放 */
        private int graceMinutes;
        /** 单次预约最长时长（分钟）；0=不限制 */
        private int maxMinutes;
        /** 爽约统计窗口（天） */
        private int noShowWindowDays;
        /** 爽约触发次数；0=不启用限制 */
        private int noShowThreshold;
        /** 触发后的禁约天数；0=不启用限制 */
        private int noShowBanDays;
        /** 当前账号在统计窗口内的未签到释放次数 */
        private int noShowCount;
        /** 当前账号禁约截止时间；未禁约为 null */
        private LocalDateTime bannedUntil;
    }

    @Data
    public static class NoShowStatusVO {
        /** 统计窗口内的未签到释放次数 */
        private int count;
        /** 达到阈值时的禁约截止时间；未禁约为 null */
        private LocalDateTime bannedUntil;
    }

    @Data
    public static class DictItemVO {
        private String value;
        private String label;
        private Integer sort;
    }

    @Data
    public static class SlotVO {
        /** HH:mm */
        private String start;
        /** HH:mm，最后一格展示为 24:00 */
        private String end;
        /** FREE/MINE/OTHERS/PAST/DISABLED */
        private String state;
        private String title;
        /** 预约人姓名（工号），供时间轴提示展示「谁预约的」 */
        private String creatorName;
        private String bookingId;
    }

    @Data
    public static class RoomScheduleVO {
        private String roomId;
        private String name;
        private String status;
        private LocalDate date;
        private Integer maxCapacity;
        private Integer comfortCapacity;
        /** 设备字典 meeting.device 的 value 列表，供时间轴会议室名下方展示 */
        private List<String> devices;
        private List<SlotVO> slots;
    }

    @Data
    public static class ScheduleVO {
        private LocalDate date;
        private List<RoomScheduleVO> rooms;
    }

    @Data
    public static class LocationVO {
        private String id;
        private String name;
        private String city;
        private String address;
        private String floorNo;
        @JsonSerialize(using = ToStringSerializer.class)
        private Long buildingId;
        private Integer sort;
        private String status;
        private String remark;
        private Integer roomCount;
        private List<LocationVO> children;
    }

    @Data
    public static class OverviewVO {
        /** 实际生效的地区过滤（空=全平台） */
        private String city;
        /** 所选日期+地区的有效预约数（不含取消/释放） */
        private long meetingCount;
        /** 所选日期+地区待签到预约数 */
        private long toCheckinCount;
        /** 所选日期+地区当前进行中的预约数 */
        private long inProgressCount;
        /** 所选地区的可用会议室数（与日期无关） */
        private long availableRooms;
        /** 日期过滤可选的最大提前天数，来自埋点 meeting.booking.max_advance_days */
        private int maxDays;
        /** yyyy-MM-dd → 本人创建或被邀请且属于所选地区的会议数 */
        private Map<String, Long> calendar;
        /** yyyy-MM-dd → 当日会议摘要（用于月历格子内展示彩色标签），按开始时间升序 */
        private Map<String, List<CalendarEventVO>> calendarEvents;
        private List<MeetingBooking> agenda;
        private LocalDate date;
    }

    /** 月历格子内的会议标签：只带展示所需字段，避免把整单塞进 VO */
    @Data
    public static class CalendarEventVO {
        @JsonSerialize(using = ToStringSerializer.class)
        private Long bookingId;
        private String title;
        private String startTime;
        private String status;
        /** CREATOR=本人创建，INVITEE=被邀请 */
        private String relation;
    }

    @Data
    public static class EmployeeVO {
        private String accountId;
        private String name;
        private String empNo;
    }

    @Data
    public static class BookingDetailVO {
        private MeetingBooking booking;
        /** 详情链接是否可访问（创建人或被邀请人） */
        private boolean canView;
    }

    /** 爽约记录：由 RELEASED + NO_SHOW 派生，不额外落表 */
    @Data
    public static class NoShowVO {
        @JsonSerialize(using = ToStringSerializer.class)
        private Long bookingId;
        private String bookingNo;
        private String title;
        @JsonSerialize(using = ToStringSerializer.class)
        private Long roomId;
        private String roomName;
        private String location;
        private String city;
        private LocalDateTime startTime;
        private LocalDateTime endTime;
        /** 实际释放时间（爽约判定发生时刻） */
        private LocalDateTime releasedAt;
        @JsonSerialize(using = ToStringSerializer.class)
        private Long creatorId;
        private String creatorName;
    }

    /** 会议室使用率：按 9:00–18:00 工作时间裁剪，重叠预约叠加，故可超过 100% */
    @Data
    public static class UsageVO {
        private String city;
        private LocalDate from;
        private LocalDate to;
        private String workStart;
        private String workEnd;
        /** 单间会议室在统计区间内的理论工作分钟数（天数 × 540） */
        private long workMinutesPerRoom;
        private int totalRooms;
        private List<UsageItemVO> items;
    }

    @Data
    public static class UsageItemVO {
        @JsonSerialize(using = ToStringSerializer.class)
        private Long roomId;
        private String roomName;
        private String city;
        private String location;
        /** 计入使用时长的有效预约数（不含取消/爽约释放） */
        private int bookings;
        /** 工作时间内重叠预约累计分钟数，可大于 workMinutesPerRoom */
        private long usedMinutes;
        /** usedMinutes / workMinutesPerRoom × 100，保留一位小数 */
        private double rate;
    }
}
