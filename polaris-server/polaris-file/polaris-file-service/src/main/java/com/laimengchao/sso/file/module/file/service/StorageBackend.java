package com.laimengchao.sso.file.module.file.service;

/**
 * 存储后端抽象。FileService 只依赖本接口，不关心字节落在本地磁盘还是对象存储。
 * <p>
 * 目前唯一实现是 {@link LocalStorageService}。未来接 MinIO/S3/OSS 时新增一个实现类，
 * 用 {@code @ConditionalOnProperty(file.storage.backend=s3)} 之类切换即可，FileService 与控制器无需改动。
 * 契约：write 返回后端可定位字节的「相对路径/对象键」，原样存入 file_object.storage_path，
 * 后续 read/delete/exists 均以该值回读。
 */
public interface StorageBackend {

    /** 写入字节并返回定位键。 */
    String write(byte[] bytes, String originalName);

    /** 按定位键读取字节；不存在返回 null。 */
    byte[] read(String key);

    /** 按定位键物理删除；返回是否成功删除（不存在返回 false）。 */
    boolean delete(String key);

    /** 定位键对应的字节是否仍存在。 */
    boolean exists(String key);
}
