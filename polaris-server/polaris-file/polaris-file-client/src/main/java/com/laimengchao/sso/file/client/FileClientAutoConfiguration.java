package com.laimengchao.sso.file.client;

import com.laimengchao.sso.file.client.api.FileApi;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.cloud.openfeign.EnableFeignClients;

/**
 * 引入 polaris-file-client 即自动装配统一文件服务跨服客户端：
 * <ul>
 *   <li>{@link FileApi}：{@code /internal/files/**} 的上传/元数据/签名/内容/软删，凭 X-Internal-Token 准入。</li>
 * </ul>
 * 地址键：{@code file.client.base-url}（缺省 {@code FILE_SERVICE_URL} / http://127.0.0.1:9011）；
 * 令牌键：{@code file.client.internal-token}（缺省复用 {@code SSO_INTERNAL_TOKEN}）。
 */
@AutoConfiguration
@EnableFeignClients(clients = {FileApi.class})
public class FileClientAutoConfiguration {
}
