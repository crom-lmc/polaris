package com.laimengchao.sso.file.module.file.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.laimengchao.sso.file.module.file.entity.FileAlias;
import com.laimengchao.sso.file.module.file.entity.FileObject;
import com.laimengchao.sso.file.module.file.mapper.FileAliasMapper;
import com.laimengchao.sso.utils.IdUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * 旧存储路径兼容映射：迁移时注册 old_path → file_id，历史正文里的旧直链据此读回新文件。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FileAliasService {

    private final FileAliasMapper mapper;
    private final FileService fileService;

    /** 注册别名；同路径重复注册时覆盖 fileId（幂等）。 */
    public void register(String oldPath, String fileId, String sourceApp) {
        if (oldPath == null || oldPath.isBlank() || fileId == null || fileId.isBlank()) {
            return;
        }
        String path = normalize(oldPath);
        FileAlias alias = mapper.selectOne(new LambdaQueryWrapper<FileAlias>()
                .eq(FileAlias::getOldPath, path).last("limit 1"));
        if (alias == null) {
            alias = new FileAlias();
            alias.setId(IdUtils.nextId());
            alias.setOldPath(path);
            alias.setCreatedAt(LocalDateTime.now());
        }
        try {
            alias.setFileId(Long.valueOf(fileId.trim()));
        } catch (NumberFormatException e) {
            log.warn("注册文件别名时 fileId 非法：{}", fileId);
            return;
        }
        alias.setSourceApp(sourceApp == null ? "" : sourceApp);
        if (alias.getId() != null && mapper.selectById(alias.getId()) == null) {
            mapper.insert(alias);
        } else {
            mapper.updateById(alias);
        }
    }

    /** 按旧路径读取文件内容；无映射或文件已删返回 null。 */
    public byte[] resolve(String oldPath) {
        String path = normalize(oldPath);
        FileAlias alias = mapper.selectOne(new LambdaQueryWrapper<FileAlias>()
                .eq(FileAlias::getOldPath, path).last("limit 1"));
        if (alias == null || alias.getFileId() == null) {
            return null;
        }
        FileObject obj = fileService.meta(String.valueOf(alias.getFileId()));
        if (obj == null) {
            return null;
        }
        return fileService.content(String.valueOf(alias.getFileId()));
    }

    /** 解析旧文件对应的 MIME 类型（供 legacy 端点回 Content-Type）。 */
    public String resolveContentType(String oldPath) {
        String path = normalize(oldPath);
        FileAlias alias = mapper.selectOne(new LambdaQueryWrapper<FileAlias>()
                .eq(FileAlias::getOldPath, path).last("limit 1"));
        if (alias == null || alias.getFileId() == null) {
            return null;
        }
        FileObject obj = fileService.meta(String.valueOf(alias.getFileId()));
        return obj == null ? null : obj.getContentType();
    }

    /** 统一旧路径：补前导 /，去掉查询串。 */
    private String normalize(String path) {
        String p = path == null ? "" : path.trim();
        int q = p.indexOf('?');
        if (q >= 0) p = p.substring(0, q);
        if (!p.startsWith("/")) p = "/" + p;
        return p;
    }
}
