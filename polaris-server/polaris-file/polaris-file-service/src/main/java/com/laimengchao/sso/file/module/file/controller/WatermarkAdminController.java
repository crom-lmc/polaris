package com.laimengchao.sso.file.module.file.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.laimengchao.sso.audit.client.annotation.AuditLog;
import com.laimengchao.sso.common.BizException;
import com.laimengchao.sso.common.R;
import com.laimengchao.sso.common.security.PermissionCodes;
import com.laimengchao.sso.file.common.FileErrorCode;
import com.laimengchao.sso.file.module.file.entity.WatermarkConfig;
import com.laimengchao.sso.file.module.file.mapper.WatermarkConfigMapper;
import com.laimengchao.sso.file.security.Permit;
import com.laimengchao.sso.utils.IdUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;

/** 附件水印配置管理端。 */
@RestController
@RequestMapping("/api/file/admin/watermarks")
@RequiredArgsConstructor
public class WatermarkAdminController {

    private final WatermarkConfigMapper mapper;

    @GetMapping
    @Permit(PermissionCodes.FILE_WATERMARK_VIEW)
    public R<List<WatermarkConfig>> list() {
        return R.ok(mapper.selectList(new LambdaQueryWrapper<WatermarkConfig>()
                .orderByAsc(WatermarkConfig::getBizType)));
    }

    @PostMapping
    @Permit(PermissionCodes.FILE_WATERMARK_EDIT)
    @AuditLog(action = "FILE_WATERMARK_SAVE", targetType = "FILE_WATERMARK")
    public R<WatermarkConfig> save(@RequestBody WatermarkConfig req) {
        validate(req);
        WatermarkConfig existing = mapper.selectOne(new LambdaQueryWrapper<WatermarkConfig>()
                .eq(WatermarkConfig::getBizType, req.getBizType()).last("limit 1"));
        if (existing == null) {
            req.setId(IdUtils.nextId());
            req.setCreatedAt(LocalDateTime.now());
            req.setUpdatedAt(LocalDateTime.now());
            mapper.insert(req);
        } else {
            req.setId(existing.getId());
            req.setCreatedAt(existing.getCreatedAt());
            req.setUpdatedAt(LocalDateTime.now());
            mapper.updateById(req);
        }
        return R.ok(req);
    }

    @DeleteMapping("/{id}")
    @Permit(PermissionCodes.FILE_WATERMARK_EDIT)
    @AuditLog(action = "FILE_WATERMARK_DELETE", targetType = "FILE_WATERMARK")
    public R<Void> delete(@PathVariable("id") String id) {
        try {
            mapper.deleteById(Long.valueOf(id));
        } catch (NumberFormatException e) {
            throw new BizException(FileErrorCode.WATERMARK_NOT_FOUND);
        }
        return R.ok();
    }

    private void validate(WatermarkConfig req) {
        if (req == null || req.getBizType() == null || req.getBizType().isBlank()) {
            throw new BizException(FileErrorCode.WATERMARK_PARAM_INVALID);
        }
        if (req.getContent() == null || req.getContent().isBlank()) {
            throw new BizException(FileErrorCode.WATERMARK_PARAM_INVALID, "水印内容不能为空");
        }
    }
}
