package com.laimengchao.sso.file.module.share.service;

import com.laimengchao.sso.file.module.file.entity.FileObject;
import com.laimengchao.sso.file.module.share.entity.FileShare;
import com.laimengchao.sso.file.module.share.vo.ShareCreateRequest;
import com.laimengchao.sso.file.module.share.vo.ShareInfoVO;
import java.util.List;

public interface ShareService {

    /** 创建分享；提取码只存 SHA256 哈希，明文仅在响应中返回一次。 */
    FileShare create(ShareCreateRequest req, Long createdBy);

    /** 我的分享列表。 */
    List<FileShare> list(Long createdBy);

    /** 撤销分享（逻辑置 enabled=false）。 */
    void revoke(Long id, Long createdBy);

    /** 分享页地址（提取码输入页），对外链接统一指向该页，不再直连下载接口。 */
    String sharePageUrl(String code);

    /** 取分享的公开元信息；不校验提取码，仅用于访问页展示，错误码与下载接口一致。 */
    ShareInfoVO info(String code);

    /**
     * 凭分享码访问：校验启用/过期/次数/提取码，返回文件并累加查看次数。
     * 返回的 meta 带签名 url，可直接内联或下载。
     */
    FileObject access(String code, String password);
}
