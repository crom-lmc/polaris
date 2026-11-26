package com.laimengchao.sso.settings.module.bizdata.service;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.laimengchao.sso.settings.module.bizdata.entity.BizNamed;
import com.laimengchao.sso.settings.module.bizdata.vo.BizCategoryVO;
import com.laimengchao.sso.settings.module.bizdata.vo.BizItemVO;
import java.util.List;

public interface BizItemService {

    /** 一个类别 = 一张真表 + 展示名 */
    public record Category(String type, String name, String remark, BaseMapper<? extends BizNamed> mapper) {
    }

    List<BizCategoryVO> listCategories();

    List<BizItemVO> listItems(String type, Boolean includeDisabled);

    BizItemVO createItem(String type, String code, String name, Integer sort, String remark, String category);

    BizItemVO updateItem(String type, Long id, String name, Integer sort, Integer status, String remark, String category);

    void deleteItem(String type, Long id);

    /**
     * 类别编码 → 启用项列表（保持 sort 排序）。类别不存在/故障返回空列表不抛异常，
     * 调用方拿到空列表走自己的内置兜底（与字典内部接口同一语义）。
     */
    List<BizItemVO> internalItems(String type);
}
