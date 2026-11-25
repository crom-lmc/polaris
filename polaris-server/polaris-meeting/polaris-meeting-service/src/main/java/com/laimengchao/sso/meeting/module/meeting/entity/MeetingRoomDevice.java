package com.laimengchao.sso.meeting.module.meeting.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.laimengchao.sso.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("meeting_room_device")
public class MeetingRoomDevice extends BaseEntity {
    @JsonSerialize(using = ToStringSerializer.class)
    private Long roomId;
    private String deviceCode;
    private Integer qty;
    private String remark;
}
