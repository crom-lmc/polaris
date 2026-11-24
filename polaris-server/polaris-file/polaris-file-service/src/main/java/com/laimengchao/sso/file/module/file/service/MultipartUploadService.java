package com.laimengchao.sso.file.module.file.service;

import com.laimengchao.sso.common.BizException;
import com.laimengchao.sso.file.common.FileErrorCode;
import com.laimengchao.sso.file.config.FileProperties;
import com.laimengchao.sso.file.module.file.entity.FileObject;
import com.laimengchao.sso.file.module.file.vo.MultipartInitRequest;
import com.laimengchao.sso.utils.IdUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 分片上传：init → 逐片 part → complete。会话存内存，分片字节落临时目录，
 * complete 时按序合并为整段字节后交给 FileService.upload（复用其策略校验与秒传去重）。
 * <p>
 * 会话有 TTL，读取/写入时顺带惰性清理过期会话及其临时目录。重启后会话丢失属预期，
 * 残留临时目录由 @Scheduled 兜底清理（见 cleanupExpired）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MultipartUploadService {

    /** 单片默认 5MB */
    public static final int DEFAULT_CHUNK_SIZE = 5 * 1024 * 1024;

    private final FileProperties props;
    private final FileService fileService;

    private final Map<String, Session> sessions = new ConcurrentHashMap<>();

    /** 初始化上传会话，返回 uploadId / chunkSize / 期望分片数。 */
    public Map<String, Object> init(MultipartInitRequest req) {
        if (req.getTotalSize() == null || req.getTotalSize() <= 0) {
            throw new BizException(FileErrorCode.MULTIPART_INDEX_INVALID, "文件大小必须大于 0");
        }
        cleanupExpired();
        String uploadId = UUID.randomUUID().toString().replace("-", "");
        int chunks = (int) ((req.getTotalSize() + DEFAULT_CHUNK_SIZE - 1) / DEFAULT_CHUNK_SIZE);
        Session s = new Session();
        s.id = uploadId;
        s.req = req;
        s.totalChunks = chunks;
        s.received = new boolean[chunks];
        s.dir = tempDir().resolve(uploadId);
        s.updatedAt = Instant.now();
        try {
            Files.createDirectories(s.dir);
        } catch (IOException e) {
            log.error("创建分片临时目录失败 uploadId={} dir={}", uploadId, s.dir, e);
            throw new BizException(FileErrorCode.STORAGE_ERROR);
        }
        sessions.put(uploadId, s);
        log.info("分片上传初始化：uploadId={} totalSize={} chunks={} bizType={}",
                uploadId, req.getTotalSize(), chunks, req.getBizType());
        return Map.of(
                "uploadId", uploadId,
                "chunkSize", DEFAULT_CHUNK_SIZE,
                "totalChunks", chunks
        );
    }

    /** 上传单片。index 从 0 开始；重复上传同片以最后一次为准（幂等覆盖）。 */
    public void part(String uploadId, int index, String base64) {
        Session s = requireSession(uploadId);
        if (index < 0 || index >= s.totalChunks) {
            throw new BizException(FileErrorCode.MULTIPART_INDEX_INVALID);
        }
        byte[] bytes;
        try {
            bytes = Base64.getDecoder().decode(base64 == null ? "" : base64);
        } catch (IllegalArgumentException e) {
            throw new BizException(FileErrorCode.BAD_BASE64);
        }
        Path partFile = s.dir.resolve(String.format("part_%06d", index));
        try {
            Files.write(partFile, bytes, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        } catch (IOException e) {
            log.error("写入分片失败 uploadId={} index={}", uploadId, index, e);
            throw new BizException(FileErrorCode.STORAGE_ERROR);
        }
        s.received[index] = true;
        s.updatedAt = Instant.now();
    }

    /** 合并所有分片并走正常上传链路；成功后清理会话与临时目录。 */
    public FileObject complete(String uploadId, Long uploaderId) {
        Session s = requireSession(uploadId);
        for (int i = 0; i < s.totalChunks; i++) {
            if (!s.received[i]) {
                throw new BizException(FileErrorCode.MULTIPART_INCOMPLETE, "缺少分片：" + i);
            }
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            for (int i = 0; i < s.totalChunks; i++) {
                byte[] partBytes = Files.readAllBytes(s.dir.resolve(String.format("part_%06d", i)));
                out.write(partBytes);
            }
        } catch (IOException e) {
            log.error("合并分片失败 uploadId={}", uploadId, e);
            throw new BizException(FileErrorCode.STORAGE_ERROR);
        }
        byte[] merged = out.toByteArray();
        MultipartInitRequest req = s.req;
        FileObject obj = fileService.upload(req.getBizType(), req.getVisibility(), req.getOriginalName(),
                req.getContentType(), merged, req.getOwnerType(), req.getOwnerId(), uploaderId);
        cleanupSession(uploadId, s);
        log.info("分片上传完成：uploadId={} fileId={} size={}", uploadId, obj.getId(), merged.length);
        return obj;
    }

    /** 会话总字节数（前端断点续传进度用，返回各片大小） */
    public Map<String, Object> status(String uploadId) {
        Session s = requireSession(uploadId);
        int receivedCount = 0;
        long receivedBytes = 0;
        for (int i = 0; i < s.totalChunks; i++) {
            if (s.received[i]) {
                receivedCount++;
                try {
                    receivedBytes += Files.size(s.dir.resolve(String.format("part_%06d", i)));
                } catch (IOException ignored) { /* 单片统计失败不影响主流程 */ }
            }
        }
        return Map.of("uploadId", uploadId, "totalChunks", s.totalChunks,
                "receivedChunks", receivedCount, "receivedBytes", receivedBytes);
    }

    /** 定时兜底清理过期会话（服务重启后残留的临时目录）。 */
    public void cleanupExpired() {
        Duration ttl = Duration.ofHours(Math.max(1, props.getMultipartTtlHours()));
        Instant now = Instant.now();
        sessions.forEach((id, s) -> {
            if (Duration.between(s.updatedAt, now).compareTo(ttl) > 0) {
                sessions.remove(id);
                cleanupSession(id, s);
            }
        });
        // 清理磁盘上无会话的孤儿临时目录（重启残留）
        Path root = tempDir();
        try {
            if (Files.exists(root)) {
                try (var stream = Files.list(root)) {
                    stream.filter(Files::isDirectory)
                            .filter(p -> !sessions.containsKey(p.getFileName().toString()))
                            .forEach(p -> deleteRecursively(p));
                }
            }
        } catch (IOException e) {
            log.warn("扫描分片临时目录失败：{}", e.getMessage());
        }
    }

    private Session requireSession(String uploadId) {
        Session s = sessions.get(uploadId);
        if (s == null) {
            throw new BizException(FileErrorCode.MULTIPART_SESSION_INVALID);
        }
        s.updatedAt = Instant.now();
        return s;
    }

    private void cleanupSession(String id, Session s) {
        sessions.remove(id);
        deleteRecursively(s.dir);
    }

    private Path tempDir() {
        return Paths.get(props.getMultipartTempDir()).toAbsolutePath().normalize();
    }

    private void deleteRecursively(Path dir) {
        if (dir == null || !Files.exists(dir)) {
            return;
        }
        try (var walk = Files.walk(dir)) {
            walk.sorted(java.util.Comparator.reverseOrder()).forEach(p -> {
                try {
                    Files.deleteIfExists(p);
                } catch (IOException ignored) { /* 清理失败忽略 */ }
            });
        } catch (IOException e) {
            log.warn("清理分片临时目录失败 dir={} reason={}", dir, e.getMessage());
        }
    }

    private static class Session {
        String id;
        MultipartInitRequest req;
        int totalChunks;
        boolean[] received;
        Path dir;
        Instant updatedAt;
    }
}
