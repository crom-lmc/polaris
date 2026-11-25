package com.laimengchao.sso.settings.module.notice.service;

import com.laimengchao.sso.common.BizException;
import com.laimengchao.sso.common.ErrorCode;
import com.laimengchao.sso.file.client.api.FileApi;
import com.laimengchao.sso.file.client.dto.R;
import com.laimengchao.sso.file.client.dto.WatermarkContentRequest;
import com.laimengchao.sso.settings.module.notice.dto.NoticeAttachmentDTO;
import com.laimengchao.sso.settings.module.notice.entity.NoticeAttachment;
import com.laimengchao.sso.settings.module.notice.entity.NoticeMessage;
import com.laimengchao.sso.settings.module.notice.mapper.NoticeAttachmentMapper;
import com.laimengchao.sso.settings.module.notice.mapper.NoticeMessageMapper;
import com.laimengchao.sso.settings.module.notice.vo.NoticeAttachmentVO;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 站内消息附件：文件本体只在统一文件服务，本表只存 file_id 与展示元信息。
 * 下载必须校验「当前登录人是该消息接收人」，不提供任何绕过接收人的公开直链。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NoticeAttachmentService {

    private final NoticeAttachmentMapper attachmentMapper;
    private final NoticeMessageMapper messageMapper;
    private final FileApi fileApi;

    /** 消息落库后绑定附件；file_id 非法或为空的项直接跳过，不让脏数据进库 */
    public void bind(Long noticeId, List<NoticeAttachmentDTO> list, Long uploaderId) {
        if (noticeId == null || list == null || list.isEmpty()) {
            return;
        }
        for (NoticeAttachmentDTO dto : list) {
            if (dto == null || dto.getFileId() == null || dto.getFileId() <= 0) {
                log.warn("站内消息附件 file_id 无效，跳过：noticeId={}", noticeId);
                continue;
            }
            NoticeAttachment a = new NoticeAttachment();
            a.setNoticeId(noticeId);
            a.setFileId(dto.getFileId());
            a.setFileName(safe(dto.getFileName(), "附件"));
            a.setSize(dto.getSize() == null ? 0L : dto.getSize());
            a.setMime(safe(dto.getMime(), ""));
            a.setUploadedBy(uploaderId == null ? 0L : uploaderId);
            attachmentMapper.insert(a);
        }
    }

    /** 批量取附件，返回 noticeId -> 附件列表；消息列表一次查询避免 N+1 */
    public Map<Long, List<NoticeAttachmentVO>> listByNoticeIds(List<Long> noticeIds) {
        if (noticeIds == null || noticeIds.isEmpty()) {
            return Collections.emptyMap();
        }
        try {
            List<NoticeAttachment> rows = attachmentMapper.selectList(
                    new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<NoticeAttachment>()
                            .in(NoticeAttachment::getNoticeId, noticeIds)
                            .orderByAsc(NoticeAttachment::getId));
            Map<Long, List<NoticeAttachmentVO>> out = new LinkedHashMap<>();
            for (NoticeAttachment a : rows) {
                out.computeIfAbsent(a.getNoticeId(), k -> new ArrayList<>()).add(toVO(a));
            }
            return out;
        } catch (Exception e) {
            // 附件是消息的附属信息：新表未建或查询异常时不能让整个消息列表不可用
            log.warn("站内消息附件列表查询失败，按无附件返回：count={}", noticeIds.size(), e);
            return Collections.emptyMap();
        }
    }

    /** 下载：先确认附件所属消息确实发给当前账号，再向统一文件服务取字节 */
    public AttachmentFile download(Long attachmentId, Long accountId) {
        return download(attachmentId, accountId, null, null, null);
    }

    /** 下载并对图片/PDF 烧录水印（身份由调用方传入）。 */
    public AttachmentFile download(Long attachmentId, Long accountId,
                                   String username, String userId, String clientIp) {
        NoticeAttachment a = attachmentId == null ? null : attachmentMapper.selectById(attachmentId);
        if (a == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "附件不存在");
        }
        NoticeMessage msg = messageMapper.selectById(a.getNoticeId());
        if (msg == null || msg.getAccountId() == null || !msg.getAccountId().equals(accountId)) {
            throw new BizException(ErrorCode.FORBIDDEN, "无权下载该附件");
        }
        byte[] bytes;
        try {
            R<String> res;
            if (username != null || userId != null) {
                WatermarkContentRequest req = new WatermarkContentRequest();
                req.setFileId(String.valueOf(a.getFileId()));
                req.setScene("DOWNLOAD");
                req.setUsername(username);
                req.setUserId(userId);
                req.setIp(clientIp);
                res = fileApi.watermarkedContent(req);
            } else {
                res = fileApi.content(String.valueOf(a.getFileId()));
            }
            if (res == null || !res.ok() || res.getData() == null) {
                log.error("统一文件服务读取消息附件失败：fileId={}, message={}", a.getFileId(),
                        res == null ? "无响应" : res.getMessage());
                throw new BizException(ErrorCode.SYSTEM_ERROR, "附件读取失败，请稍后重试");
            }
            bytes = Base64.getDecoder().decode(res.getData());
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            log.error("统一文件服务读取消息附件异常：fileId={}", a.getFileId(), e);
            throw new BizException(ErrorCode.SYSTEM_ERROR, "附件读取失败，请稍后重试");
        }
        AttachmentFile file = new AttachmentFile();
        file.setFileName(a.getFileName());
        file.setMime(a.getMime());
        file.setBytes(bytes);
        return file;
    }

    /** 删除消息时由业务侧级联调用；文件本体走 file-service 软删 */
    public void deleteByNoticeIds(List<Long> noticeIds) {
        if (noticeIds == null || noticeIds.isEmpty()) {
            return;
        }
        List<NoticeAttachment> rows = attachmentMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<NoticeAttachment>()
                        .in(NoticeAttachment::getNoticeId, noticeIds));
        for (NoticeAttachment a : rows) {
            attachmentMapper.deleteById(a.getId());
            try {
                R<Void> res = fileApi.delete(String.valueOf(a.getFileId()));
                if (res == null || !res.ok()) {
                    log.warn("消息附件软删失败：fileId={}", a.getFileId());
                }
            } catch (Exception e) {
                log.warn("消息附件软删异常：fileId={}", a.getFileId(), e);
            }
        }
    }

    private NoticeAttachmentVO toVO(NoticeAttachment a) {
        NoticeAttachmentVO vo = new NoticeAttachmentVO();
        vo.setId(a.getId());
        vo.setFileName(a.getFileName());
        vo.setSize(a.getSize());
        vo.setMime(a.getMime());
        vo.setDownloadUrl("/api/settings/notices/attachments/" + a.getId() + "/download");
        vo.setCreatedAt(a.getCreatedAt());
        return vo;
    }

    private static String safe(String v, String fallback) {
        return v == null || v.isBlank() ? fallback : v;
    }

    /** 下载结果：文件名 / MIME / 字节 */
    @Data
    public static class AttachmentFile {
        private String fileName;
        private String mime;
        private byte[] bytes;
    }
}
