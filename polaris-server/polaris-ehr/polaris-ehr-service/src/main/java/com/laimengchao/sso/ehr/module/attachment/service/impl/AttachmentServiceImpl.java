package com.laimengchao.sso.ehr.module.attachment.service.impl;

import com.laimengchao.sso.ehr.module.attachment.service.AttachmentService;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.laimengchao.sso.common.BizException;
import com.laimengchao.sso.common.ErrorCode;
import com.laimengchao.sso.ehr.common.EhrErrorCode;
import com.laimengchao.sso.ehr.module.attachment.entity.Attachment;
import com.laimengchao.sso.ehr.module.attachment.mapper.AttachmentMapper;
import com.laimengchao.sso.ehr.module.attachment.vo.AttachmentVO;
import com.laimengchao.sso.ehr.module.contract.entity.Contract;
import com.laimengchao.sso.ehr.module.contract.mapper.ContractMapper;
import com.laimengchao.sso.ehr.module.employee.entity.Employee;
import com.laimengchao.sso.ehr.module.employee.mapper.EmployeeMapper;
import com.laimengchao.sso.ehr.security.DataScopeService;
import com.laimengchao.sso.file.client.api.FileApi;
import com.laimengchao.sso.file.client.dto.FileMeta;
import com.laimengchao.sso.file.client.dto.FileUploadRequest;
import com.laimengchao.sso.file.client.dto.R;
import com.laimengchao.sso.file.client.dto.WatermarkContentRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

/**
 * 最小附件能力（P1-1 的 ehr_attachment 先落这一版）：上传后得到附件ID，
 * 业务提交时把附件ID带上，成功后由业务服务 bind。文件名只进元数据，落盘用雪花ID，避免路径穿越。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AttachmentServiceImpl implements AttachmentService {

    private static final long MAX_SIZE = 20L * 1024 * 1024;
    private static final String FILE_BIZ_TYPE = "ehr_attachment";

    private final AttachmentMapper attachmentMapper;
    private final EmployeeMapper employeeMapper;
    private final ContractMapper contractMapper;
    private final DataScopeService dataScopeService;
    private final FileApi fileApi;

    @Value("${ehr.file-root:./data/attachments}")
    private String fileRoot;

    public AttachmentVO upload(MultipartFile file, String bizType, Long accountId, Long bizId) {
        if (file == null || file.isEmpty()) {
            throw new BizException(ErrorCode.PARAM_ERROR, "请选择要上传的文件");
        }
        if (file.getSize() > MAX_SIZE) {
            throw new BizException(ErrorCode.PARAM_ERROR, "单个附件不能超过 20MB");
        }
        String type = normalizeType(bizType);
        if (("EMPLOYEE".equals(type) || "CONTRACT".equals(type)) && bizId != null) {
            checkReadable(type, bizId);
        }
        String original = file.getOriginalFilename() == null ? "file" : file.getOriginalFilename();
        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException e) {
            log.error("附件读取失败 name={}", original, e);
            throw new BizException(ErrorCode.SYSTEM_ERROR, "附件读取失败，请稍后重试");
        }
        return save(bytes, original, file.getContentType(), type, accountId, bizId);
    }

    /** 服务端生成的文件（如导入错误明细 xlsx）直接落库，供本人下载 */
    public AttachmentVO uploadBytes(byte[] bytes, String fileName, String bizType, Long accountId, Long bizId) {
        if (bytes == null || bytes.length == 0) {
            throw new BizException(ErrorCode.PARAM_ERROR, "文件内容为空");
        }
        if (bytes.length > MAX_SIZE) {
            throw new BizException(ErrorCode.PARAM_ERROR, "单个附件不能超过 20MB");
        }
        String type = normalizeType(bizType);
        String original = fileName == null || fileName.isBlank() ? "file" : fileName;
        return save(bytes, original, "", type, accountId, bizId);
    }

    private AttachmentVO save(byte[] bytes, String original, String contentType,
                              String type, Long accountId, Long bizId) {
        String suffix = suffixOf(original);
        long id = IdWorker.getId();
        String key = type + "/" + id + suffix;
        Long fileId = uploadToUnified(bytes, original, contentType, accountId);
        if (fileId == null) {
            Path target = Paths.get(fileRoot).toAbsolutePath().normalize().resolve(key);
            try {
                Files.createDirectories(target.getParent());
                Files.write(target, bytes);
            } catch (IOException e) {
                log.error("附件落盘失败 key={}", key, e);
                throw new BizException(ErrorCode.SYSTEM_ERROR, "附件保存失败，请稍后重试");
            }
        }
        Attachment a = new Attachment();
        a.setId(id);
        a.setBizType(type);
        a.setBizId(bizId == null ? 0L : bizId);
        a.setFileName(original);
        a.setFileKey(fileId == null ? key : "");
        a.setFileId(fileId);
        a.setSize((long) bytes.length);
        a.setMime(contentType == null ? "" : contentType);
        a.setUploadedBy(accountId);
        attachmentMapper.insert(a);
        AttachmentVO vo = new AttachmentVO();
        vo.setId(id);
        vo.setFileId(fileId);
        vo.setFileName(original);
        vo.setSize((long) bytes.length);
        vo.setMime(a.getMime());
        vo.setBizType(type);
        vo.setBizId(a.getBizId());
        vo.setUploadedBy(accountId);
        vo.setCreatedAt(a.getCreatedAt());
        return vo;
    }

    /** 上传到统一文件服务；失败返回 null，由调用方回退旧本地存储。 */
    private Long uploadToUnified(byte[] bytes, String original, String contentType, Long accountId) {
        try {
            FileUploadRequest request = new FileUploadRequest();
            request.setBizType(FILE_BIZ_TYPE);
            // ehr_attachment 策略 force_private=1，服务端会强制 PRIVATE，这里显式声明避免歧义
            request.setVisibility("PRIVATE");
            request.setOriginalName(original);
            request.setContentType(contentType == null ? "" : contentType);
            request.setContent(Base64.getEncoder().encodeToString(bytes));
            request.setOwnerType("EHR_ATTACHMENT");
            request.setUploaderId(accountId);
            R<FileMeta> result = fileApi.upload(request);
            FileMeta meta = result == null ? null : result.getData();
            if (result == null || !result.ok() || meta == null || meta.getId() == null) {
                log.warn("统一文件服务上传失败：name={}, message={}", original,
                        result == null ? "无响应" : result.getMessage());
                return null;
            }
            return Long.valueOf(meta.getId());
        } catch (Exception e) {
            log.warn("统一文件服务上传异常，回退本地存储：name={}", original, e);
            return null;
        }
    }

    /** 读附件字节：file_id 优先从统一文件服务取，读不到再回退旧本地文件。 */
    private byte[] readBytes(Attachment a) {
        if (a.getFileId() != null) {
            try {
                R<String> result = fileApi.content(String.valueOf(a.getFileId()));
                if (result != null && result.ok() && result.getData() != null) {
                    return Base64.getDecoder().decode(result.getData());
                }
                log.warn("统一文件服务读取为空，回退本地：fileId={}", a.getFileId());
            } catch (Exception e) {
                log.warn("统一文件服务读取失败，回退本地：fileId={}", a.getFileId(), e);
            }
        }
        if (a.getFileKey() == null || a.getFileKey().isBlank()) {
            throw new BizException(EhrErrorCode.ATTACHMENT_NOT_FOUND, "附件文件已丢失");
        }
        Path p = Paths.get(fileRoot).toAbsolutePath().normalize().resolve(a.getFileKey());
        try {
            if (!Files.exists(p)) {
                throw new BizException(EhrErrorCode.ATTACHMENT_NOT_FOUND, "附件文件已丢失");
            }
            return Files.readAllBytes(p);
        } catch (IOException e) {
            log.error("附件读取失败 key={}", a.getFileKey(), e);
            throw new BizException(EhrErrorCode.ATTACHMENT_NOT_FOUND, "附件文件已丢失");
        }
    }

    /** 内部解析用（导入 xlsx）：返回落盘路径，不存在直接报错 */
    public Path localPath(Long attachmentId) {
        Attachment a = attachmentId == null ? null : attachmentMapper.selectById(attachmentId);
        if (a == null) {
            throw new BizException(EhrErrorCode.ATTACHMENT_NOT_FOUND, "附件不存在");
        }
        if (a.getFileId() != null) {
            // 统一文件服务里的文件没有本地路径，Excel 解析需要一个带正确扩展名的临时文件
            try {
                Path temp = Files.createTempFile("ehr-attachment-", suffixOf(a.getFileName()));
                Files.write(temp, readBytes(a));
                temp.toFile().deleteOnExit();
                return temp;
            } catch (IOException e) {
                log.error("附件临时文件生成失败 id={}", attachmentId, e);
                throw new BizException(EhrErrorCode.ATTACHMENT_NOT_FOUND, "附件读取失败");
            }
        }
        Path p = Paths.get(fileRoot).toAbsolutePath().normalize().resolve(a.getFileKey());
        if (!Files.exists(p)) {
            throw new BizException(EhrErrorCode.ATTACHMENT_NOT_FOUND, "附件文件已丢失");
        }
        return p;
    }

    /** 员工档案 / 合同附件列表：按数据范围校验归属 */
    public List<AttachmentVO> list(String bizType, Long bizId) {
        String type = normalizeType(bizType);
        checkReadable(type, bizId);
        return attachmentMapper.selectList(new LambdaQueryWrapper<Attachment>()
                        .eq(Attachment::getBizType, type)
                        .eq(Attachment::getBizId, bizId)
                        .orderByDesc(Attachment::getCreatedAt)
                        .orderByDesc(Attachment::getId))
                .stream().map(this::toVO).collect(Collectors.toList());
    }

    /** 删除附件（逻辑删除，旧本地文件保留便于追溯）；仅业务数据范围内可删 */
    public void delete(Long attachmentId, Long accountId) {
        Attachment a = attachmentMapper.selectById(attachmentId);
        if (a == null) {
            throw new BizException(EhrErrorCode.ATTACHMENT_NOT_FOUND, "附件不存在");
        }
        if ("EMPLOYEE".equals(a.getBizType()) || "CONTRACT".equals(a.getBizType())) {
            checkReadable(a.getBizType(), a.getBizId());
        } else if (accountId != null && !accountId.equals(a.getUploadedBy())) {
            throw new BizException(ErrorCode.FORBIDDEN, "只能删除自己上传的附件");
        }
        attachmentMapper.deleteById(attachmentId);
        if (a.getFileId() != null) {
            try {
                R<Void> result = fileApi.delete(String.valueOf(a.getFileId()));
                if (result == null || !result.ok()) {
                    log.warn("统一文件服务软删失败：id={}, message={}", a.getFileId(),
                            result == null ? "无响应" : result.getMessage());
                }
            } catch (Exception e) {
                log.warn("统一文件服务软删异常：id={}", a.getFileId(), e);
            }
        }
    }

    private AttachmentVO toVO(Attachment a) {
        AttachmentVO vo = new AttachmentVO();
        vo.setId(a.getId());
        vo.setFileId(a.getFileId());
        vo.setFileName(a.getFileName());
        vo.setSize(a.getSize());
        vo.setMime(a.getMime());
        vo.setBizType(a.getBizType());
        vo.setBizId(a.getBizId());
        vo.setUploadedBy(a.getUploadedBy());
        vo.setCreatedAt(a.getCreatedAt());
        return vo;
    }

    private String normalizeType(String bizType) {
        return bizType == null || bizType.isBlank() ? "TEMP" : bizType.trim().toUpperCase(Locale.ROOT);
    }

    /** 档案 / 合同附件按员工数据范围；临时附件只允许本人 */
    private void checkReadable(String type, Long bizId) {
        if ("EMPLOYEE".equals(type)) {
            requireAccessibleEmployee(bizId);
        } else if ("CONTRACT".equals(type)) {
            Contract c = bizId == null ? null : contractMapper.selectById(bizId);
            if (c == null) {
                throw new BizException(EhrErrorCode.CONTRACT_NOT_FOUND, "合同不存在");
            }
            requireAccessibleEmployee(c.getEmployeeId());
        } else if ("TEMP".equals(type)) {
            var user = com.laimengchao.sso.common.security.SecurityUtils.currentUser().orElse(null);
            if (user == null || !user.isSuperAdmin()) {
                throw new BizException(ErrorCode.FORBIDDEN, "临时附件仅本人可操作");
            }
        }
    }

    private Employee requireAccessibleEmployee(Long employeeId) {
        Employee emp = employeeId == null ? null : employeeMapper.selectById(employeeId);
        if (emp == null) {
            throw new BizException(EhrErrorCode.EMP_NOT_FOUND, "员工不存在");
        }
        if (!dataScopeService.canAccessOrg(emp.getOrgCode())) {
            throw new BizException(ErrorCode.FORBIDDEN, "无权查看该员工的附件");
        }
        return emp;
    }

    /** 提交请假时校验附件归属，防止拿别人的附件ID */
    public Attachment requireOwned(Long attachmentId, Long accountId) {
        Attachment a = attachmentMapper.selectById(attachmentId);
        if (a == null || (accountId != null && !accountId.equals(a.getUploadedBy()))) {
            throw new BizException(EhrErrorCode.ATTACHMENT_NOT_FOUND, "附件不存在或无权使用");
        }
        return a;
    }

    /** 业务单据落库后绑定附件，避免未提交的附件被其他单据复用 */
    public void bind(Long attachmentId, String bizType, Long bizId) {
        attachmentMapper.update(null, new LambdaUpdateWrapper<Attachment>()
                .eq(Attachment::getId, attachmentId)
                .set(Attachment::getBizType, bizType)
                .set(Attachment::getBizId, bizId));
    }

    public String nameOf(Long attachmentId) {
        if (attachmentId == null) {
            return "";
        }
        Attachment a = attachmentMapper.selectById(attachmentId);
        return a == null ? "" : a.getFileName();
    }

    /**
     * 复制附件到新的业务对象（候选人简历 → 员工档案）。落盘文件复制一份新 key，
     * 避免原候选人附件被清理/保留期到期时连带删除员工侧文件。
     */
    public Long copyTo(Long attachmentId, String bizType, Long bizId) {
        Attachment src = attachmentId == null ? null : attachmentMapper.selectById(attachmentId);
        if (src == null) {
            throw new BizException(EhrErrorCode.ATTACHMENT_NOT_FOUND, "附件不存在");
        }
        byte[] bytes = readBytes(src);
        AttachmentVO vo = save(bytes, src.getFileName(), src.getMime(), normalizeType(bizType),
                src.getUploadedBy(), bizId);
        return vo.getId();
    }

    public Resource download(Long attachmentId, Long accountId, boolean canViewAll) {
        return download(attachmentId, accountId, canViewAll, null, null, null);
    }

    /** 下载附件；传入下载人身份时对图片/PDF 烧录水印（由统一文件服务按水印配置处理）。 */
    public Resource download(Long attachmentId, Long accountId, boolean canViewAll,
                             String username, String userId, String clientIp) {
        Attachment a = attachmentMapper.selectById(attachmentId);
        if (a == null) {
            throw new BizException(EhrErrorCode.ATTACHMENT_NOT_FOUND, "附件不存在");
        }
        // 档案 / 合同附件按员工数据范围放行；请假附件沿用「本人或有审批/HR权限」
        if ("EMPLOYEE".equals(a.getBizType())) {
            requireAccessibleEmployee(a.getBizId());
        } else if ("CONTRACT".equals(a.getBizType())) {
            Contract c = contractMapper.selectById(a.getBizId());
            if (c == null) {
                throw new BizException(EhrErrorCode.CONTRACT_NOT_FOUND, "合同不存在");
            }
            requireAccessibleEmployee(c.getEmployeeId());
        } else if ("CANDIDATE".equals(a.getBizType())) {
            // 候选人简历只对招聘权限持有者开放
            if (!canViewAll) {
                throw new BizException(ErrorCode.FORBIDDEN, "无权下载该附件");
            }
        } else if (!canViewAll && accountId != null && !accountId.equals(a.getUploadedBy())) {
            throw new BizException(ErrorCode.FORBIDDEN, "无权下载该附件");
        }
        if (a.getFileId() != null) {
            byte[] bytes = readBytes(a);
            // 有下载人身份时走水印烧录；失败回退原始字节，绝不影响下载可用性
            if (username != null || userId != null) {
                bytes = watermarkedBytes(String.valueOf(a.getFileId()), bytes, username, userId, clientIp);
            }
            return new ByteArrayResource(bytes);
        }
        Path p = Paths.get(fileRoot).toAbsolutePath().normalize().resolve(a.getFileKey());
        if (!Files.exists(p)) {
            throw new BizException(EhrErrorCode.ATTACHMENT_NOT_FOUND, "附件文件已丢失");
        }
        return new FileSystemResource(p);
    }

    /** 调用统一文件服务的水印烧录接口；异常时回退原字节。 */
    private byte[] watermarkedBytes(String fileId, byte[] original, String username, String userId, String clientIp) {
        try {
            WatermarkContentRequest req = new WatermarkContentRequest();
            req.setFileId(fileId);
            req.setScene("DOWNLOAD");
            req.setUsername(username);
            req.setUserId(userId);
            req.setIp(clientIp);
            R<String> res = fileApi.watermarkedContent(req);
            if (res != null && res.ok() && res.getData() != null) {
                return Base64.getDecoder().decode(res.getData());
            }
            log.warn("水印烧录返回为空，回退原文件：fileId={}", fileId);
        } catch (Exception e) {
            log.warn("水印烧录失败，回退原文件：fileId={}, message={}", fileId, e.getMessage());
        }
        return original;
    }

    public String fileName(Long attachmentId) {
        return nameOf(attachmentId);
    }

    private String suffixOf(String name) {
        int i = name.lastIndexOf('.');
        if (i < 0 || i > name.length() - 2) {
            return "";
        }
        String s = name.substring(i).toLowerCase(Locale.ROOT);
        return s.matches("\\.(pdf|png|jpg|jpeg|gif|webp|doc|docx|xls|xlsx|zip|rar)") ? s : "";
    }
}
