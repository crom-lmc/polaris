package com.laimengchao.sso.file.module.file.service;

import com.laimengchao.sso.file.module.file.service.impl.ThumbnailServiceImpl;

public interface ThumbnailService {

    /**
     * 等比缩放到最大边 maxEdge；非图片或处理失败时返回原字节。
     */
    byte[] scale(byte[] src, int maxEdge);

    /** 是否为可做缩略图的位图 MIME。 */
    static boolean isRasterImage(String contentType) {
        return ThumbnailServiceImpl.isRasterImage(contentType);
    }
}
