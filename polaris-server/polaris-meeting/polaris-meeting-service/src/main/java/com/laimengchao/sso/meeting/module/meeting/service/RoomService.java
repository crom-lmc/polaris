package com.laimengchao.sso.meeting.module.meeting.service;

import com.laimengchao.sso.meeting.module.meeting.dto.MeetingRequests;
import com.laimengchao.sso.meeting.module.meeting.entity.MeetingRoom;
import java.util.List;

public interface RoomService {

    List<MeetingRoom> list(String city, Long buildingId, Long floorId, String roomType, String capacityTier, String status, String deviceCode, String keyword);

    /** 可用会议室数量：city 非空时按城市（经大楼→楼层关联）过滤，空则全平台 */
    long availableCountOfCity(String city);

    /** 填充位置、设备与图片临时 URL；不修改业务表字段 */
    void decorate(MeetingRoom room);

    MeetingRoom require(Long id);

    MeetingRoom requireAvailable(Long id);

    MeetingRoom save(MeetingRequests.RoomSave req);

    void update(Long id, MeetingRequests.RoomSave req);

    void delete(Long id);

    void changeStatus(Long id, String status);

    /** 用户所在城市下的所有房间（预定页时间轴） */
    List<MeetingRoom> roomsOfCity(String city);
}
