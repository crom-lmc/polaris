package com.laimengchao.sso.forum.module.interaction.service;


import com.laimengchao.sso.forum.module.forum.vo.ThreadVO;
import com.laimengchao.sso.forum.module.interaction.vo.FavoriteToggleVO;
import java.util.List;





public interface FavoriteService {

    FavoriteToggleVO add(long threadId);

    FavoriteToggleVO remove(long threadId);

    List<ThreadVO> list();

    int count(long accountId);
}
