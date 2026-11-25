package com.laimengchao.sso.meeting.module.meeting.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.laimengchao.sso.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;
import java.util.List;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("meeting_booking")
public class MeetingBooking extends BaseEntity {
    private String bookingNo;
    /** 雪花 ID 必须序列化为字符串，否则 JS 精度丢失导致前端匹配不到房间 */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long roomId;
    private String title;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private Integer attendeeCount;
    /** PENDING/CHECKED_IN/COMPLETED/CANCELLED/RELEASED */
    private String status;
    private LocalDateTime checkinAt;
    private String releaseReason;
    private String city;
    @JsonSerialize(using = ToStringSerializer.class)
    private Long creatorId;
    /** 预留平台组织编码（非 org_id） */
    private String orgCode;
    private String remark;

    @TableField(exist = false)
    private String roomName;
    @TableField(exist = false)
    private String location;
    @TableField(exist = false)
    private String creatorName;
    @TableField(exist = false)
    @JsonSerialize(contentUsing = ToStringSerializer.class)
    private List<Long> attendeeIds;
    @TableField(exist = false)
    private List<MeetingBookingAttendee> attendees;
    /** 概览日程视角：CREATOR（本人创建）/ INVITEE（被邀请），仅概览返回 */
    @TableField(exist = false)
    private String relation;
}
