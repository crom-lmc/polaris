package com.laimengchao.sso.meeting.module.meeting.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.laimengchao.sso.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("meeting_floor")
public class MeetingFloor extends BaseEntity {
    @JsonSerialize(using = ToStringSerializer.class)
    private Long buildingId;
    private String floorNo;
    private Integer sort;
    private String status;
    private String remark;
}
