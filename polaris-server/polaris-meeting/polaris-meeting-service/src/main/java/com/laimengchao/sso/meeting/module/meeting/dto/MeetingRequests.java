package com.laimengchao.sso.meeting.module.meeting.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/** 会议管理请求体集合（按页面归组，避免散落多个小文件）。 */
public final class MeetingRequests {

    private MeetingRequests() {
    }

    @Data
    public static class BuildingSave {
        /** 城市由服务端按当前上下文/URL 层级决定，前端传值仅作展示，不落库 */
        private String city;
        private String name;
        private String address;
        private Integer sort;
        private String status;
        private String remark;
    }

    @Data
    public static class FloorSave {
        /** 所属大楼由服务端按层级决定，忽略前端传值 */
        private Long buildingId;
        private String floorNo;
        private Integer sort;
        private String status;
        private String remark;
    }

    @Data
    public static class RoomSave {
        private Long floorId;
        private String name;
        private String roomNo;
        private String roomType;
        private String capacityTier;
        private Integer maxCapacity;
        private Integer comfortCapacity;
        private String layout;
        private List<String> fileIds;
        private String status;
        private Boolean needApproval;
        private String remark;
        private List<DeviceSave> devices;
    }

    @Data
    public static class DeviceSave {
        private String deviceCode;
        private Integer qty;
        private String remark;
    }

    @Data
    public static class BookingSave {
        private Long roomId;
        private String title;
        private LocalDateTime startTime;
        private LocalDateTime endTime;
        private Integer attendeeCount;
        private List<Long> attendeeIds;
        private String remark;
    }

    @Data
    public static class ActionRequest {
        /** 延时后的结束时间 */
        private LocalDateTime endTime;
        private String reason;
    }
}
