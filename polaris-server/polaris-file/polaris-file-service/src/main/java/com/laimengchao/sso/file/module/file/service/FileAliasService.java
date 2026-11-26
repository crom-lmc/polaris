package com.laimengchao.sso.file.module.file.service;

public interface FileAliasService {

    /** 注册别名；同路径重复注册时覆盖 fileId（幂等）。 */
    void register(String oldPath, String fileId, String sourceApp);

    /** 按旧路径读取文件内容；无映射或文件已删返回 null。 */
    byte[] resolve(String oldPath);

    /** 解析旧文件对应的 MIME 类型（供 legacy 端点回 Content-Type）。 */
    String resolveContentType(String oldPath);
}
