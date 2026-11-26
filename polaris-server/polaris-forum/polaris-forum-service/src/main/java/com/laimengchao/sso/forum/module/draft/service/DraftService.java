package com.laimengchao.sso.forum.module.draft.service;


import com.laimengchao.sso.forum.module.draft.dto.DraftRequest;
import com.laimengchao.sso.forum.module.draft.vo.DraftVO;
import com.laimengchao.sso.forum.module.forum.vo.ThreadDetailVO;
import java.util.List;


public interface DraftService {

    List<DraftVO> list();

    DraftVO get(long id);

    DraftVO create(DraftRequest body);

    DraftVO update(long id, DraftRequest body);

    void delete(long id);

    ThreadDetailVO publish(long id);
}
