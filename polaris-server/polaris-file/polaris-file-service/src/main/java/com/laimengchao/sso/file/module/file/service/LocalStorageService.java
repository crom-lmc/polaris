package com.laimengchao.sso.file.module.file.service;

import com.laimengchao.sso.common.BizException;
import com.laimengchao.sso.file.common.FileErrorCode;
import com.laimengchao.sso.file.config.FileProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * 本地磁盘存储。文件按 yyyy/MM/dd 分目录落盘，元数据里只存相对路径，
 * 换存储后端（OSS/MinIO）时只需替换本实现，FileService 与控制器不动。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LocalStorageService {

    private static final DateTimeFormatter DIR = DateTimeFormatter.ofPattern("yyyy/MM/dd");

    private final FileProperties props;

    /** 写入文件并返回相对存储路径。 */
    public String write(byte[] bytes, String originalName) {
        String rel = LocalDate.now().format(DIR) + "/" + java.util.UUID.randomUUID().toString().replace("-", "")
                + safeSuffix(originalName);
        Path target = resolve(rel);
        try {
            Files.createDirectories(target.getParent());
            Files.write(target, bytes, StandardOpenOption.CREATE_NEW);
            return rel;
        } catch (IOException e) {
            log.error("写入文件失败 path={}", target, e);
            throw new BizException(FileErrorCode.STORAGE_ERROR);
        }
    }

    /** 读取文件字节；不存在返回 null。 */
    public byte[] read(String rel) {
        Path path = resolve(rel);
        if (!Files.exists(path)) {
            return null;
        }
        try {
            return Files.readAllBytes(path);
        } catch (IOException e) {
            log.error("读取文件失败 path={}", path, e);
            throw new BizException(FileErrorCode.STORAGE_ERROR);
        }
    }

    /** 物理删除（软删标记由 FileService 更新元数据后，孤儿清理任务调用本方法）。 */
    public boolean delete(String rel) {
        try {
            return Files.deleteIfExists(resolve(rel));
        } catch (IOException e) {
            log.error("删除文件失败 path={}", rel, e);
            return false;
        }
    }

    /** 路径穿越防护：相对路径必须落在存储根目录内。 */
    private Path resolve(String rel) {
        Path root = Paths.get(props.getStorageRoot()).toAbsolutePath().normalize();
        Path target = root.resolve(rel).normalize();
        if (!target.startsWith(root)) {
            throw new BizException(FileErrorCode.STORAGE_ERROR, "非法文件路径");
        }
        return target;
    }

    private String safeSuffix(String originalName) {
        if (originalName == null) {
            return "";
        }
        int dot = originalName.lastIndexOf('.');
        if (dot < 0 || dot == originalName.length() - 1) {
            return "";
        }
        String ext = originalName.substring(dot).replaceAll("[^a-zA-Z0-9.]", "");
        return ext.length() <= 10 ? ext : "";
    }
}
