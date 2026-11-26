package com.laimengchao.sso.file.module.file.service;

public interface LocalStorageService {

    /** 写入文件并返回相对存储路径。 */
    String write(byte[] bytes, String originalName);

    /** 读取文件字节；不存在返回 null。 */
    byte[] read(String rel);

    /** 判断物理字节是否仍存在（回收站恢复前校验用）。 */
    boolean exists(String rel);

    /** 物理删除（软删标记由 FileService 更新元数据后，孤儿清理任务调用本方法）。 */
    boolean delete(String rel);
}
