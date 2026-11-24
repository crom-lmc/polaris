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
    STATS_PARAM_INVALID(8111, "统计时间范围不合法");

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
                return 400;
            default:
                return 0;
        }
    }
}
