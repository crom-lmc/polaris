package com.laimengchao.sso.settings.module.dict.service;

import com.laimengchao.sso.settings.module.dict.vo.DictItemVO;
import com.laimengchao.sso.settings.module.dict.vo.DictTypeVO;
import java.util.List;
import java.util.Map;

public interface DictService {

    List<DictTypeVO> listTypes(String keyword);

    DictTypeVO createType(String code, String name, String appCode, String remark);

    DictTypeVO updateType(Long id, String name, String appCode, String remark, Integer status);

    void deleteType(Long id);

    List<DictItemVO> listItems(String typeCode, Boolean includeDisabled);

    DictItemVO createItem(String typeCode, String value, String label, Integer sort, String remark);

    DictItemVO updateItem(Long id, String label, Integer sort, Integer status, String remark);

    void deleteItem(Long id);

    /**
     * 类型编码 → 启用项列表（value/label/sort）。类型不存在或已停用返回空列表，
     * 不抛异常——调用方拿到空列表自然走自己的兜底。
     */
    List<DictItemVO> internalItems(String typeCode);

    /** 批量：typeCodes → { code: items }，减少调用方启动时的往返次数 */
    Map<String, List<DictItemVO>> internalBatch(List<String> typeCodes);
}
