package com.laimengchao.sso.cms.module.content.service;

import com.laimengchao.sso.cms.module.content.dto.TagVO;
import java.util.List;

public interface TagService {

    List<TagVO> listAll(String keyword);

    /** 创建（按名+orgCode 去重，存在即返回已有） */
    TagVO create(String name);

    void rename(Long id, String name);

    void delete(Long id);
}
