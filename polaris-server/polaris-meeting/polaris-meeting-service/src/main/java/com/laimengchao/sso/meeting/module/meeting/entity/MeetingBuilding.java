package com.laimengchao.sso.meeting.module.meeting.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.laimengchao.sso.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("meeting_building")
public class MeetingBuilding extends BaseEntity {
    private String city;
    private String name;
    private String address;
    private Integer sort;
    private String status;
    private String remark;
}
