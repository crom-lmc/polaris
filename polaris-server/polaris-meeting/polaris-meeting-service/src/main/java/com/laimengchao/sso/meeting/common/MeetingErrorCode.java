package com.laimengchao.sso.meeting.common;

import com.laimengchao.sso.common.IErrorCode;
import lombok.Getter;

/** 会议室服务业务码段（8301+，与其他服务码段不冲突）。 */
@Getter
public enum MeetingErrorCode implements IErrorCode {

    ROOM_NOT_FOUND(8301, "会议室不存在或已删除"),
    LOCATION_NOT_FOUND(8302, "大楼或楼层不存在"),
    LOCATION_HAS_ROOM(8303, "已维护会议室，删除和停用已锁定"),
    LOCATION_HAS_FLOOR(8304, "该大楼下仍有楼层，禁止删除"),
    BOOKING_NOT_FOUND(8305, "预定不存在"),
    BOOKING_FORBIDDEN(8306, "只能操作本人创建的预定"),
    BOOKING_CONFLICT(8307, "该时段已被预定，请重新选择"),
    BOOKING_TIME_INVALID(8308, "时段不合法：不跨天、30 分钟步长、不能选过去时段"),
    BOOKING_DATE_OUT_OF_RANGE(8309, "超出可预约日期范围"),
    BOOKING_STATUS_INVALID(8310, "当前预定状态不支持该操作"),
    ROOM_NOT_AVAILABLE(8311, "会议室不可用或状态非可用"),
    CITY_MISMATCH(8312, "只能预定当前人员所在城市的会议室"),
    CAPACITY_EXCEEDED(8313, "参会人数超过会议室硬性上限"),
    ROOM_IN_USE(8314, "已有预定关联该会议室，禁止删除"),
    ATTENDEE_INVALID(8315, "参会人参数不合法"),
    CHECKIN_NOT_OPEN(8316, "未到签到开放时间"),
    BOOKING_DURATION_EXCEEDED(8317, "单次预约时长超过配置上限"),
    NO_SHOW_BOOKING_BANNED(8318, "爽约次数达到限制，暂不可预约会议室");

    private final int code;
    private final String message;

    MeetingErrorCode(int code, String message) {
        this.code = code;
        this.message = message;
    }

    @Override
    public int httpStatus() {
        return switch (code) {
            case 8301, 8302, 8305 -> 404;
            case 8306, 8312, 8318 -> 403;
            case 8303, 8304, 8307, 8308, 8309, 8310, 8311, 8313, 8314, 8315, 8316, 8317 -> 400;
            default -> 0;
        };
    }
}
