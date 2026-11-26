package com.laimengchao.sso.file.module.file.service.impl;
import com.laimengchao.sso.file.module.file.service.FileService;
import com.laimengchao.sso.file.module.file.service.AttachmentSettingService;
import com.laimengchao.sso.file.module.file.service.SignUrlService;
import com.laimengchao.sso.file.module.file.service.StorageBackend;
import com.laimengchao.sso.file.module.file.service.ThumbnailService;

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
public class FileServiceImpl implements FileService {

    private static final String STATUS_NORMAL = "NORMAL";
    private static final String STATUS_DELETED = "DELETED";
    private static final String VISIBILITY_PUBLIC = "PUBLIC";

    private final FileObjectMapper fileMapper;
    private final StoragePolicyMapper policyMapper;
    private final StorageBackend storage;
    private final SignUrlService signer;
    private final AttachmentSettingService settings;

    /** 上传：校验策略 → 内容去重/落盘 → 写元数据，返回带访问 URL 的元数据。 */
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

        // 秒传去重：同摘要 + 同大小的正常文件已存在时，复用其物理字节，只新增元数据。
        // 可通过“附件设置”关闭（dedup.enabled）；关闭后每次上传都独立落盘。
        String sha = sha256(bytes);
        FileObject dup = null;
        boolean dedupEnabled = settings.bool(AttachmentSettingServiceImpl.DEDUP_ENABLED, true);
        if (dedupEnabled && !sha.isEmpty()) {
            dup = fileMapper.selectOne(new LambdaQueryWrapper<FileObject>()
                    .eq(FileObject::getSha256, sha)
                    .eq(FileObject::getSize, (long) bytes.length)
                    .eq(FileObject::getStatus, STATUS_NORMAL)
                    .last("limit 1"));
        }
        boolean deduplicated = dup != null;
        // 业务配额：仅真实新增字节时占用配额（秒传复用不增加物理占用，跳过校验）
        if (!deduplicated) {
            checkQuota(policy, bizType, bytes.length);
        }
        String path = deduplicated ? dup.getStoragePath() : storage.write(bytes, originalName);
        if (deduplicated) {
            log.info("秒传命中：复用字节 path={} 来源 fileId={} bizType={} sha={}",
                    path, dup.getId(), bizType, sha);
        }
        try {
            FileObject obj = new FileObject();
            obj.setId(IdUtils.nextId());
            obj.setBizType(bizType);
            obj.setVisibility(finalVisibility);
            obj.setOriginalName(originalName);
            obj.setContentType(contentType);
            obj.setSize((long) bytes.length);
            obj.setSha256(sha);
            obj.setStoragePath(path);
            obj.setOwnerType(ownerType);
            obj.setOwnerId(ownerId);
            obj.setUploaderId(uploaderId == null ? 0L : uploaderId);
            obj.setStatus(STATUS_NORMAL);
            obj.setCreatedAt(LocalDateTime.now());
            obj.setDeduplicated(deduplicated);
            fileMapper.insert(obj);
            log.info("文件上传成功：fileId={} bizType={} visibility={} size={} path={} dedup={}",
                    obj.getId(), bizType, finalVisibility, bytes.length, path, deduplicated);
            return withUrl(obj, null);
        } catch (RuntimeException e) {
            // 元数据写库失败：仅当本次确实落盘新字节时才补偿删除，避免删掉被秒传复用的共享字节
            if (!deduplicated) {
                log.error("文件上传写元数据失败，已回滚落盘文件：bizType={} name={} path={}",
                        bizType, originalName, path, e);
                storage.delete(path);
            } else {
                log.error("文件上传写元数据失败（秒传复用字节，保留共享文件）：bizType={} name={} path={}",
                        bizType, originalName, path, e);
            }
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

    /** 软删除：仅改元数据状态，字节保留（秒传可能共享，物理删除走 purge 并做引用检查）。 */
    @Transactional(rollbackFor = Exception.class)
    public void delete(String id) {
        FileObject obj = require(id);
        obj.setStatus(STATUS_DELETED);
        obj.setDeletedAt(LocalDateTime.now());
        fileMapper.updateById(obj);
    }

    /** 回收站分页：查询已软删的文件。 */
    public PageResult<FileObject> recycleBin(int page, int size, String bizType, String keyword) {
        LambdaQueryWrapper<FileObject> w = new LambdaQueryWrapper<>();
        w.eq(FileObject::getStatus, STATUS_DELETED);
        if (bizType != null && !bizType.isBlank()) {
            w.eq(FileObject::getBizType, (bizType == null ? null : bizType.trim()));
        }
        if (keyword != null && !keyword.isBlank()) {
            w.like(FileObject::getOriginalName, keyword.trim());
        }
        w.orderByDesc(FileObject::getDeletedAt);
        IPage<FileObject> p = fileMapper.selectPage(new Page<>(page, size), w);
        return PageResult.of(p.getRecords().stream().map(o -> withUrl(o, null)).toList(),
                p.getTotal(), page, size);
    }

    /** 从回收站恢复：校验处于 DELETED 且物理字节仍在。 */
    @Transactional(rollbackFor = Exception.class)
    public void restore(String id) {
        Long lid = parseId(id);
        FileObject obj = lid == null ? null : fileMapper.selectById(lid);
        if (obj == null || !STATUS_DELETED.equals(obj.getStatus())) {
            throw new BizException(FileErrorCode.FILE_NOT_DELETED);
        }
        if (!storage.exists(obj.getStoragePath())) {
            log.error("恢复失败：物理字节已丢失 fileId={} path={}", id, obj.getStoragePath());
            throw new BizException(FileErrorCode.FILE_RESTORE_PATH_LOST);
        }
        obj.setStatus(STATUS_NORMAL);
        obj.setDeletedAt(null);
        fileMapper.updateById(obj);
        log.info("文件已从回收站恢复：fileId={} path={}", id, obj.getStoragePath());
    }

    /**
     * 彻底删除：物理删字节 + 删元数据。秒传场景下多个元数据可能共享同一字节，
     * 若仍有其它记录（正常或回收站）引用该 path，则只删本条元数据，保留物理文件。
     */
    @Transactional(rollbackFor = Exception.class)
    public void purge(String id) {
        Long lid = parseId(id);
        FileObject obj = lid == null ? null : fileMapper.selectById(lid);
        if (obj == null || !STATUS_DELETED.equals(obj.getStatus())) {
            throw new BizException(FileErrorCode.FILE_NOT_DELETED);
        }
        String path = obj.getStoragePath();
        boolean referenced = fileMapper.selectCount(new LambdaQueryWrapper<FileObject>()
                .eq(FileObject::getStoragePath, path)
                .ne(FileObject::getId, lid)) > 0;
        if (referenced) {
            log.info("彻底删除：物理字节仍被其它记录引用，仅删元数据 fileId={} path={}", id, path);
        } else if (!storage.delete(path)) {
            log.warn("彻底删除：物理字节不存在或删除失败 fileId={} path={}", id, path);
        }
        fileMapper.deleteById(lid);
        log.info("文件已彻底删除：fileId={} path={} physicalDeleted={}", id, path, !referenced);
    }

    /**
     * 生命周期清理：按各业务策略 ttlDays，把超期的 NORMAL 文件移入回收站（软删，可恢复）。
     * 由定时任务调用；返回本轮处理条数。
     */
    @Transactional(rollbackFor = Exception.class)
    public int expireByTtl() {
        List<StoragePolicy> policies = policies().stream()
                .filter(p -> p.getTtlDays() != null && p.getTtlDays() > 0).toList();
        int total = 0;
        LocalDateTime now = LocalDateTime.now();
        for (StoragePolicy p : policies) {
            LocalDateTime cutoff = now.minusDays(p.getTtlDays());
            List<FileObject> expired = fileMapper.selectList(new LambdaQueryWrapper<FileObject>()
                    .eq(FileObject::getStatus, STATUS_NORMAL)
                    .eq(FileObject::getBizType, p.getBizType())
                    .lt(FileObject::getCreatedAt, cutoff));
            for (FileObject obj : expired) {
                obj.setStatus(STATUS_DELETED);
                obj.setDeletedAt(now);
                fileMapper.updateById(obj);
                total++;
            }
            if (!expired.isEmpty()) {
                log.info("生命周期清理：bizType={} ttlDays={} 移入回收站 {} 条",
                        p.getBizType(), p.getTtlDays(), expired.size());
            }
        }
        return total;
    }

    /**
     * 回收站自动清理：软删超过“附件设置”recycle.keepDays 的文件物理删除（复用秒传引用检查）。
     * keepDays<=0 表示保留、不清理。返回本轮清理条数。
     */
    public int purgeExpiredRecycle() {
        int keepDays = settings.integer(AttachmentSettingServiceImpl.RECYCLE_KEEP_DAYS, 0);
        if (keepDays <= 0) {
            return 0;
        }
        LocalDateTime cutoff = LocalDateTime.now().minusDays(keepDays);
        List<FileObject> expired = fileMapper.selectList(new LambdaQueryWrapper<FileObject>()
                .eq(FileObject::getStatus, STATUS_DELETED)
                .lt(FileObject::getDeletedAt, cutoff));
        int total = 0;
        for (FileObject obj : expired) {
            try {
                purge(String.valueOf(obj.getId()));
                total++;
            } catch (RuntimeException e) {
                log.warn("回收站自动清理失败：fileId={} {}", obj.getId(), e.getMessage());
            }
        }
        if (total > 0) {
            log.info("回收站自动清理：keepDays={} 物理删除 {} 条", keepDays, total);
        }
        return total;
    }

    /** 管理端分页。 */
    public PageResult<FileObject> page(int page, int size, String bizType, String visibility, String keyword) {
        LambdaQueryWrapper<FileObject> w = new LambdaQueryWrapper<>();
        w.eq(FileObject::getStatus, STATUS_NORMAL);
        if (bizType != null && !bizType.isBlank()) {
            w.eq(FileObject::getBizType, (bizType == null ? null : bizType.trim()));
        }
        if (visibility != null && !visibility.isBlank()) {
            w.eq(FileObject::getVisibility, visibility.trim().toUpperCase(Locale.ROOT));
        }
        if (keyword != null && !keyword.isBlank()) {
            w.like(FileObject::getOriginalName, (keyword == null ? null : keyword.trim()));
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

    /** 业务总配额校验：按 bizType 汇总 NORMAL 文件字节，超过 quotaGb 拒绝新增。 */
    private void checkQuota(StoragePolicy policy, String bizType, int incomingBytes) {
        Long quotaGb = policy.getQuotaGb();
        if (quotaGb == null || quotaGb <= 0) {
            return;
        }
        Long used = fileMapper.selectList(new LambdaQueryWrapper<FileObject>()
                        .eq(FileObject::getStatus, STATUS_NORMAL)
                        .eq(FileObject::getBizType, bizType)
                        .select(FileObject::getSize)).stream()
                .map(FileObject::getSize).filter(java.util.Objects::nonNull)
                .mapToLong(Long::longValue).sum();
        long limit = quotaGb * 1024L * 1024L * 1024L;
        if (used + incomingBytes > limit) {
            log.warn("上传被拒：业务配额超限 bizType={} used={} incoming={} limit={}",
                    bizType, used, incomingBytes, limit);
            throw new BizException(FileErrorCode.QUOTA_EXCEEDED,
                    "业务「" + bizType + "」存储配额已用尽（上限 " + quotaGb + "GB）");
        }
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
        // 位图图片额外给一个缩略图 URL：签名串不覆盖 w 参数，追加后校验仍通过
        if (ThumbnailService.isRasterImage(obj.getContentType())) {
            int edge = settings.integer(AttachmentSettingServiceImpl.THUMBNAIL_MAX_EDGE, 320);
            edge = Math.max(32, Math.min(edge, 1200));
            obj.setThumbUrl(signed[0] + "&w=" + edge);
        }
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
