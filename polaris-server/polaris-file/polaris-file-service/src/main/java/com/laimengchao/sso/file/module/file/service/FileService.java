package com.laimengchao.sso.file.module.file.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.laimengchao.sso.common.BizException;
import com.laimengchao.sso.common.PageResult;
import com.laimengchao.sso.file.common.FileErrorCode;
import com.laimengchao.sso.file.module.file.entity.FileObject;
import com.laimengchao.sso.file.module.file.entity.StoragePolicy;
import com.laimengchao.sso.file.module.file.mapper.FileObjectMapper;
import com.laimengchao.sso.file.module.file.mapper.StoragePolicyMapper;
import com.laimengchao.sso.utils.IdUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** 文件元数据与存储的核心逻辑。 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FileService {

    private static final String STATUS_NORMAL = "NORMAL";
    private static final String STATUS_DELETED = "DELETED";
    private static final String VISIBILITY_PUBLIC = "PUBLIC";

    private final FileObjectMapper fileMapper;
    private final StoragePolicyMapper policyMapper;
    private final LocalStorageService storage;
    private final SignUrlService signer;

    /** 上传：校验策略 → 落盘 → 写元数据，返回带访问 URL 的元数据。 */
    @Transactional(rollbackFor = Exception.class)
    public FileObject upload(String bizType, String visibility, String originalName, String contentType,
                             byte[] bytes, String ownerType, String ownerId, Long uploaderId) {
        StoragePolicy policy = policy(bizType);
        if (bytes.length > policy.getMaxSizeMb() * 1024 * 1024) {
            log.warn("上传被拒：文件超限 bizType={} name={} size={} maxMb={}",
                    bizType, originalName, bytes.length, policy.getMaxSizeMb());
            throw new BizException(FileErrorCode.FILE_TOO_LARGE,
                    "文件大小超出上限 " + policy.getMaxSizeMb() + "MB");
        }
        if (policy.getAllowedExt() != null && !policy.getAllowedExt().isBlank()) {
            String ext = extension(originalName);
            boolean allowed = Arrays.stream(policy.getAllowedExt().split(","))
                    .map(String::trim).map(s -> s.toLowerCase(Locale.ROOT))
                    .anyMatch(e -> e.equals(ext));
            if (!allowed) {
                log.warn("上传被拒：扩展名不允许 bizType={} name={} ext={}", bizType, originalName, ext);
                throw new BizException(FileErrorCode.EXT_NOT_ALLOWED,
                        "仅允许 " + policy.getAllowedExt());
            }
        }
        String finalVisibility = Boolean.TRUE.equals(policy.getForcePrivate())
                ? "PRIVATE"
                : (visibility == null || visibility.isBlank()
                    ? policy.getDefaultVisibility() : visibility.toUpperCase(Locale.ROOT));

        String path = storage.write(bytes, originalName);
        try {
            FileObject obj = new FileObject();
            obj.setId(IdUtils.nextId());
            obj.setBizType(bizType);
            obj.setVisibility(finalVisibility);
            obj.setOriginalName(originalName);
            obj.setContentType(contentType);
            obj.setSize((long) bytes.length);
            obj.setSha256(sha256(bytes));
            obj.setStoragePath(path);
            obj.setOwnerType(ownerType);
            obj.setOwnerId(ownerId);
            obj.setUploaderId(uploaderId == null ? 0L : uploaderId);
            obj.setStatus(STATUS_NORMAL);
            obj.setCreatedAt(LocalDateTime.now());
            fileMapper.insert(obj);
            log.info("文件上传成功：fileId={} bizType={} visibility={} size={} path={}",
                    obj.getId(), bizType, finalVisibility, bytes.length, path);
            return withUrl(obj, null);
        } catch (RuntimeException e) {
            // 元数据写库失败，补偿删除已落盘字节，避免产生孤儿文件
            log.error("文件上传写元数据失败，已回滚落盘文件：bizType={} name={} path={}",
                    bizType, originalName, path, e);
            storage.delete(path);
            throw e;
        }
    }

    /** 查询单个元数据；不存在或已软删返回 null。 */
    public FileObject meta(String id) {
        Long lid = parseId(id);
        if (lid == null) {
            return null;
        }
        FileObject obj = fileMapper.selectById(lid);
        if (obj == null || !STATUS_NORMAL.equals(obj.getStatus())) {
            return null;
        }
        return withUrl(obj, null);
    }

    public List<FileObject> metas(String ids) {
        if (ids == null || ids.isBlank()) {
            return Collections.emptyList();
        }
        List<Long> parsed = Arrays.stream(ids.split(","))
                .map(String::trim).map(this::parseId)
                .filter(java.util.Objects::nonNull).toList();
        if (parsed.isEmpty()) {
            return Collections.emptyList();
        }
        List<FileObject> list = fileMapper.selectBatchIds(parsed).stream()
                .filter(o -> STATUS_NORMAL.equals(o.getStatus())).toList();
        return list.stream().map(o -> withUrl(o, null)).toList();
    }

    /** 读取文件内容。校验签名由控制器完成，这里只保证文件存在。 */
    public byte[] content(String id) {
        FileObject obj = require(id);
        byte[] bytes = storage.read(obj.getStoragePath());
        if (bytes == null) {
            throw new BizException(FileErrorCode.FILE_NOT_FOUND);
        }
        return bytes;
    }

    /** 生成签名 URL。 */
    public String[] sign(String id, String purpose, Long expiresSeconds) {
        FileObject obj = require(id);
        String normalizedPurpose = "DOWNLOAD".equals(purpose) ? "DOWNLOAD" : "VIEW";
        return signer.sign(String.valueOf(obj.getId()), normalizedPurpose, expiresSeconds);
    }

    /** 软删除：仅改元数据状态，字节由孤儿清理任务延后物理删除。 */
    @Transactional(rollbackFor = Exception.class)
    public void delete(String id) {
        FileObject obj = require(id);
        obj.setStatus(STATUS_DELETED);
        obj.setDeletedAt(LocalDateTime.now());
        fileMapper.updateById(obj);
    }

    /** 管理端分页。 */
    public PageResult<FileObject> page(int page, int size, String bizType, String visibility, String keyword) {
        LambdaQueryWrapper<FileObject> w = new LambdaQueryWrapper<>();
        w.eq(FileObject::getStatus, STATUS_NORMAL);
        if (bizType != null && !bizType.isBlank()) {
            w.eq(FileObject::getBizType, bizType.trim());
        }
        if (visibility != null && !visibility.isBlank()) {
            w.eq(FileObject::getVisibility, visibility.trim().toUpperCase(Locale.ROOT));
        }
        if (keyword != null && !keyword.isBlank()) {
            w.like(FileObject::getOriginalName, keyword.trim());
        }
        w.orderByDesc(FileObject::getCreatedAt);
        IPage<FileObject> p = fileMapper.selectPage(new Page<>(page, size), w);
        return PageResult.of(p.getRecords().stream().map(o -> withUrl(o, null)).toList(),
                p.getTotal(), page, size);
    }

    public List<StoragePolicy> policies() {
        return policyMapper.selectList(new LambdaQueryWrapper<StoragePolicy>()
                .orderByAsc(StoragePolicy::getBizType));
    }

    public void savePolicy(StoragePolicy policy) {
        String bizType = policy.getBizType();
        if (bizType == null || !bizType.matches("[a-z][a-z0-9_]{1,63}")) {
            throw new BizException(FileErrorCode.POLICY_INVALID,
                    "业务类型须以小写字母开头，仅含小写字母/数字/下划线，长度 2-64");
        }
        if (policy.getMaxSizeMb() == null || policy.getMaxSizeMb() <= 0) {
            throw new BizException(FileErrorCode.POLICY_INVALID, "大小上限须大于 0");
        }
        String visibility = policy.getDefaultVisibility();
        if (!"PUBLIC".equals(visibility) && !"PRIVATE".equals(visibility)) {
            throw new BizException(FileErrorCode.POLICY_INVALID, "默认可见性须为 PUBLIC 或 PRIVATE");
        }
        StoragePolicy exists = policyMapper.selectOne(new LambdaQueryWrapper<StoragePolicy>()
                .eq(StoragePolicy::getBizType, bizType).last("limit 1"));
        if (policy.getId() == null) {
            if (exists != null) {
                throw new BizException(FileErrorCode.POLICY_EXISTS, "业务类型已存在：" + bizType);
            }
            policy.setCreatedAt(LocalDateTime.now());
            policyMapper.insert(policy);
        } else {
            if (exists != null && !exists.getId().equals(policy.getId())) {
                throw new BizException(FileErrorCode.POLICY_EXISTS, "业务类型已存在：" + bizType);
            }
            policy.setUpdatedAt(LocalDateTime.now());
            policyMapper.updateById(policy);
        }
    }

    /** 存储概览统计：总量、可见性分布、业务类型分布、近 10 天上传趋势、最近上传。 */
    public java.util.Map<String, Object> overview() {
        LocalDate today = LocalDate.now();
        long total = fileMapper.selectCount(new LambdaQueryWrapper<FileObject>()
                .eq(FileObject::getStatus, STATUS_NORMAL));
        long publicCount = fileMapper.selectCount(new LambdaQueryWrapper<FileObject>()
                .eq(FileObject::getStatus, STATUS_NORMAL)
                .eq(FileObject::getVisibility, VISIBILITY_PUBLIC));
        long privateCount = total - publicCount;
        Long sum = fileMapper.selectList(new LambdaQueryWrapper<FileObject>()
                        .eq(FileObject::getStatus, STATUS_NORMAL).select(FileObject::getSize)).stream()
                .map(FileObject::getSize).filter(java.util.Objects::nonNull)
                .mapToLong(Long::longValue).sum();
        long todayCount = fileMapper.selectCount(new LambdaQueryWrapper<FileObject>()
                .eq(FileObject::getStatus, STATUS_NORMAL)
                .ge(FileObject::getCreatedAt, today.atStartOfDay()));

        java.util.List<java.util.Map<String, Object>> byBizType = fileMapper.selectMaps(
                new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<FileObject>()
                        .select("biz_type AS bizType, COUNT(*) AS cnt, COALESCE(SUM(size),0) AS bytes")
                        .eq("status", STATUS_NORMAL)
                        .groupBy("biz_type").orderByDesc("cnt"));
        java.util.List<java.util.Map<String, Object>> trend = fileMapper.selectMaps(
                new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<FileObject>()
                        .select("DATE(created_at) AS day, COUNT(*) AS cnt")
                        .eq("status", STATUS_NORMAL)
                        .ge("created_at", today.minusDays(9).atStartOfDay())
                        .groupBy("DATE(created_at)").orderByAsc("DATE(created_at)"));
        java.util.List<FileObject> recent = fileMapper.selectList(new LambdaQueryWrapper<FileObject>()
                .eq(FileObject::getStatus, STATUS_NORMAL)
                .orderByDesc(FileObject::getCreatedAt).last("limit 5"));

        java.util.Map<String, Object> map = new java.util.HashMap<>();
        map.put("total", total);
        map.put("publicCount", publicCount);
        map.put("privateCount", privateCount);
        map.put("totalBytes", sum);
        map.put("todayCount", todayCount);
        map.put("byBizType", byBizType);
        map.put("trend", trend);
        map.put("recent", recent);
        return map;
    }

    /**
     * 按天统计上传量（抽屉自定义时间范围用）。
     * 日期含首尾，缺失的日期补 0；范围上限 366 天，避免一次扫描过大。
     */
    public List<Map<String, Object>> uploadTrend(String startDate, String endDate) {
        LocalDate start;
        LocalDate end;
        if (startDate == null || startDate.isBlank() || endDate == null || endDate.isBlank()) {
            throw new BizException(FileErrorCode.STATS_PARAM_INVALID, "请选择开始和结束日期");
        }
        try {
            start = LocalDate.parse(startDate);
            end = LocalDate.parse(endDate);
        } catch (DateTimeParseException e) {
            throw new BizException(FileErrorCode.STATS_PARAM_INVALID, "日期格式应为 yyyy-MM-dd");
        }
        if (end.isBefore(start)) {
            LocalDate swap = start;
            start = end;
            end = swap;
        }
        if (ChronoUnit.DAYS.between(start, end) >= 366) {
            throw new BizException(FileErrorCode.STATS_PARAM_INVALID, "统计范围不能超过 366 天");
        }
        List<Map<String, Object>> rows = fileMapper.selectMaps(
                new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<FileObject>()
                        .select("DATE(created_at) AS day, COUNT(*) AS cnt, COALESCE(SUM(size),0) AS bytes")
                        .eq("status", STATUS_NORMAL)
                        .ge("created_at", start.atStartOfDay())
                        .lt("created_at", end.plusDays(1).atStartOfDay())
                        .groupBy("DATE(created_at)").orderByAsc("DATE(created_at)"));
        Map<String, Map<String, Object>> byDay = new LinkedHashMap<>();
        for (Map<String, Object> row : rows) {
            Object day = row.get("day");
            String key = day == null ? "" : day.toString();
            byDay.put(key, row);
        }
        List<Map<String, Object>> result = new java.util.ArrayList<>();
        for (LocalDate d = start; !d.isAfter(end); d = d.plusDays(1)) {
            String key = d.toString();
            Map<String, Object> row = byDay.get(key);
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("day", key);
            item.put("cnt", row == null ? 0L : row.get("cnt"));
            item.put("bytes", row == null ? 0L : row.get("bytes"));
            result.add(item);
        }
        return result;
    }

    private StoragePolicy policy(String bizType) {
        StoragePolicy p = policyMapper.selectOne(new LambdaQueryWrapper<StoragePolicy>()
                .eq(StoragePolicy::getBizType, bizType)
                .eq(StoragePolicy::getEnabled, true)
                .last("limit 1"));
        if (p == null) {
            throw new BizException(FileErrorCode.POLICY_NOT_FOUND, "业务类型：" + bizType);
        }
        return p;
    }

    private FileObject require(String id) {
        Long lid = parseId(id);
        FileObject obj = lid == null ? null : fileMapper.selectById(lid);
        if (obj == null || !STATUS_NORMAL.equals(obj.getStatus())) {
            throw new BizException(FileErrorCode.FILE_NOT_FOUND);
        }
        return obj;
    }

    private Long parseId(String id) {
        if (id == null || id.isBlank()) {
            return null;
        }
        try {
            return Long.valueOf(id.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private FileObject withUrl(FileObject obj, String purpose) {
        String[] signed = signer.sign(String.valueOf(obj.getId()),
                purpose == null ? "VIEW" : purpose, null);
        obj.setUrl(signed[0]);
        return obj;
    }

    private String extension(String name) {
        if (name == null) {
            return "";
        }
        int dot = name.lastIndexOf('.');
        return dot < 0 || dot == name.length() - 1
                ? "" : name.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    private String sha256(byte[] bytes) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(bytes);
            StringBuilder sb = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            return "";
        }
    }
}
