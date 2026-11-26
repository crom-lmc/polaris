package com.laimengchao.sso.forum.module.attachment.service;


import com.laimengchao.sso.forum.module.attachment.vo.AttachmentVO;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;


public interface AttachmentService {

    AttachmentVO upload(MultipartFile file, String usageType);

    ResponseEntity<Resource> download(long id, boolean publicOnly);

    List<AttachmentVO> threadAttachments(long threadId);

    List<AttachmentVO> draftAttachments(long draftId);

    List<Long> draftAttachmentIds(long draftId);

    void bindDraft(long draftId, long accountId, String kind, Object rawAttachmentIds, long coverAttachmentId);

    void bindThread(long threadId, Long draftId, long accountId, String kind, Object rawAttachmentIds, long coverAttachmentId);

    void bindProfileImages(long accountId, Long avatarId, Long coverId, boolean avatarProvided, boolean coverProvided);

    List<Long> attachmentIds(Object raw);
}
