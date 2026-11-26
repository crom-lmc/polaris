package com.laimengchao.sso.file.module.file.service;

import com.laimengchao.sso.file.module.file.entity.FileObject;
import com.laimengchao.sso.file.module.file.vo.MultipartInitRequest;
import java.util.Map;

public interface MultipartUploadService {

    /** 初始化上传会话，返回 uploadId / chunkSize / 期望分片数。 */
    Map<String, Object> init(MultipartInitRequest req);

    /** 上传单片。index 从 0 开始；重复上传同片以最后一次为准（幂等覆盖）。 */
    void part(String uploadId, int index, String base64);

    /** 合并所有分片并走正常上传链路；成功后清理会话与临时目录。 */
    FileObject complete(String uploadId, Long uploaderId);

    /** 会话总字节数（前端断点续传进度用，返回各片大小） */
    Map<String, Object> status(String uploadId);

    /** 定时兜底清理过期会话（服务重启后残留的临时目录）。 */
    void cleanupExpired();
}
