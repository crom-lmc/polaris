package com.laimengchao.sso.file.module.share.service.impl;

import com.laimengchao.sso.file.module.share.service.ShareService;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.laimengchao.sso.common.BizException;
import com.laimengchao.sso.file.common.FileErrorCode;
import com.laimengchao.sso.file.config.FileProperties;
import com.laimengchao.sso.file.module.file.entity.FileObject;
import com.laimengchao.sso.file.module.file.service.FileService;
import com.laimengchao.sso.file.module.share.entity.FileShare;
import com.laimengchao.sso.file.module.share.mapper.FileShareMapper;
import com.laimengchao.sso.file.module.share.vo.ShareCreateRequest;
import com.laimengchao.sso.file.module.share.vo.ShareInfoVO;
import com.laimengchao.sso.utils.IdUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;

/** 文件分享链接业务：创建、列表、撤销、凭码访问。 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ShareServiceImpl implements ShareService {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final String CODE_CHARS = "abcdefghjkmnpqrstuvwxyzABCDEFGHJKLMNPQRSTUVWXYZ23456789";

    private final FileShareMapper mapper;
    private final FileService fileService;
    private final FileProperties props;

    /** 创建分享；提取码只存 SHA256 哈希，明文仅在响应中返回一次。 */
    @Transactional(rollbackFor = Exception.class)
    public FileShare create(ShareCreateRequest req, Long createdBy) {
        // 留空(null)表示长期有效/不限次数；一旦填写则必须为正整数，0/负数一律拒绝
        Long expiresHours = req.getExpiresHours();
        Integer maxViews = req.getMaxViews();
        if ((expiresHours != null && expiresHours <= 0) || (maxViews != null && maxViews <= 0)) {
            throw new BizException(FileErrorCode.SHARE_PARAM_INVALID);
        }
        FileObject file = fileService.meta(req.getFileId());
        if (file == null) {
            throw new BizException(FileErrorCode.FILE_NOT_FOUND);
        }
        String code = randomCode(12);
        FileShare share = new FileShare();
        share.setId(IdUtils.nextId());
        share.setFileId(Long.valueOf(file.getId()));
        share.setShareCode(code);
        share.setPasswordHash(req.getPassword() == null || req.getPassword().isBlank()
                ? "" : sha256(req.getPassword()));
        long hours = req.getExpiresHours() == null ? 0 : req.getExpiresHours();
        share.setExpiresAt(hours > 0 ? LocalDateTime.now().plusHours(hours) : null);
        share.setMaxViews(req.getMaxViews() == null ? 0 : Math.max(0, req.getMaxViews()));
        share.setViewCount(0);
        share.setEnabled(true);
        share.setCreatedBy(createdBy == null ? 0L : createdBy);
        share.setCreatedAt(LocalDateTime.now());
        mapper.insert(share);
        share.setUrl(shareUrl(code));
        share.setFileName(file.getOriginalName());
        log.info("创建文件分享：shareId={} fileId={} code={} hasPassword={} expiresAt={}",
                share.getId(), share.getFileId(), code, !share.getPasswordHash().isEmpty(), share.getExpiresAt());
        return share;
    }

    /** 我的分享列表。 */
    public List<FileShare> list(Long createdBy) {
        List<FileShare> list = mapper.selectList(new LambdaQueryWrapper<FileShare>()
                .eq(FileShare::getCreatedBy, createdBy == null ? 0L : createdBy)
                .eq(FileShare::getEnabled, true)
                .orderByDesc(FileShare::getCreatedAt));
        for (FileShare s : list) {
            s.setUrl(shareUrl(s.getShareCode()));
            FileObject f = fileService.meta(String.valueOf(s.getFileId()));
            s.setFileName(f == null ? "" : f.getOriginalName());
        }
        return list;
    }

    /** 撤销分享（逻辑置 enabled=false）。 */
    @Transactional(rollbackFor = Exception.class)
    public void revoke(Long id, Long createdBy) {
        FileShare s = mapper.selectById(id);
        if (s == null || (createdBy != null && !createdBy.equals(s.getCreatedBy()))) {
            throw new BizException(FileErrorCode.SHARE_NOT_FOUND);
        }
        s.setEnabled(false);
        mapper.updateById(s);
        log.info("撤销文件分享：shareId={}", id);
    }

    /** 分享页地址（提取码输入页），对外链接统一指向该页，不再直连下载接口。 */
    public String sharePageUrl(String code) {
        String base = props.getPublicBaseUrl() == null || props.getPublicBaseUrl().isBlank()
                ? "" : props.getPublicBaseUrl();
        return base + "/share/access?code=" + code;
    }

    /** 取分享的公开元信息；不校验提取码，仅用于访问页展示，错误码与下载接口一致。 */
    public ShareInfoVO info(String code) {
        FileShare s = activeShare(code);
        FileObject file = fileService.meta(String.valueOf(s.getFileId()));
        if (file == null) {
            throw new BizException(FileErrorCode.SHARE_NOT_FOUND);
        }
        ShareInfoVO vo = new ShareInfoVO();
        vo.setFileName(file.getOriginalName());
        vo.setNeedPassword(s.getPasswordHash() != null && !s.getPasswordHash().isEmpty());
        vo.setExpiresAt(s.getExpiresAt());
        vo.setMaxViews(s.getMaxViews());
        vo.setViewCount(s.getViewCount());
        return vo;
    }

    /**
     * 凭分享码访问：校验启用/过期/次数/提取码，返回文件并累加查看次数。
     * 返回的 meta 带签名 url，可直接内联或下载。
     */
    @Transactional(rollbackFor = Exception.class)
    public FileObject access(String code, String password) {
        FileShare s = activeShare(code);
        if (s.getPasswordHash() != null && !s.getPasswordHash().isEmpty()) {
            if (password == null || !s.getPasswordHash().equals(sha256(password))) {
                throw new BizException(FileErrorCode.SHARE_PASSWORD_WRONG);
            }
        }
        FileObject file = fileService.meta(String.valueOf(s.getFileId()));
        if (file == null) {
            throw new BizException(FileErrorCode.SHARE_NOT_FOUND);
        }
        s.setViewCount((s.getViewCount() == null ? 0 : s.getViewCount()) + 1);
        mapper.updateById(s);
        log.info("分享访问：shareId={} code={} viewCount={}", s.getId(), code, s.getViewCount());
        return file;
    }

    /** 校验分享处于可用状态（启用/未过期/次数未用尽），原 access 内的前置校验收敛至此。 */
    private FileShare activeShare(String code) {
        FileShare s = mapper.selectOne(new LambdaQueryWrapper<FileShare>()
                .eq(FileShare::getShareCode, code)
                .eq(FileShare::getEnabled, true)
                .last("limit 1"));
        if (s == null) {
            throw new BizException(FileErrorCode.SHARE_NOT_FOUND);
        }
        if (s.getExpiresAt() != null && s.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new BizException(FileErrorCode.SHARE_EXPIRED);
        }
        if (s.getMaxViews() != null && s.getMaxViews() > 0
                && s.getViewCount() != null && s.getViewCount() >= s.getMaxViews()) {
            throw new BizException(FileErrorCode.SHARE_EXHAUSTED);
        }
        return s;
    }

    private String shareUrl(String code) {
        return sharePageUrl(code);
    }

    private String randomCode(int len) {
        StringBuilder sb = new StringBuilder(len);
        for (int i = 0; i < len; i++) {
            sb.append(CODE_CHARS.charAt(RANDOM.nextInt(CODE_CHARS.length())));
        }
        return sb.toString();
    }

    private String sha256(String text) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(digest);
        } catch (Exception e) {
            throw new IllegalStateException("计算提取码摘要失败", e);
        }
    }
}
