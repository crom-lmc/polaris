package com.laimengchao.sso.settings.module.approval.service;

import java.util.List;
import java.util.Map;
import java.util.Set;
import com.laimengchao.sso.settings.module.approval.service.impl.BizPointReferenceServiceImpl;

public interface BizPointReferenceService {

    /**
     * 全量引用表：{@code bizType::code -> [引用位置说明, ...]}。
     * 只返回有引用的业务点；没被引用的不出现在 map 里。
     */
    Map<String, List<String>> all();

    /** 单个业务场景的引用表，供保存时校验用 */
    Map<String, List<String>> of(String bizType);

    /** formSchema 里的业务点编码集合；脏 JSON 按「没有业务点」处理，不阻断配置保存 */
    Set<String> parsePointCodes(String formSchema);

    /** 引用表 key，与前端业务点列表的 rowKey 保持同一格式 */
    static String key(String bizType, String code) {
        return BizPointReferenceServiceImpl.key(bizType, code);
    }
}
