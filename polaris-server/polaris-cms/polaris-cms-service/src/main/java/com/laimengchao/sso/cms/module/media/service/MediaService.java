package com.laimengchao.sso.cms.module.media.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.laimengchao.sso.cms.module.media.entity.Media;
import org.springframework.web.multipart.MultipartFile;

public interface MediaService {

    Media upload(MultipartFile file);

    IPage<Media> page(String keyword, int page, int size);

    void delete(Long id);
}
