package com.laimengchao.sso.meeting.module.meeting.service.impl;

import com.laimengchao.sso.meeting.module.meeting.service.LocationService;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.laimengchao.sso.common.BizException;
import com.laimengchao.sso.common.ErrorCode;
import com.laimengchao.sso.meeting.common.MeetingErrorCode;
import com.laimengchao.sso.meeting.module.meeting.dto.MeetingRequests;
import com.laimengchao.sso.meeting.module.meeting.dto.MeetingVOs;
import com.laimengchao.sso.meeting.module.meeting.entity.MeetingBuilding;
import com.laimengchao.sso.meeting.module.meeting.entity.MeetingFloor;
import com.laimengchao.sso.meeting.module.meeting.entity.MeetingRoom;
import com.laimengchao.sso.meeting.module.meeting.mapper.MeetingBuildingMapper;
import com.laimengchao.sso.meeting.module.meeting.mapper.MeetingFloorMapper;
import com.laimengchao.sso.meeting.module.meeting.mapper.MeetingRoomMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** 位置维护：城市只读复用字典，大楼 / 楼层严格按层级新增、按会议室占用锁定删除与停用。 */
@Service
@RequiredArgsConstructor
public class LocationServiceImpl implements LocationService {

    /** 与 settings 字典 meeting.location_status 的 value 保持一致 */
    public static final String ENABLED = "可用";
    public static final String DISABLED = "停用";

    private final MeetingBuildingMapper buildingMapper;
    private final MeetingFloorMapper floorMapper;
    private final MeetingRoomMapper roomMapper;

    public List<MeetingVOs.LocationVO> listBuildings(String city) {
        LambdaQueryWrapper<MeetingBuilding> qw = new LambdaQueryWrapper<MeetingBuilding>()
                .eq(city != null && !city.isBlank(), MeetingBuilding::getCity, city)
                .orderByAsc(MeetingBuilding::getSort).orderByAsc(MeetingBuilding::getId);
        List<MeetingBuilding> buildings = buildingMapper.selectList(qw);
        if (buildings.isEmpty()) {
            return List.of();
        }
        List<Long> buildingIds = buildings.stream().map(MeetingBuilding::getId).toList();
        List<MeetingFloor> floors = floorMapper.selectList(new LambdaQueryWrapper<MeetingFloor>()
                .in(MeetingFloor::getBuildingId, buildingIds));
        Map<Long, Long> floorCounts = floors.stream().collect(Collectors.groupingBy(
                MeetingFloor::getBuildingId, Collectors.counting()));
        Map<Long, Long> roomCountsByBuilding = new HashMap<>();
        if (!floors.isEmpty()) {
            List<Long> floorIds = floors.stream().map(MeetingFloor::getId).toList();
            List<MeetingRoom> rooms = roomMapper.selectList(new LambdaQueryWrapper<MeetingRoom>()
                    .in(MeetingRoom::getFloorId, floorIds)
                    .select(MeetingRoom::getId, MeetingRoom::getFloorId));
            Map<Long, Long> roomCountsByFloor = rooms.stream().collect(Collectors.groupingBy(
                    MeetingRoom::getFloorId, Collectors.counting()));
            for (MeetingFloor floor : floors) {
                Long bid = floor.getBuildingId();
                roomCountsByBuilding.merge(bid, roomCountsByFloor.getOrDefault(floor.getId(), 0L), Long::sum);
            }
        }
        Map<Long, List<MeetingVOs.LocationVO>> children = new HashMap<>();
        for (MeetingFloor floor : floors) {
            MeetingVOs.LocationVO vo = toFloorVO(floor);
            vo.setRoomCount(0); // 上层统一填入，此处避免重复查询
            children.computeIfAbsent(floor.getBuildingId(), k -> new ArrayList<>()).add(vo);
        }
        List<MeetingVOs.LocationVO> out = new ArrayList<>();
        for (MeetingBuilding b : buildings) {
            MeetingVOs.LocationVO vo = toBuildingVO(b);
            vo.setRoomCount(Math.toIntExact(roomCountsByBuilding.getOrDefault(b.getId(), 0L)));
            List<MeetingVOs.LocationVO> floorVOs = children.getOrDefault(b.getId(), List.of()).stream()
                    .sorted((a, c) -> {
                        MeetingFloor fa = findFloor(floors, Long.valueOf(a.getId()));
                        MeetingFloor fc = findFloor(floors, Long.valueOf(c.getId()));
                        int s = Integer.compare(fa.getSort() == null ? 0 : fa.getSort(),
                                fc.getSort() == null ? 0 : fc.getSort());
                        return s != 0 ? s : Long.compare(fa.getId(), fc.getId());
                    }).toList();
            floorVOs.forEach(f -> f.setRoomCount(Math.toIntExact(roomCountOfFloor(floors, Long.valueOf(f.getId())))));
            vo.setChildren(floorVOs);
            out.add(vo);
        }
        return out;
    }

    private MeetingFloor findFloor(List<MeetingFloor> floors, Long id) {
        return floors.stream().filter(f -> f.getId().equals(id)).findFirst().orElseThrow();
    }

    private long roomCountOfFloor(List<MeetingFloor> floors, Long floorId) {
        return roomMapper.selectCount(new LambdaQueryWrapper<MeetingRoom>()
                .eq(MeetingRoom::getFloorId, floorId));
    }

    private MeetingVOs.LocationVO toBuildingVO(MeetingBuilding b) {
        MeetingVOs.LocationVO vo = new MeetingVOs.LocationVO();
        vo.setId(String.valueOf(b.getId()));
        vo.setName(b.getName());
        vo.setCity(b.getCity());
        vo.setAddress(b.getAddress());
        vo.setSort(b.getSort());
        vo.setStatus(b.getStatus());
        vo.setRemark(b.getRemark());
        return vo;
    }

    private MeetingVOs.LocationVO toFloorVO(MeetingFloor f) {
        MeetingVOs.LocationVO vo = new MeetingVOs.LocationVO();
        vo.setId(String.valueOf(f.getId()));
        vo.setName(f.getFloorNo());
        vo.setFloorNo(f.getFloorNo());
        vo.setBuildingId(f.getBuildingId());
        vo.setSort(f.getSort());
        vo.setStatus(f.getStatus());
        vo.setRemark(f.getRemark());
        return vo;
    }

    public MeetingBuilding requireBuilding(Long id) {
        MeetingBuilding b = id == null ? null : buildingMapper.selectById(id);
        if (b == null) {
            throw new BizException(MeetingErrorCode.LOCATION_NOT_FOUND);
        }
        return b;
    }

    public MeetingFloor requireFloor(Long id) {
        MeetingFloor f = id == null ? null : floorMapper.selectById(id);
        if (f == null) {
            throw new BizException(MeetingErrorCode.LOCATION_NOT_FOUND);
        }
        return f;
    }

    @Transactional(rollbackFor = Exception.class)
    public MeetingBuilding saveBuilding(String city, MeetingRequests.BuildingSave req) {
        if (city == null || city.isBlank()) {
            throw new BizException(ErrorCode.PARAM_ERROR, "请先选择城市");
        }
        if (req.getName() == null || req.getName().isBlank()) {
            throw new BizException(ErrorCode.PARAM_ERROR, "大楼名称不能为空");
        }
        MeetingBuilding b = new MeetingBuilding();
        b.setCity(city.trim());
        b.setName(req.getName().trim());
        b.setAddress(req.getAddress() == null ? "" : req.getAddress().trim());
        b.setSort(req.getSort() == null ? 0 : req.getSort());
        b.setStatus(req.getStatus() == null || req.getStatus().isBlank() ? ENABLED : req.getStatus());
        b.setRemark(req.getRemark() == null ? "" : req.getRemark().trim());
        b.setCreatedAt(LocalDateTime.now());
        buildingMapper.insert(b);
        return b;
    }

    @Transactional(rollbackFor = Exception.class)
    public void updateBuilding(Long id, MeetingRequests.BuildingSave req) {
        MeetingBuilding b = requireBuilding(id);
        if (DISABLED.equals(req.getStatus())) {
            assertNoRoomsInBuilding(id);
        }
        b.setName(req.getName() == null ? b.getName() : req.getName().trim());
        b.setAddress(req.getAddress() == null ? b.getAddress() : req.getAddress().trim());
        b.setSort(req.getSort() == null ? b.getSort() : req.getSort());
        if (req.getStatus() != null && !req.getStatus().isBlank()) {
            b.setStatus(req.getStatus());
        }
        if (req.getRemark() != null) {
            b.setRemark(req.getRemark().trim());
        }
        b.setUpdatedAt(LocalDateTime.now());
        buildingMapper.updateById(b);
    }

    @Transactional(rollbackFor = Exception.class)
    public void deleteBuilding(Long id) {
        requireBuilding(id);
        if (floorMapper.selectCount(new LambdaQueryWrapper<MeetingFloor>().eq(MeetingFloor::getBuildingId, id)) > 0) {
            throw new BizException(MeetingErrorCode.LOCATION_HAS_FLOOR);
        }
        assertNoRoomsInBuilding(id);
        buildingMapper.deleteById(id);
    }

    private void assertNoRoomsInBuilding(Long buildingId) {
        List<MeetingFloor> floors = floorMapper.selectList(new LambdaQueryWrapper<MeetingFloor>()
                .eq(MeetingFloor::getBuildingId, buildingId));
        if (floors.isEmpty()) {
            return;
        }
        Long rooms = roomMapper.selectCount(new LambdaQueryWrapper<MeetingRoom>()
                .in(MeetingRoom::getFloorId, floors.stream().map(MeetingFloor::getId).toList()));
        if (rooms != null && rooms > 0) {
            throw new BizException(MeetingErrorCode.LOCATION_HAS_ROOM);
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public MeetingFloor saveFloor(Long buildingId, MeetingRequests.FloorSave req) {
        requireBuilding(buildingId);
        if (req.getFloorNo() == null || req.getFloorNo().isBlank()) {
            throw new BizException(ErrorCode.PARAM_ERROR, "楼层号不能为空");
        }
        MeetingFloor f = new MeetingFloor();
        f.setBuildingId(buildingId);
        f.setFloorNo(req.getFloorNo().trim());
        f.setSort(req.getSort() == null ? 0 : req.getSort());
        f.setStatus(req.getStatus() == null || req.getStatus().isBlank() ? ENABLED : req.getStatus());
        f.setRemark(req.getRemark() == null ? "" : req.getRemark().trim());
        f.setCreatedAt(LocalDateTime.now());
        floorMapper.insert(f);
        return f;
    }

    @Transactional(rollbackFor = Exception.class)
    public void updateFloor(Long id, MeetingRequests.FloorSave req) {
        MeetingFloor f = requireFloor(id);
        if (DISABLED.equals(req.getStatus())) {
            assertNoRoomsInFloor(id);
        }
        f.setFloorNo(req.getFloorNo() == null ? f.getFloorNo() : req.getFloorNo().trim());
        f.setSort(req.getSort() == null ? f.getSort() : req.getSort());
        if (req.getStatus() != null && !req.getStatus().isBlank()) {
            f.setStatus(req.getStatus());
        }
        if (req.getRemark() != null) {
            f.setRemark(req.getRemark().trim());
        }
        f.setUpdatedAt(LocalDateTime.now());
        floorMapper.updateById(f);
    }

    @Transactional(rollbackFor = Exception.class)
    public void deleteFloor(Long id) {
        requireFloor(id);
        assertNoRoomsInFloor(id);
        floorMapper.deleteById(id);
    }

    private void assertNoRoomsInFloor(Long floorId) {
        Long rooms = roomMapper.selectCount(new LambdaQueryWrapper<MeetingRoom>()
                .eq(MeetingRoom::getFloorId, floorId));
        if (rooms != null && rooms > 0) {
            throw new BizException(MeetingErrorCode.LOCATION_HAS_ROOM);
        }
    }

    /** 按城市拿所有可用楼层（预定选房三级筛选） */
    public List<MeetingVOs.LocationVO> floorsOfCity(String city) {
        List<MeetingVOs.LocationVO> out = new ArrayList<>();
        for (MeetingVOs.LocationVO building : listBuildings(city)) {
            out.addAll(building.getChildren() == null ? List.of() : building.getChildren());
        }
        return out;
    }

}
