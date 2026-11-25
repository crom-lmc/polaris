package com.laimengchao.sso.file.common;

import com.laimengchao.sso.common.IErrorCode;
import lombok.Getter;

/** 文件服务业务码段（8xxx）。 */
@Getter
public enum FileErrorCode implements IErrorCode {

    FILE_NOT_FOUND(8101, "文件不存在或已删除"),
    POLICY_NOT_FOUND(8102, "业务类型未配置存储策略"),
    FILE_TOO_LARGE(8103, "文件大小超出策略上限"),
    EXT_NOT_ALLOWED(8104, "文件类型不允许上传"),
    SIGN_INVALID(8105, "访问签名无效或已过期"),
    FORBIDDEN_PRIVATE(8106, "私有文件需要登录后访问"),
    STORAGE_ERROR(8107, "文件存储读写失败"),
    BAD_BASE64(8108, "文件内容不是合法的 base64 编码"),
    POLICY_INVALID(8109, "存储策略参数不合法"),
    POLICY_EXISTS(8110, "业务类型已存在"),
    STATS_PARAM_INVALID(8111, "统计时间范围不合法"),
    FILE_NOT_DELETED(8112, "文件未处于回收站，无法恢复"),
    FILE_RESTORE_PATH_LOST(8113, "物理文件已丢失，无法恢复"),
    FILE_STILL_REFERENCED(8114, "存在秒传复用的关联文件，无法彻底删除"),
    MULTIPART_SESSION_INVALID(8115, "分片上传会话不存在或已过期"),
    MULTIPART_INDEX_INVALID(8116, "分片序号超出范围"),
    MULTIPART_INCOMPLETE(8117, "分片未全部上传完成"),
    SHARE_NOT_FOUND(8118, "分享链接不存在或已失效"),
    SHARE_PASSWORD_WRONG(8119, "分享提取码错误"),
    SHARE_EXPIRED(8120, "分享链接已过期"),
    SHARE_EXHAUSTED(8121, "分享次数已用尽"),
    QUOTA_EXCEEDED(8122, "业务存储配额已用尽"),
    SHARE_PARAM_INVALID(8123, "有效期与查看次数留空表示不限，填写则必须大于 0"),
    WATERMARK_PARAM_INVALID(8124, "水印配置参数不合法"),
    WATERMARK_NOT_FOUND(8125, "水印配置不存在");

    private final int code;
    private final String message;

    FileErrorCode(int code, String message) {
        this.code = code;
        this.message = message;
    }

    @Override
    public int httpStatus() {
        switch (code) {
            case 8101:
            case 8102:
                return 404;
            case 8105:
            case 8106:
                return 403;
            case 8103:
            case 8104:
            case 8108:
            case 8109:
            case 8110:
            case 8111:
            case 8112:
            case 8113:
            case 8114:
            case 8116:
            case 8117:
            case 8122:
            case 8123:
            case 8124:
                return 400;
            case 8115:
            case 8118:
            case 8125:
                return 404;
            case 8119:
            case 8120:
            case 8121:
                return 403;
            default:
                return 0;
        }
    }
}
