package com.laimengchao.sso.ehr.module.announcement.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.laimengchao.sso.common.BizException;
import com.laimengchao.sso.common.ErrorCode;
import com.laimengchao.sso.ehr.module.announcement.entity.AnnouncementAttachment;
import com.laimengchao.sso.ehr.module.announcement.mapper.AnnouncementAttachmentMapper;
import com.laimengchao.sso.ehr.module.announcement.vo.AnnouncementAttachmentVO;
import com.laimengchao.sso.file.client.api.FileApi;
import com.laimengchao.sso.file.client.dto.FileUploadRequest;
import com.laimengchao.sso.file.client.dto.R;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 内部通知附件：统一文件服务优先，不保留本地磁盘回退——公告是内容型数据，
 * 附件读不到就明确报错，避免悄悄展示一份「本地影子文件」造成版本分叉。
 * <p>
 * 权限不在这里判：上传/绑定由公告管理权把门，下载由 Controller 先校验公告可见范围。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AnnouncementAttachmentService {

    private static final long MAX_SIZE = 20L * 1024 * 1024;
    private static final String FILE_BIZ_TYPE = "announcement_attachment";

    private final AnnouncementAttachmentMapper attachmentMapper;
    private final FileApi fileApi;

    /** 上传得到临时附件（announcement_id=0），公告保存时再绑定真实 ID */
    public AnnouncementAttachmentVO upload(MultipartFile file, Long accountId) {
        if (file == null || file.isEmpty()) {
            throw new BizException(ErrorCode.PARAM_ERROR, "请选择要上传的文件");
        }
        if (file.getSize() > MAX_SIZE) {
            throw new BizException(ErrorCode.PARAM_ERROR, "单个附件不能超过 20MB");
        }
        String original = file.getOriginalFilename() == null ? "file" : file.getOriginalFilename();
        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException e) {
            log.error("公告附件读取失败 name={}", original, e);
            throw new BizException(ErrorCode.SYSTEM_ERROR, "附件读取失败，请稍后重试");
        }

        Long fileId;
        try {
            FileUploadRequest req = new FileUploadRequest();
            req.setBizType(FILE_BIZ_TYPE);
            req.setVisibility("PRIVATE");
            req.setOriginalName(original);
            req.setContentType(file.getContentType() == null ? "" : file.getContentType());
            req.setContent(Base64.getEncoder().encodeToString(bytes));
            req.setOwnerType("ANNOUNCEMENT_ATTACHMENT");
            req.setUploaderId(accountId);
            R<com.laimengchao.sso.file.client.dto.FileMeta> res = fileApi.upload(req);
            if (res == null || !res.ok() || res.getData() == null || res.getData().getId() == null) {
                log.error("公告附件上传失败：name={}, message={}", original,
                        res == null ? "无响应" : res.getMessage());
                throw new BizException(ErrorCode.SYSTEM_ERROR, "附件上传失败，请稍后重试");
            }
            fileId = Long.valueOf(res.getData().getId());
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            log.error("公告附件上传异常：name={}", original, e);
            throw new BizException(ErrorCode.SYSTEM_ERROR, "附件上传失败，请稍后重试");
        }

        AnnouncementAttachment a = new AnnouncementAttachment();
        a.setAnnouncementId(0L);
        a.setFileId(fileId);
        a.setFileName(original);
        a.setSize((long) bytes.length);
        a.setMime(file.getContentType() == null ? "" : file.getContentType());
        a.setUploadedBy(accountId);
        attachmentMapper.insert(a);
        log.info("公告附件上传成功：id={} fileId={} name={}", a.getId(), fileId, original);
        return toVO(a);
    }

    /**
     * 公告保存时绑定附件：只接受尚未绑定的临时附件，防止拿别的公告的附件 ID 复用。
     * 本次没带上的旧附件会被移除（记录逻辑删 + 文件服务软删）。
     */
    @Transactional
    public void bind(Long announcementId, List<Long> attachmentIds) {
        List<AnnouncementAttachment> current = listOf(announcementId);
        Set<Long> keep = attachmentIds == null ? Set.of() : new HashSet<>(attachmentIds);
        for (AnnouncementAttachment a : current) {
            if (!keep.contains(a.getId())) {
                remove(a);
            }
        }
        if (attachmentIds == null || attachmentIds.isEmpty()) {
            return;
        }
        for (Long id : attachmentIds) {
            if (id == null) {
                continue;
            }
            AnnouncementAttachment a = attachmentMapper.selectById(id);
            if (a == null) {
                throw new BizException(ErrorCode.PARAM_ERROR, "附件不存在或已被清理");
            }
            if (a.getAnnouncementId() != null && a.getAnnouncementId() != 0L
                    && !a.getAnnouncementId().equals(announcementId)) {
                throw new BizException(ErrorCode.PARAM_ERROR, "附件已被其他通知使用");
            }
            if (!announcementId.equals(a.getAnnouncementId())) {
                attachmentMapper.update(null, new LambdaUpdateWrapper<AnnouncementAttachment>()
                        .eq(AnnouncementAttachment::getId, id)
                        .set(AnnouncementAttachment::getAnnouncementId, announcementId));
            }
        }
    }

    /** 批量取附件，返回 announcementId -> 列表 */
    public Map<Long, List<AnnouncementAttachmentVO>> listByAnnouncementIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return Collections.emptyMap();
        }
        try {
            List<AnnouncementAttachment> rows = attachmentMapper.selectList(
                    new LambdaQueryWrapper<AnnouncementAttachment>()
                            .in(AnnouncementAttachment::getAnnouncementId, ids)
                            .orderByAsc(AnnouncementAttachment::getId));
            Map<Long, List<AnnouncementAttachmentVO>> out = new LinkedHashMap<>();
            for (AnnouncementAttachment a : rows) {
                out.computeIfAbsent(a.getAnnouncementId(), k -> new ArrayList<>()).add(toVO(a));
            }
            return out;
        } catch (Exception e) {
            // 附件是通知的附属信息：新表未建或查询异常时不能让整个通知列表/详情不可用
            log.warn("通知附件列表查询失败，按无附件返回：count={}", ids.size(), e);
            return Collections.emptyMap();
        }
    }

    /** 取字节：调用方必须先做公告可见性校验 */
    public AttachmentFile content(Long attachmentId) {
        AnnouncementAttachment a = attachmentId == null ? null : attachmentMapper.selectById(attachmentId);
        if (a == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "附件不存在");
        }
        byte[] bytes;
        try {
            R<String> res = fileApi.content(String.valueOf(a.getFileId()));
            if (res == null || !res.ok() || res.getData() == null) {
                log.error("统一文件服务读取公告附件失败：fileId={}, message={}", a.getFileId(),
                        res == null ? "无响应" : res.getMessage());
                throw new BizException(ErrorCode.SYSTEM_ERROR, "附件读取失败，请稍后重试");
            }
            bytes = Base64.getDecoder().decode(res.getData());
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            log.error("统一文件服务读取公告附件异常：fileId={}", a.getFileId(), e);
            throw new BizException(ErrorCode.SYSTEM_ERROR, "附件读取失败，请稍后重试");
        }
        AttachmentFile file = new AttachmentFile();
        file.setFileName(a.getFileName());
        file.setMime(a.getMime());
        file.setBytes(bytes);
        return file;
    }

    /** 公告删除时级联清理 */
    public void deleteByAnnouncementIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return;
        }
        for (Long id : ids) {
            for (AnnouncementAttachment a : listOf(id)) {
                remove(a);
            }
        }
    }

    /** 公告所属附件 ID 列表，供可见性校验反查公告 */
    public AnnouncementAttachment find(Long attachmentId) {
        return attachmentId == null ? null : attachmentMapper.selectById(attachmentId);
    }

    private List<AnnouncementAttachment> listOf(Long announcementId) {
        return attachmentMapper.selectList(new LambdaQueryWrapper<AnnouncementAttachment>()
                .eq(AnnouncementAttachment::getAnnouncementId, announcementId)
                .orderByAsc(AnnouncementAttachment::getId));
    }

    private void remove(AnnouncementAttachment a) {
        attachmentMapper.deleteById(a.getId());
        try {
            R<Void> res = fileApi.delete(String.valueOf(a.getFileId()));
            if (res == null || !res.ok()) {
                log.warn("公告附件软删失败：id={} fileId={}", a.getId(), a.getFileId());
            }
        } catch (Exception e) {
            log.warn("公告附件软删异常：id={} fileId={}", a.getId(), a.getFileId(), e);
        }
    }

    private AnnouncementAttachmentVO toVO(AnnouncementAttachment a) {
        AnnouncementAttachmentVO vo = new AnnouncementAttachmentVO();
        vo.setId(a.getId());
        vo.setFileName(a.getFileName());
        vo.setSize(a.getSize());
        vo.setMime(a.getMime());
        vo.setDownloadUrl("/api/ehr/announcements/attachments/" + a.getId() + "/download");
        vo.setCreatedAt(a.getCreatedAt());
        return vo;
    }

    @Data
    public static class AttachmentFile {
        private String fileName;
        private String mime;
        private byte[] bytes;
    }
}
