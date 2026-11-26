package com.laimengchao.sso.ehr.module.attachment.service;

import com.laimengchao.sso.ehr.module.attachment.entity.Attachment;
import com.laimengchao.sso.ehr.module.attachment.vo.AttachmentVO;
import org.springframework.core.io.Resource;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Path;
import java.util.List;

public interface AttachmentService {

    AttachmentVO upload(MultipartFile file, String bizType, Long accountId, Long bizId);

    /** 服务端生成的文件（如导入错误明细 xlsx）直接落库，供本人下载 */
    AttachmentVO uploadBytes(byte[] bytes, String fileName, String bizType, Long accountId, Long bizId);

    /** 内部解析用（导入 xlsx）：返回落盘路径，不存在直接报错 */
    Path localPath(Long attachmentId);

    /** 员工档案 / 合同附件列表：按数据范围校验归属 */
    List<AttachmentVO> list(String bizType, Long bizId);

    /** 删除附件（逻辑删除，旧本地文件保留便于追溯）；仅业务数据范围内可删 */
    void delete(Long attachmentId, Long accountId);

    /** 提交请假时校验附件归属，防止拿别人的附件ID */
    Attachment requireOwned(Long attachmentId, Long accountId);

    /** 业务单据落库后绑定附件，避免未提交的附件被其他单据复用 */
    void bind(Long attachmentId, String bizType, Long bizId);

    String nameOf(Long attachmentId);

    /**
     * 复制附件到新的业务对象（候选人简历 → 员工档案）。落盘文件复制一份新 key，
     * 避免原候选人附件被清理/保留期到期时连带删除员工侧文件。
     */
    Long copyTo(Long attachmentId, String bizType, Long bizId);

    Resource download(Long attachmentId, Long accountId, boolean canViewAll);

    /** 下载附件；传入下载人身份时对图片/PDF 烧录水印（由统一文件服务按水印配置处理）。 */
    Resource download(Long attachmentId, Long accountId, boolean canViewAll, String username, String userId, String clientIp);

    String fileName(Long attachmentId);
}
