package com.laimengchao.sso.file.client.api;

import com.laimengchao.sso.file.client.config.FileFeignConfig;
import com.laimengchao.sso.file.client.dto.FileAliasRequest;
import com.laimengchao.sso.file.client.dto.FileMeta;
import com.laimengchao.sso.file.client.dto.FileUploadRequest;
import com.laimengchao.sso.file.client.dto.R;
import com.laimengchao.sso.file.client.dto.SignUrlRequest;
import com.laimengchao.sso.file.client.dto.SignUrlResult;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

/**
 * 统一文件服务的服务间 API（CMS/EHR/forum/openapi 等跨服存取文件统一收敛到这里）。
 * 走 {@code /internal/files/**}（不经网关），由 {@link FileFeignConfig} 注入 X-Internal-Token 与超时。
 * <p>
 * 浏览器/第三方对文件的查看与下载不经过本契约——公开文件走 file-service 的公开签名直链，
 * 私有文件走其鉴权下载接口，避免把文件字节回传到调用方服务再转发出去。
 */
@FeignClient(
        name = "file-service",
        contextId = "file-service-client",
        url = "${file.client.base-url:${FILE_SERVICE_URL:http://127.0.0.1:9011}}",
        configuration = FileFeignConfig.class)
public interface FileApi {

    /** 上传文件，返回元数据 */
    @PostMapping("/internal/files/upload")
    R<FileMeta> upload(@RequestBody FileUploadRequest request);

    /** 按 ID 查询元数据，不存在返回 data=null */
    @GetMapping("/internal/files/{id}")
    R<FileMeta> meta(@PathVariable("id") String id);

    /** 批量按 ID 查询元数据 */
    @GetMapping("/internal/files/metas")
    R<List<FileMeta>> metas(@RequestParam("ids") String ids);

    /** 生成临时访问 URL（私有文件用于鉴权下载，公开文件用于签名直链） */
    @PostMapping("/internal/files/{id}/sign")
    R<SignUrlResult> sign(@PathVariable("id") String id, @RequestBody SignUrlRequest request);

    /** 读取文件内容（base64），仅供确实需要字节回传的服务端场景使用 */
    @GetMapping("/internal/files/{id}/content")
    R<String> content(@PathVariable("id") String id);

    /** 软删除 */
    @DeleteMapping("/internal/files/{id}")
    R<Void> delete(@PathVariable("id") String id);

    /** 注册旧路径别名（存量迁移，幂等） */
    @PostMapping("/internal/files/alias/register")
    R<Void> registerAlias(@RequestBody FileAliasRequest request);
}
