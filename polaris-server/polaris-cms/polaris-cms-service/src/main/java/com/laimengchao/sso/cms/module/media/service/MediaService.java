package com.laimengchao.sso.cms.module.media.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.laimengchao.sso.common.BizException;
import com.laimengchao.sso.common.ErrorCode;
import com.laimengchao.sso.common.security.FileTypeValidator;
import com.laimengchao.sso.common.security.SecurityUtils;
import com.laimengchao.sso.cms.common.CmsErrorCode;
import com.laimengchao.sso.cms.config.CmsProperties;
import com.laimengchao.sso.cms.module.media.entity.Media;
import com.laimengchao.sso.cms.module.media.mapper.MediaMapper;
import com.laimengchao.sso.cms.security.DataScopeService;
import com.laimengchao.sso.cms.storage.StorageService;
import com.laimengchao.sso.cms.storage.StoredFile;
import com.laimengchao.sso.file.client.api.FileApi;
import com.laimengchao.sso.file.client.dto.FileAliasRequest;
import com.laimengchao.sso.file.client.dto.FileMeta;
import com.laimengchao.sso.file.client.dto.FileUploadRequest;
import com.laimengchao.sso.file.client.dto.R;
import com.laimengchao.sso.utils.AccountContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.Locale;
import java.util.UUID;

/** 媒体库：上传/分页/删除（软删 + 统一文件服务优先，本地磁盘降级回退） */
@Slf4j
@Service
@RequiredArgsConstructor
public class MediaService {

    private static final DateTimeFormatter DATE_PATH = DateTimeFormatter.ofPattern("yyyy/MM/dd");
    private static final String BIZ_TYPE = "cms_media";
    private static final String VISIBILITY = "PUBLIC";
    private static final String SOURCE_APP = "cms";

    private final MediaMapper mediaMapper;
    private final StorageService storageService;
    private final DataScopeService dataScopeService;
    private final FileApi fileApi;
    private final CmsProperties properties;

    @Value("${cms.upload.url-prefix:/api/cms/media/file}")
    private String urlPrefix;

    public Media upload(MultipartFile file) {
        Long uid = SecurityUtils.currentUserId();
        if (uid == null) {
            throw new BizException(ErrorCode.UNAUTHORIZED);
        }
        String originalName = file == null || file.getOriginalFilename() == null
                ? "" : file.getOriginalFilename().trim();
        String ext = extension(originalName);
        if (ext.isEmpty() || !properties.getUpload().getAllowedExtensions().contains(ext)) {
            throw new BizException(CmsErrorCode.FILE_TYPE_NOT_ALLOWED);
        }
        byte[] bytes;
        try {
            bytes = file == null ? new byte[0] : file.getBytes();
        } catch (IOException e) {
            throw new BizException(CmsErrorCode.UPLOAD_FAILED, "文件读取失败：" + e.getMessage());
        }
        if (bytes.length > properties.getUpload().getMaxSizeMb() * 1024L * 1024L) {
            throw new BizException(CmsErrorCode.FILE_TOO_LARGE);
        }
        String contentType;
        try (BufferedInputStream in = new BufferedInputStream(new ByteArrayInputStream(bytes))) {
            contentType = FileTypeValidator.validate(in, ext);
        } catch (IOException e) {
            throw new BizException(CmsErrorCode.UPLOAD_FAILED, "文件校验失败：" + e.getMessage());
        }

        StoredFile sf;
        Long fileId = null;
        String virtualPath = "/" + LocalDate.now().format(DATE_PATH) + "/"
                + UUID.randomUUID().toString().replace("-", "") + "." + ext;
        try {
            FileUploadRequest upload = new FileUploadRequest();
            upload.setBizType(BIZ_TYPE);
            upload.setVisibility(VISIBILITY);
            upload.setOriginalName(originalName);
            upload.setContentType(contentType);
            upload.setContent(Base64.getEncoder().encodeToString(bytes));
            upload.setOwnerType("CMS_MEDIA");
            upload.setUploaderId(uid);
            R<FileMeta> result = fileApi.upload(upload);
            FileMeta meta = result == null ? null : result.getData();
            if (result == null || !result.ok() || meta == null || meta.getId() == null) {
                throw new IllegalStateException(result == null ? "无响应" : result.getMessage());
            }
            fileId = Long.valueOf(meta.getId());

            // 新文件也注册一条稳定别名：正文存的是相对路径，签名 URL 会过期，
            // 只有 legacy 路径可以长期写进文章正文，读取统一走 file-service。
            FileAliasRequest alias = new FileAliasRequest();
            alias.setOldPath(urlPrefix + virtualPath);
            alias.setFileId(meta.getId());
            alias.setSourceApp(SOURCE_APP);
            R<Void> aliasResult = fileApi.registerAlias(alias);
            if (aliasResult == null || !aliasResult.ok()) {
                throw new IllegalStateException(aliasResult == null ? "别名无响应" : aliasResult.getMessage());
            }

            sf = new StoredFile();
            sf.setFilename(meta.getId() + "." + ext);
            sf.setOriginalName(originalName);
            sf.setContentType(contentType);
            sf.setSize(bytes.length);
            sf.setUrl(urlPrefix + virtualPath);
        } catch (Exception e) {
            log.warn("统一文件服务上传失败，回退本地存储：name={}", originalName, e);
            if (fileId != null) {
                try {
                    fileApi.delete(String.valueOf(fileId));
                } catch (Exception ignore) {
                    log.warn("清理统一文件服务孤儿文件失败：id={}", fileId, ignore);
                }
                fileId = null;
            }
            try {
                sf = storageService.store(file, null);
            } catch (IOException ioe) {
                throw new BizException(CmsErrorCode.UPLOAD_FAILED, "文件写入失败：" + ioe.getMessage());
            }
        }

        Media m = new Media();
        m.setFilename(sf.getFilename());
        m.setOriginalName(sf.getOriginalName());
        m.setUrl(sf.getUrl());
        m.setFileId(fileId);
        m.setContentType(sf.getContentType());
        m.setSize(sf.getSize());
        m.setUploadedBy(uid);
        m.setUploadedName(SecurityUtils.currentUsername().orElse(""));
        m.setOrgCode(dataScopeService.currentScopeCode());
        mediaMapper.insert(m);
        return m;
    }

    public IPage<Media> page(String keyword, int page, int size) {
        LambdaQueryWrapper<Media> w = new LambdaQueryWrapper<>();
        dataScopeService.applyOrgScope(w, Media::getOrgCode);
        if (keyword != null && !keyword.isBlank()) {
            w.like(Media::getOriginalName, keyword.trim());
        }
        w.orderByDesc(Media::getId);
        return mediaMapper.selectPage(Page.of(page, size), w);
    }

    public void delete(Long id) {
        Media m = mediaMapper.selectById(id);
        if (m == null) {
            throw new BizException(CmsErrorCode.DATA_NOT_FOUND, "文件不存在");
        }
        dataScopeService.assertCanAccessOrg(m.getOrgCode());
        if (m.getFileId() != null) {
            try {
                R<Void> result = fileApi.delete(String.valueOf(m.getFileId()));
                if (result == null || !result.ok()) {
                    log.warn("统一文件服务软删失败：id={}, message={}", m.getFileId(),
                            result == null ? "无响应" : result.getMessage());
                }
            } catch (Exception e) {
                log.warn("统一文件服务软删异常：id={}", m.getFileId(), e);
            }
        }
        storageService.delete(m.getUrl());
        mediaMapper.deleteById(id);
    }

    private String extension(String name) {
        int dot = name.lastIndexOf('.');
        if (dot < 0 || dot == name.length() - 1) {
            return "";
        }
        return name.substring(dot + 1).toLowerCase(Locale.ROOT);
    }
}
