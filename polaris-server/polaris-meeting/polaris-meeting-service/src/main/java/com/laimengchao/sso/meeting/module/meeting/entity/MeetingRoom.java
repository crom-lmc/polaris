package com.laimengchao.sso.meeting.module.meeting.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.laimengchao.sso.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("meeting_room")
public class MeetingRoom extends BaseEntity {
    @JsonSerialize(using = ToStringSerializer.class)
    private Long floorId;
    private String name;
    private String roomNo;
    private String roomType;
    private String capacityTier;
    private Integer maxCapacity;
    private Integer comfortCapacity;
    private String layout;
    /** 统一文件服务 file_id，逗号分隔；不在业务表存 URL */
    private String fileIds;
    private String status;
    private Boolean needApproval;
    private String remark;

    /** 以下为关联展示字段，不落库 */
    @TableField(exist = false)
    private String city;
    @TableField(exist = false)
    private String buildingName;
    @TableField(exist = false)
    private String floorNo;
    @TableField(exist = false)
    private List<MeetingRoomDevice> devices;
    @TableField(exist = false)
    private List<String> roomImages;
}
