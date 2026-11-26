package com.laimengchao.sso.cms.module.content.service;

import com.laimengchao.sso.cms.module.content.dto.CategorySaveRequest;
import com.laimengchao.sso.cms.module.content.dto.TagVO;
import java.util.List;

public interface CategoryService {

    List<TagVO> listAll();

    void save(CategorySaveRequest req);

    void delete(Long id);
}
