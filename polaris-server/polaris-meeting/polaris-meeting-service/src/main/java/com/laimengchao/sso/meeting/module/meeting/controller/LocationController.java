package com.laimengchao.sso.meeting.module.meeting.controller;

import com.laimengchao.sso.common.R;
import com.laimengchao.sso.common.security.PermissionCodes;
import com.laimengchao.sso.meeting.module.meeting.dto.MeetingRequests;
import com.laimengchao.sso.meeting.module.meeting.dto.MeetingVOs;
import com.laimengchao.sso.meeting.module.meeting.entity.MeetingBuilding;
import com.laimengchao.sso.meeting.module.meeting.entity.MeetingFloor;
import com.laimengchao.sso.meeting.module.meeting.service.LocationService;
import com.laimengchao.sso.meeting.security.Permit;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 位置维护：城市（字典只读）→ 大楼 → 楼层，严格按层级新增。 */
@RestController
@RequestMapping("/api/meetingroom")
@RequiredArgsConstructor
public class LocationController {

    private final LocationService locationService;

    @GetMapping("/buildings")
    @Permit(PermissionCodes.MEETING_ROOM_LIST)
    public R<List<MeetingVOs.LocationVO>> buildings(@RequestParam(value = "city", required = false) String city) {
        return R.ok(locationService.listBuildings(city));
    }

    /** 位置维护统计：大楼 / 楼层 / 会议室数量 */
    @GetMapping("/locations/stats")
    @Permit(PermissionCodes.MEETING_ROOM_LIST)
    public R<Map<String, Integer>> stats(@RequestParam(value = "city", required = false) String city) {
        List<MeetingVOs.LocationVO> buildings = locationService.listBuildings(city);
        Map<String, Integer> out = new LinkedHashMap<>();
        int floors = 0;
        int rooms = 0;
        for (MeetingVOs.LocationVO b : buildings) {
            floors += b.getChildren() == null ? 0 : b.getChildren().size();
            rooms += b.getRoomCount() == null ? 0 : b.getRoomCount();
        }
        out.put("buildings", buildings.size());
        out.put("floors", floors);
        out.put("rooms", rooms);
        return R.ok(out);
    }

    @PostMapping("/buildings")
    @Permit(PermissionCodes.MEETING_ROOM_EDIT)
    public R<MeetingBuilding> saveBuilding(@RequestParam("city") String city,
                                           @RequestBody MeetingRequests.BuildingSave req) {
        return R.ok(locationService.saveBuilding(city, req));
    }

    @PutMapping("/buildings/{id}")
    @Permit(PermissionCodes.MEETING_ROOM_EDIT)
    public R<Void> updateBuilding(@PathVariable("id") Long id,
                                  @RequestBody MeetingRequests.BuildingSave req) {
        locationService.updateBuilding(id, req);
        return R.ok();
    }

    @DeleteMapping("/buildings/{id}")
    @Permit(PermissionCodes.MEETING_ROOM_EDIT)
    public R<Void> deleteBuilding(@PathVariable("id") Long id) {
        locationService.deleteBuilding(id);
        return R.ok();
    }

    /** 楼层平铺：按城市展示所有大楼的楼层（位置维护页） */
    @GetMapping("/floors")
    @Permit(PermissionCodes.MEETING_ROOM_LIST)
    public R<List<MeetingVOs.LocationVO>> floors(@RequestParam(value = "city", required = false) String city) {
        return R.ok(locationService.floorsOfCity(city));
    }

    @PostMapping("/floors")
    @Permit(PermissionCodes.MEETING_ROOM_EDIT)
    public R<MeetingFloor> saveFloor(@RequestParam("buildingId") Long buildingId,
                                     @RequestBody MeetingRequests.FloorSave req) {
        return R.ok(locationService.saveFloor(buildingId, req));
    }

    @PutMapping("/floors/{id}")
    @Permit(PermissionCodes.MEETING_ROOM_EDIT)
    public R<Void> updateFloor(@PathVariable("id") Long id, @RequestBody MeetingRequests.FloorSave req) {
        locationService.updateFloor(id, req);
        return R.ok();
    }

    @DeleteMapping("/floors/{id}")
    @Permit(PermissionCodes.MEETING_ROOM_EDIT)
    public R<Void> deleteFloor(@PathVariable("id") Long id) {
        locationService.deleteFloor(id);
        return R.ok();
    }

    /** 房间新增表单的三级下拉：大楼 + 楼层一并给出 */
    @GetMapping("/location-tree")
    @Permit(PermissionCodes.MEETING_ROOM_LIST)
    public R<List<MeetingVOs.LocationVO>> tree(@RequestParam(value = "city", required = false) String city) {
        List<MeetingVOs.LocationVO> out = new ArrayList<>(locationService.listBuildings(city));
        return R.ok(out);
    }
}
