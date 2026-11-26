package com.laimengchao.sso.meeting.module.meeting.controller;

import com.laimengchao.sso.common.R;
import com.laimengchao.sso.common.security.PermissionCodes;
import com.laimengchao.sso.meeting.module.meeting.dto.MeetingRequests;
import com.laimengchao.sso.meeting.module.meeting.entity.MeetingRoom;
import com.laimengchao.sso.meeting.module.meeting.service.RoomService;
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

import java.util.List;
import java.util.Map;

/** 会议室台账：维护端全局可见，由 meeting:room:edit 控制写操作。 */
@RestController
@RequestMapping("/api/meetingroom/rooms")
@RequiredArgsConstructor
public class RoomController {

    private final RoomService roomService;

    @GetMapping
    @Permit(PermissionCodes.MEETING_ROOM_LIST)
    public R<List<MeetingRoom>> list(
            @RequestParam(value = "city", required = false) String city,
            @RequestParam(value = "buildingId", required = false) Long buildingId,
            @RequestParam(value = "floorId", required = false) Long floorId,
            @RequestParam(value = "roomType", required = false) String roomType,
            @RequestParam(value = "capacityTier", required = false) String capacityTier,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "deviceCode", required = false) String deviceCode,
            @RequestParam(value = "keyword", required = false) String keyword) {
        return R.ok(roomService.list(city, buildingId, floorId, roomType, capacityTier, status, deviceCode, keyword));
    }

    /** 预定弹窗的「会议室简介」也要读详情：只读放宽到预定页权限，维护列表仍限 room:list */
    @GetMapping("/{id}")
    @Permit({PermissionCodes.MEETING_ROOM_LIST, PermissionCodes.MEETING_BOOKING_VIEW})
    public R<MeetingRoom> detail(@PathVariable("id") Long id) {
        MeetingRoom room = roomService.require(id);
        roomService.decorate(room);
        return R.ok(room);
    }

    @PostMapping
    @Permit(PermissionCodes.MEETING_ROOM_EDIT)
    public R<MeetingRoom> save(@RequestBody MeetingRequests.RoomSave req) {
        return R.ok(roomService.save(req));
    }

    @PutMapping("/{id}")
    @Permit(PermissionCodes.MEETING_ROOM_EDIT)
    public R<Void> update(@PathVariable("id") Long id, @RequestBody MeetingRequests.RoomSave req) {
        roomService.update(id, req);
        return R.ok();
    }

    @DeleteMapping("/{id}")
    @Permit(PermissionCodes.MEETING_ROOM_EDIT)
    public R<Void> delete(@PathVariable("id") Long id) {
        roomService.delete(id);
        return R.ok();
    }

    /** 状态切换：可用 / 维护 / 停用，均不自动取消历史预定 */
    @PostMapping("/{id}/status")
    @Permit(PermissionCodes.MEETING_ROOM_EDIT)
    public R<Void> changeStatus(@PathVariable("id") Long id, @RequestBody Map<String, String> body) {
        roomService.changeStatus(id, body == null ? null : body.get("status"));
        return R.ok();
    }
}
