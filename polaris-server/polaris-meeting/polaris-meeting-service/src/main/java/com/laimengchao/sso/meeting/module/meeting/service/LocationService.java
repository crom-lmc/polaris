package com.laimengchao.sso.meeting.module.meeting.service;

import com.laimengchao.sso.meeting.module.meeting.dto.MeetingRequests;
import com.laimengchao.sso.meeting.module.meeting.dto.MeetingVOs;
import com.laimengchao.sso.meeting.module.meeting.entity.MeetingBuilding;
import com.laimengchao.sso.meeting.module.meeting.entity.MeetingFloor;
import java.util.List;

public interface LocationService {

    List<MeetingVOs.LocationVO> listBuildings(String city);

    MeetingBuilding requireBuilding(Long id);

    MeetingFloor requireFloor(Long id);

    MeetingBuilding saveBuilding(String city, MeetingRequests.BuildingSave req);

    void updateBuilding(Long id, MeetingRequests.BuildingSave req);

    void deleteBuilding(Long id);

    MeetingFloor saveFloor(Long buildingId, MeetingRequests.FloorSave req);

    void updateFloor(Long id, MeetingRequests.FloorSave req);

    void deleteFloor(Long id);

    /** 按城市拿所有可用楼层（预定选房三级筛选） */
    List<MeetingVOs.LocationVO> floorsOfCity(String city);

}
