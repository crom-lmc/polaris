package com.laimengchao.sso.cms.module.banner.service;

import com.laimengchao.sso.cms.module.banner.entity.Banner;
import java.util.List;

public interface BannerService {

    List<Banner> listAll();

    Banner save(Banner banner);

    void delete(Long id);
}
