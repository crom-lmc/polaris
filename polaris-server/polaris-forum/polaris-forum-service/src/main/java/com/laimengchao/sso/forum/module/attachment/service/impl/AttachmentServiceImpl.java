package com.laimengchao.sso.forum.module.attachment.service.impl;

import com.laimengchao.sso.forum.module.attachment.service.AttachmentService;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.laimengchao.sso.common.BizException;
import com.laimengchao.sso.common.ErrorCode;
import com.laimengchao.sso.common.security.FileTypeValidator;
import com.laimengchao.sso.common.security.SecurityUser;
import com.laimengchao.sso.common.security.SecurityUtils;
import com.laimengchao.sso.forum.module.attachment.entity.Attachment;
import com.laimengchao.sso.forum.module.attachment.mapper.AttachmentMapper;
import com.laimengchao.sso.forum.module.attachment.vo.AttachmentVO;
import com.laimengchao.sso.file.client.api.FileApi;
import com.laimengchao.sso.file.client.dto.FileAliasRequest;
import com.laimengchao.sso.file.client.dto.FileMeta;
import com.laimengchao.sso.file.client.dto.FileUploadRequest;
import com.laimengchao.sso.file.client.dto.R;
import com.laimengchao.sso.file.client.dto.WatermarkContentRequest;
import com.laimengchao.sso.forum.security.ForumAdminGuard;
import com.laimengchao.sso.forum.storage.ForumStorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class AttachmentServiceImpl implements AttachmentService {

    private static final long MAX_ATTACHMENT_SIZE = 50L * 1024 * 1024;
    private static final long MAX_COVER_SIZE = 10L * 1024 * 1024;
    private static final String FILE_BIZ_TYPE = "forum_post";
    /** 正文插图上传后写入稳定别名，供编辑器与帖子正文长期匿名读取 */
    private static final String INLINE_URL_PREFIX = "/api/forum/media/file";

    private final AttachmentMapper mapper;
    private final ForumStorageService storage;
    private final ForumAdminGuard adminGuard;
    private final FileApi fileApi;

    public AttachmentVO upload(MultipartFile file, String usageType) {
        SecurityUser user = requireUser();
        String usage = usageType == null ? "ATTACHMENT" : usageType.trim().toUpperCase();
        if (!Set.of("ATTACHMENT", "COVER", "AVATAR", "PROFILE_COVER", "INLINE").contains(usage)) throw param("不支持的附件用途");
        if (file == null || file.isEmpty()) throw param("请选择要上传的文件");
        // 正文插图只允许图片，与普通附件不同（普通附件可传 PDF/压缩包等）
        boolean imageOnly = !"ATTACHMENT".equals(usage);
        long maxSize = imageOnly ? MAX_COVER_SIZE : MAX_ATTACHMENT_SIZE;
        if (file.getSize() > maxSize) throw param(imageOnly ? "图片大小不能超过 10MB" : "附件大小不能超过 50MB");
        String originalName = file.getOriginalFilename() == null ? "" : file.getOriginalFilename().trim();
        if (originalName.isBlank() || originalName.length() > 255) throw param("原始文件名不正确");
        String extension = ForumStorageService.extension(originalName);
        if (imageOnly && !FileTypeValidator.isImageFilename(originalName)) throw param("该用途仅支持图片文件");
        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException e) {
            throw new BizException(ErrorCode.SYSTEM_ERROR, "附件读取失败");
        }
        String contentType;
        try (BufferedInputStream in = new BufferedInputStream(new ByteArrayInputStream(bytes))) {
            contentType = FileTypeValidator.validate(in, extension);
        } catch (IOException e) {
            throw new BizException(ErrorCode.SYSTEM_ERROR, "附件校验失败");
        }

        ForumStorageService.StoredFile stored = null;
        Long fileId = null;
        try {
            FileUploadRequest request = new FileUploadRequest();
            request.setBizType(FILE_BIZ_TYPE);
            request.setVisibility("PUBLIC");
            request.setOriginalName(originalName);
            request.setContentType(contentType);
            request.setContent(Base64.getEncoder().encodeToString(bytes));
            request.setOwnerType("FORUM_ATTACHMENT");
            request.setUploaderId(user.getAccountId());
            R<FileMeta> result = fileApi.upload(request);
            FileMeta meta = result == null ? null : result.getData();
            if (result == null || !result.ok() || meta == null || meta.getId() == null) {
                throw new IllegalStateException(result == null ? "无响应" : result.getMessage());
            }
            fileId = Long.valueOf(meta.getId());
            if ("INLINE".equals(usage)) {
                // 正文插图写进帖子 HTML，必须用不依赖登录/绑定状态的稳定路径：
                // 注册别名 → 走网关 forum-file-legacy 路由 → file-service legacy 端点读取。
                // 别名路径由 fileId 确定性生成（view() 可原样重建），编辑期与发布后都能匿名访问。
                FileAliasRequest alias = new FileAliasRequest();
                alias.setOldPath(inlineUrl(fileId, extension));
                alias.setFileId(meta.getId());
                alias.setSourceApp("forum");
                R<Void> aliasResult = fileApi.registerAlias(alias);
                if (aliasResult == null || !aliasResult.ok()) {
                    throw new IllegalStateException(aliasResult == null ? "别名无响应" : aliasResult.getMessage());
                }
            }
        } catch (Exception e) {
            // 正文插图必须落到统一文件服务并注册别名，本地回退无法生成长期可读的正文 URL，
            // 因此这里不做降级，直接报错（避免插入帖子后变成裂图）；已上传成功的先清理孤儿。
            if ("INLINE".equals(usage)) {
                if (fileId != null) {
                    try {
                        fileApi.delete(String.valueOf(fileId));
                    } catch (Exception ignore) {
                        log.warn("清理统一文件服务孤儿文件失败：id={}", fileId, ignore);
                    }
                }
                log.error("正文插图上传失败：name={}", originalName, e);
                throw new BizException(ErrorCode.SYSTEM_ERROR, "图片上传失败，请稍后重试");
            }
            log.warn("统一文件服务上传失败，回退本地存储：name={}", originalName, e);
            try {
                stored = storage.store(file, extension);
            } catch (Exception ioe) {
                throw new BizException(ErrorCode.SYSTEM_ERROR, "附件保存失败");
            }
        }

        Attachment values = new Attachment();
        values.setId(IdWorker.getId());
        values.setOwnerAccountId(user.getAccountId());
        values.setUsageType(usage);
        values.setOriginalName(originalName);
        values.setStoragePath(stored == null ? "" : stored.storagePath());
        values.setFileId(fileId);
        values.setMimeType(stored == null ? contentType : stored.mimeType());
        values.setSize(stored == null ? bytes.length : stored.size());
        try {
            mapper.insert(values);
            return view(mapper.findById(values.getId()));
        } catch (RuntimeException e) {
            if (stored != null) {
                storage.delete(stored.storagePath());
            } else if (fileId != null) {
                try {
                    fileApi.delete(String.valueOf(fileId));
                } catch (Exception ignore) {
                    log.warn("清理统一文件服务孤儿文件失败：id={}", fileId, ignore);
                }
            }
            throw e;
        }
    }

    public ResponseEntity<Resource> download(long id, boolean publicOnly) {
        Attachment row = publicOnly ? mapper.findPublicById(id) : mapper.findById(id);
        if (row == null) throw new BizException(ErrorCode.NOT_FOUND, "附件不存在或不可公开访问");
        if (!publicOnly) {
            SecurityUser user = requireUser();
            if (row.getOwnerAccountId() != user.getAccountId() && !adminGuard.isForumAdmin(user)) {
                throw new BizException(ErrorCode.FORBIDDEN, "无权下载该附件");
            }
        }
        String mimeType = text(row.getMimeType());
        String originalName = text(row.getOriginalName());
        boolean inline = mimeType.startsWith("image/");
        ContentDisposition disposition = ContentDisposition.builder(inline ? "inline" : "attachment")
                .filename(originalName, StandardCharsets.UTF_8).build();
        ResponseEntity.BodyBuilder builder = ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(mimeType))
                .contentLength(longValue(row.getSize()))
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .header("X-Content-Type-Options", "nosniff");
        if (publicOnly && inline) {
            // 公开图片按附件ID寻址、内容不可变（上传后不覆盖），加长缓存避免轮播/头像每次进页面重复下载。
            // 显式设置后 Spring Security 的 CacheControlHeadersWriter 不再注入 no-cache。
            builder.cacheControl(CacheControl.maxAge(365, TimeUnit.DAYS).cachePublic().immutable());
        }
        // 管理端下载带登录人身份用于水印；公开下载匿名（水印若有则用占位符）
        if (publicOnly) {
            return builder.body(readFile(row));
        }
        SecurityUser user = requireUser();
        return builder.body(readFile(row, user.getUsername(), String.valueOf(user.getAccountId()), null));
    }

    /** 统一文件服务优先；取不到时回退旧本地存储（存量文件 file_id 为空）。 */
    private Resource readFile(Attachment row) {
        return readFile(row, null, null, null);
    }

    /** 读取附件字节；传身份时对图片/PDF 烧录下载水印。 */
    private Resource readFile(Attachment row, String username, String userId, String clientIp) {
        if (row.getFileId() != null) {
            try {
                R<String> result;
                if (username != null || userId != null) {
                    WatermarkContentRequest req = new WatermarkContentRequest();
                    req.setFileId(String.valueOf(row.getFileId()));
                    req.setScene("DOWNLOAD");
                    req.setUsername(username);
                    req.setUserId(userId);
                    req.setIp(clientIp);
                    result = fileApi.watermarkedContent(req);
                } else {
                    result = fileApi.content(String.valueOf(row.getFileId()));
                }
                if (result != null && result.ok() && result.getData() != null) {
                    return new ByteArrayResource(Base64.getDecoder().decode(result.getData()));
                }
                log.warn("统一文件服务读取为空，回退本地：fileId={}", row.getFileId());
            } catch (Exception e) {
                log.warn("统一文件服务读取失败，回退本地：fileId={}", row.getFileId(), e);
            }
        }
        return storage.read(text(row.getStoragePath()));
    }

    public List<AttachmentVO> threadAttachments(long threadId) {
        return mapper.listByThread(threadId).stream().map(this::view).toList();
    }

    public List<AttachmentVO> draftAttachments(long draftId) {
        return mapper.listByDraft(draftId).stream().map(this::view).toList();
    }

    public List<Long> draftAttachmentIds(long draftId) {
        return mapper.listByDraft(draftId).stream()
                .filter(row -> "ATTACHMENT".equals(row.getUsageType()))
                .map(Attachment::getId).toList();
    }

    public void bindDraft(long draftId, long accountId, String kind, Object rawAttachmentIds, long coverAttachmentId) {
        mapper.clearDraft(draftId);
        List<Long> ids = attachmentIds(rawAttachmentIds);
        validateCover(kind, coverAttachmentId);
        for (Long id : ids) {
            requireOwned(id, accountId, "ATTACHMENT");
            if (mapper.bindDraft(id, draftId, accountId) != 1) throw param("附件已绑定其他内容");
        }
        if (coverAttachmentId > 0) {
            requireOwned(coverAttachmentId, accountId, "COVER");
            if (mapper.bindDraft(coverAttachmentId, draftId, accountId) != 1) throw param("封面已绑定其他内容");
        }
    }

    public void bindThread(long threadId, Long draftId, long accountId, String kind,
                           Object rawAttachmentIds, long coverAttachmentId) {
        List<Long> ids = attachmentIds(rawAttachmentIds);
        validateCover(kind, coverAttachmentId);
        for (Long id : ids) {
            requireOwned(id, accountId, "ATTACHMENT");
            bindThreadRow(id, threadId, draftId, accountId, "附件已绑定其他内容");
        }
        if (coverAttachmentId > 0) {
            requireOwned(coverAttachmentId, accountId, "COVER");
            bindThreadRow(coverAttachmentId, threadId, draftId, accountId, "封面已绑定其他内容");
        }
    }

    public void bindProfileImages(long accountId, Long avatarId, Long coverId,
                                  boolean avatarProvided, boolean coverProvided) {
        if (avatarProvided && avatarId != null) requireProfileImage(avatarId, accountId, "AVATAR");
        if (coverProvided && coverId != null) requireProfileImage(coverId, accountId, "PROFILE_COVER");
        if (avatarProvided) bindProfileImage(accountId, avatarId, "AVATAR");
        if (coverProvided) bindProfileImage(accountId, coverId, "PROFILE_COVER");
    }

    private void requireProfileImage(long id, long accountId, String usageType) {
        Attachment row = requireOwned(id, accountId, usageType);
        if (longValue(row.getThreadId()) > 0 || longValue(row.getDraftId()) > 0
                || !text(row.getMimeType()).startsWith("image/")) {
            throw param("资料图片已绑定其他内容或不是有效图片");
        }
    }

    private void bindProfileImage(long accountId, Long id, String usageType) {
        mapper.clearProfile(accountId, usageType, id);
        if (id != null && mapper.bindProfile(id, accountId, usageType) != 1) {
            throw param("资料图片已绑定其他内容");
        }
    }

    public List<Long> attachmentIds(Object raw) {
        if (raw == null) return List.of();
        if (!(raw instanceof List<?> list)) throw param("attachmentIds 必须为数组");
        Set<Long> ids = new LinkedHashSet<>();
        for (Object value : list) {
            long id = longValue(value);
            if (id <= 0) throw param("附件 ID 不正确");
            ids.add(id);
        }
        return new ArrayList<>(ids);
    }

    private void bindThreadRow(long id, long threadId, Long draftId, long accountId, String message) {
        int updated = draftId == null ? mapper.bindDirectThread(id, threadId, accountId)
                : mapper.bindDraftThread(id, draftId, threadId, accountId);
        if (updated != 1) throw param(message);
    }

    private Attachment requireOwned(long id, long accountId, String usageType) {
        Attachment row = mapper.findByIdForUpdate(id);
        if (row == null || row.getOwnerAccountId() != accountId) {
            throw new BizException(ErrorCode.FORBIDDEN, "附件不存在或不属于当前账号");
        }
        if (!usageType.equals(row.getUsageType())) throw param("附件用途不正确");
        return row;
    }

    private void validateCover(String kind, long coverAttachmentId) {
        if (!"article".equals(kind) && coverAttachmentId > 0) throw param("仅文章可以设置封面");
    }

    private AttachmentVO view(Attachment row) {
        AttachmentVO view = new AttachmentVO();
        view.setId(row.getId());
        view.setOriginalName(text(row.getOriginalName()));
        view.setMimeType(text(row.getMimeType()));
        view.setSize(longValue(row.getSize()));
        view.setUsageType(text(row.getUsageType()));
        // 正文插图走稳定别名路径（编辑期/发布后均可匿名读取），其余附件仍按 ID 寻址
        if ("INLINE".equals(text(row.getUsageType())) && row.getFileId() != null) {
            view.setUrl(inlineUrl(row.getFileId(), ForumStorageService.extension(text(row.getOriginalName()))));
            return view;
        }
        boolean published = longValue(row.getThreadId()) > 0 || longValue(row.getProfileAccountId()) > 0;
        view.setUrl((published ? "/api/forum/public/attachments/" : "/api/forum/attachments/") + row.getId());
        return view;
    }

    /** 由 fileId 确定性生成正文插图的稳定别名路径，与上传时注册的路径一致。 */
    private String inlineUrl(long fileId, String extension) {
        return INLINE_URL_PREFIX + "/" + fileId + (extension.isEmpty() ? "" : "." + extension);
    }

    private SecurityUser requireUser() {
        return SecurityUtils.currentUser().orElseThrow(() -> new BizException(ErrorCode.UNAUTHORIZED));
    }

    private BizException param(String message) {
        return new BizException(ErrorCode.PARAM_ERROR, message);
    }

    private String text(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private long longValue(Object value) {
        if (value instanceof Number number) return number.longValue();
        try { return value == null || String.valueOf(value).isBlank() ? 0 : Long.parseLong(String.valueOf(value)); }
        catch (NumberFormatException e) { return 0; }
    }
}
