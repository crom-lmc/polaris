package com.laimengchao.sso.meeting.module.meeting.service.impl;

import com.laimengchao.sso.meeting.module.meeting.service.RoomService;
import com.laimengchao.sso.meeting.module.meeting.service.LocationService;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.laimengchao.sso.common.BizException;
import com.laimengchao.sso.common.ErrorCode;
import com.laimengchao.sso.file.client.api.FileApi;
import com.laimengchao.sso.file.client.dto.SignUrlRequest;
import com.laimengchao.sso.file.client.dto.SignUrlResult;
import com.laimengchao.sso.file.client.dto.R;
import com.laimengchao.sso.meeting.common.MeetingErrorCode;
import com.laimengchao.sso.meeting.module.meeting.dto.MeetingRequests;
import com.laimengchao.sso.meeting.module.meeting.entity.MeetingBooking;
import com.laimengchao.sso.meeting.module.meeting.entity.MeetingBuilding;
import com.laimengchao.sso.meeting.module.meeting.entity.MeetingFloor;
import com.laimengchao.sso.meeting.module.meeting.entity.MeetingRoom;
import com.laimengchao.sso.meeting.module.meeting.entity.MeetingRoomDevice;
import com.laimengchao.sso.meeting.module.meeting.mapper.MeetingBookingMapper;
import com.laimengchao.sso.meeting.module.meeting.mapper.MeetingBuildingMapper;
import com.laimengchao.sso.meeting.module.meeting.mapper.MeetingFloorMapper;
import com.laimengchao.sso.meeting.module.meeting.mapper.MeetingRoomDeviceMapper;
import com.laimengchao.sso.meeting.module.meeting.mapper.MeetingRoomMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/** 会议室台账：位置 + 容量档位 + 设备清单 + 附件 + 状态，维护端全局可见。 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RoomServiceImpl implements RoomService {

    /** 与 settings 字典 meeting.room_status 的 value 保持一致 */
    public static final String AVAILABLE = "可用";
    public static final String MAINTAIN = "维护";
    public static final String DISABLED = "停用";

    private final MeetingRoomMapper roomMapper;
    private final MeetingRoomDeviceMapper deviceMapper;
    private final MeetingBuildingMapper buildingMapper;
    private final MeetingFloorMapper floorMapper;
    private final MeetingBookingMapper bookingMapper;
    private final LocationService locationService;
    private final FileApi fileApi;

    public List<MeetingRoom> list(String city, Long buildingId, Long floorId, String roomType,
                                  String capacityTier, String status, String deviceCode, String keyword) {
        Set<Long> deviceFiltered = null;
        if (deviceCode != null && !deviceCode.isBlank()) {
            deviceFiltered = deviceMapper.selectList(new LambdaQueryWrapper<MeetingRoomDevice>()
                            .eq(MeetingRoomDevice::getDeviceCode, deviceCode.trim()))
                    .stream().map(MeetingRoomDevice::getRoomId).collect(Collectors.toSet());
            if (deviceFiltered.isEmpty()) {
                return List.of();
            }
        }
        Set<Long> floorIds = null;
        if (floorId != null) {
            floorIds = Set.of(floorId);
        } else if (buildingId != null) {
            floorIds = floorIdsOfBuilding(buildingId);
        } else if (city != null && !city.isBlank()) {
            floorIds = floorIdsOfCity(city);
        }
        // 已指定层级但没有匹配楼层时必须是空集，不能退化成全表
        if (floorIds != null && floorIds.isEmpty()) {
            return List.of();
        }
        LambdaQueryWrapper<MeetingRoom> qw = new LambdaQueryWrapper<MeetingRoom>()
                .in(floorIds != null && !floorIds.isEmpty(), MeetingRoom::getFloorId, floorIds)
                .eq(roomType != null && !roomType.isBlank(), MeetingRoom::getRoomType, roomType)
                .eq(capacityTier != null && !capacityTier.isBlank(), MeetingRoom::getCapacityTier, capacityTier)
                .eq(status != null && !status.isBlank(), MeetingRoom::getStatus, status)
                .and(keyword != null && !keyword.isBlank(), w -> w
                        .like(MeetingRoom::getName, keyword.trim())
                        .or().like(MeetingRoom::getRoomNo, (keyword == null ? null : keyword.trim())))
                .orderByAsc(MeetingRoom::getId);
        List<MeetingRoom> rooms = roomMapper.selectList(qw);
        List<MeetingRoom> out = new ArrayList<>();
        for (MeetingRoom room : rooms) {
            if (deviceFiltered != null && !deviceFiltered.contains(room.getId())) {
                continue;
            }
            decorate(room);
            out.add(room);
        }
        return out;
    }

    /** 可用会议室数量：city 非空时按城市（经大楼→楼层关联）过滤，空则全平台 */
    public long availableCountOfCity(String city) {
        if (city == null || city.isBlank()) {
            return roomMapper.selectCount(new LambdaQueryWrapper<MeetingRoom>()
                    .eq(MeetingRoom::getStatus, AVAILABLE));
        }
        Set<Long> floorIds = floorIdsOfCity(city.trim());
        if (floorIds.isEmpty()) {
            return 0;
        }
        return roomMapper.selectCount(new LambdaQueryWrapper<MeetingRoom>()
                .in(MeetingRoom::getFloorId, floorIds)
                .eq(MeetingRoom::getStatus, AVAILABLE));
    }

    private Set<Long> floorIdsOfBuilding(Long buildingId) {
        return floorMapper.selectList(new LambdaQueryWrapper<MeetingFloor>()
                        .eq(MeetingFloor::getBuildingId, buildingId))
                .stream().map(MeetingFloor::getId).collect(Collectors.toSet());
    }

    private Set<Long> floorIdsOfCity(String city) {
        List<MeetingBuilding> buildings = buildingMapper.selectList(new LambdaQueryWrapper<MeetingBuilding>()
                .eq(MeetingBuilding::getCity, city));
        if (buildings.isEmpty()) {
            return Set.of();
        }
        return floorMapper.selectList(new LambdaQueryWrapper<MeetingFloor>()
                        .in(MeetingFloor::getBuildingId, buildings.stream().map(MeetingBuilding::getId).toList()))
                .stream().map(MeetingFloor::getId).collect(Collectors.toSet());
    }

    /** 填充位置、设备与图片临时 URL；不修改业务表字段 */
    public void decorate(MeetingRoom room) {
        MeetingFloor floor = floorMapper.selectById(room.getFloorId());
        if (floor != null) {
            room.setFloorNo(floor.getFloorNo());
            MeetingBuilding building = buildingMapper.selectById(floor.getBuildingId());
            if (building != null) {
                room.setCity(building.getCity());
                room.setBuildingName(building.getName());
            }
        }
        room.setDevices(deviceMapper.selectList(new LambdaQueryWrapper<MeetingRoomDevice>()
                .eq(MeetingRoomDevice::getRoomId, room.getId())
                .orderByAsc(MeetingRoomDevice::getId)));
        List<String> images = new ArrayList<>();
        if (room.getFileIds() != null && !room.getFileIds().isBlank()) {
            SignUrlRequest req = new SignUrlRequest();
            req.setPurpose("VIEW");
            req.setExpiresSeconds(900L);
            for (String id : Arrays.asList(room.getFileIds().split(","))) {
                if (id.isBlank()) {
                    continue;
                }
                try {
                    R<SignUrlResult> resp = fileApi.sign(id.trim(), req);
                    if (resp != null && resp.ok() && resp.getData() != null && resp.getData().getUrl() != null) {
                        images.add(resp.getData().getUrl());
                    }
                } catch (Exception e) {
                    log.warn("会议室图片签名失败：fileId={} {}", id, e.getMessage());
                }
            }
        }
        room.setRoomImages(images);
    }

    public MeetingRoom require(Long id) {
        MeetingRoom room = id == null ? null : roomMapper.selectById(id);
        if (room == null) {
            throw new BizException(MeetingErrorCode.ROOM_NOT_FOUND);
        }
        return room;
    }

    public MeetingRoom requireAvailable(Long id) {
        MeetingRoom room = require(id);
        if (!AVAILABLE.equals(room.getStatus())) {
            throw new BizException(MeetingErrorCode.ROOM_NOT_AVAILABLE);
        }
        return room;
    }

    @Transactional(rollbackFor = Exception.class)
    public MeetingRoom save(MeetingRequests.RoomSave req) {
        validate(req);
        MeetingFloor floor = locationService.requireFloor(req.getFloorId());
        MeetingRoom room = new MeetingRoom();
        apply(room, req);
        room.setCreatedAt(LocalDateTime.now());
        roomMapper.insert(room);
        replaceDevices(room.getId(), req.getDevices());
        decorate(room);
        return room;
    }

    @Transactional(rollbackFor = Exception.class)
    public void update(Long id, MeetingRequests.RoomSave req) {
        MeetingRoom room = require(id);
        validate(req);
        apply(room, req);
        room.setUpdatedAt(LocalDateTime.now());
        roomMapper.updateById(room);
        replaceDevices(id, req.getDevices());
    }

    private void validate(MeetingRequests.RoomSave req) {
        if (req.getFloorId() == null) {
            throw new BizException(ErrorCode.PARAM_ERROR, "请选择楼层");
        }
        if (req.getName() == null || req.getName().isBlank()) {
            throw new BizException(ErrorCode.PARAM_ERROR, "会议室名称不能为空");
        }
        int max = req.getMaxCapacity() == null ? 0 : req.getMaxCapacity();
        int comfort = req.getComfortCapacity() == null ? 0 : req.getComfortCapacity();
        if (max <= 0 || comfort < 0 || comfort > max) {
            throw new BizException(ErrorCode.PARAM_ERROR, "容量不合法：舒适人数不能超过硬性上限");
        }
    }

    private void apply(MeetingRoom room, MeetingRequests.RoomSave req) {
        room.setFloorId(req.getFloorId());
        room.setName(req.getName().trim());
        room.setRoomNo(req.getRoomNo() == null ? "" : req.getRoomNo().trim());
        room.setRoomType(req.getRoomType() == null ? "" : req.getRoomType());
        room.setCapacityTier(req.getCapacityTier() == null ? "" : req.getCapacityTier());
        room.setMaxCapacity(req.getMaxCapacity());
        room.setComfortCapacity(req.getComfortCapacity() == null ? 0 : req.getComfortCapacity());
        room.setLayout(req.getLayout() == null ? "" : req.getLayout());
        room.setFileIds(req.getFileIds() == null ? "" : String.join(",", req.getFileIds()));
        room.setStatus(req.getStatus() == null || req.getStatus().isBlank() ? AVAILABLE : req.getStatus());
        room.setNeedApproval(Boolean.TRUE.equals(req.getNeedApproval()));
        room.setRemark(req.getRemark() == null ? "" : req.getRemark().trim());
    }

    private void replaceDevices(Long roomId, List<MeetingRequests.DeviceSave> devices) {
        deviceMapper.delete(new LambdaQueryWrapper<MeetingRoomDevice>().eq(MeetingRoomDevice::getRoomId, roomId));
        if (devices == null) {
            return;
        }
        for (MeetingRequests.DeviceSave d : devices) {
            if (d == null || d.getDeviceCode() == null || d.getDeviceCode().isBlank()) {
                continue;
            }
            MeetingRoomDevice entity = new MeetingRoomDevice();
            entity.setRoomId(roomId);
            entity.setDeviceCode(d.getDeviceCode().trim());
            entity.setQty(d.getQty() == null || d.getQty() < 1 ? 1 : d.getQty());
            entity.setRemark(d.getRemark() == null ? "" : d.getRemark().trim());
            entity.setCreatedAt(LocalDateTime.now());
            deviceMapper.insert(entity);
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        require(id);
        Long count = bookingMapper.selectCount(new LambdaQueryWrapper<MeetingBooking>()
                .eq(MeetingBooking::getRoomId, id));
        if (count != null && count > 0) {
            throw new BizException(MeetingErrorCode.ROOM_IN_USE);
        }
        deviceMapper.delete(new LambdaQueryWrapper<MeetingRoomDevice>().eq(MeetingRoomDevice::getRoomId, id));
        roomMapper.deleteById(id);
    }

    @Transactional(rollbackFor = Exception.class)
    public void changeStatus(Long id, String status) {
        if (status == null || status.isBlank()) {
            throw new BizException(ErrorCode.PARAM_ERROR, "状态不能为空");
        }
        MeetingRoom room = require(id);
        room.setStatus(status);
        room.setUpdatedAt(LocalDateTime.now());
        roomMapper.updateById(room);
    }

    /** 用户所在城市下的所有房间（预定页时间轴） */
    public List<MeetingRoom> roomsOfCity(String city) {
        return list(city, null, null, null, null, null, null, null);
    }
}
