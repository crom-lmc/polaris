package com.laimengchao.sso.forum.module.banner.service;


import com.laimengchao.sso.forum.module.attachment.vo.AttachmentVO;
import com.laimengchao.sso.forum.module.banner.dto.BannerRequest;
import com.laimengchao.sso.forum.module.banner.vo.BannerVO;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;


public interface BannerService {

    /** 管理端上传轮播图片：以 COVER 用途存入论坛附件，返回可被公开地址引用的附件信息。 */
    AttachmentVO uploadImage(MultipartFile file);

    List<BannerVO> publicBanners();

    List<BannerVO> adminBanners();

    BannerVO create(BannerRequest body);

    BannerVO update(long id, BannerRequest body);
}
