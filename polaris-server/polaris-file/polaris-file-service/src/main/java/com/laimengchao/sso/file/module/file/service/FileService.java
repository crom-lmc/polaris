package com.laimengchao.sso.file.module.file.service;

import com.laimengchao.sso.common.PageResult;
import com.laimengchao.sso.file.module.file.entity.FileObject;
import com.laimengchao.sso.file.module.file.entity.StoragePolicy;
import java.util.List;
import java.util.Map;

public interface FileService {

    /** 上传：校验策略 → 内容去重/落盘 → 写元数据，返回带访问 URL 的元数据。 */
    FileObject upload(String bizType, String visibility, String originalName, String contentType, byte[] bytes, String ownerType, String ownerId, Long uploaderId);

    /** 查询单个元数据；不存在或已软删返回 null。 */
    FileObject meta(String id);

    List<FileObject> metas(String ids);

    /** 读取文件内容。校验签名由控制器完成，这里只保证文件存在。 */
    byte[] content(String id);

    /** 生成签名 URL。 */
    String[] sign(String id, String purpose, Long expiresSeconds);

    /** 软删除：仅改元数据状态，字节保留（秒传可能共享，物理删除走 purge 并做引用检查）。 */
    void delete(String id);

    /** 回收站分页：查询已软删的文件。 */
    PageResult<FileObject> recycleBin(int page, int size, String bizType, String keyword);

    /** 从回收站恢复：校验处于 DELETED 且物理字节仍在。 */
    void restore(String id);

    /**
     * 彻底删除：物理删字节 + 删元数据。秒传场景下多个元数据可能共享同一字节，
     * 若仍有其它记录（正常或回收站）引用该 path，则只删本条元数据，保留物理文件。
     */
    void purge(String id);

    /**
     * 生命周期清理：按各业务策略 ttlDays，把超期的 NORMAL 文件移入回收站（软删，可恢复）。
     * 由定时任务调用；返回本轮处理条数。
     */
    int expireByTtl();

    /**
     * 回收站自动清理：软删超过“附件设置”recycle.keepDays 的文件物理删除（复用秒传引用检查）。
     * keepDays<=0 表示保留、不清理。返回本轮清理条数。
     */
    int purgeExpiredRecycle();

    /** 管理端分页。 */
    PageResult<FileObject> page(int page, int size, String bizType, String visibility, String keyword);

    List<StoragePolicy> policies();

    void savePolicy(StoragePolicy policy);

    /** 存储概览统计：总量、可见性分布、业务类型分布、近 10 天上传趋势、最近上传。 */
    java.util.Map<String, Object> overview();

    /**
     * 按天统计上传量（抽屉自定义时间范围用）。
     * 日期含首尾，缺失的日期补 0；范围上限 366 天，避免一次扫描过大。
     */
    List<Map<String, Object>> uploadTrend(String startDate, String endDate);
}
