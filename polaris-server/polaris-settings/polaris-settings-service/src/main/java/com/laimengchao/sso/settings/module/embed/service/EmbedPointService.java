package com.laimengchao.sso.settings.module.embed.service;

import com.laimengchao.sso.settings.module.embed.dto.EmbedPointCreate;
import com.laimengchao.sso.settings.module.embed.dto.EmbedPointDTO;
import com.laimengchao.sso.settings.module.embed.dto.EmbedPointPatch;
import com.laimengchao.sso.settings.module.embed.vo.EmbedPointHistoryVO;
import com.laimengchao.sso.settings.module.embed.vo.EmbedPointVO;
import java.util.List;

public interface EmbedPointService {

    List<EmbedPointVO> list(String keyword);

    EmbedPointVO create(EmbedPointCreate req);

    EmbedPointVO update(Long id, EmbedPointPatch patch);

    List<EmbedPointHistoryVO> histories(Long pointId);

    /** 跨服务按 key 取值，不存在返回 null（调用方走自身兜底） */
    EmbedPointDTO getByCode(String code);

    List<EmbedPointDTO> listAll();
}
