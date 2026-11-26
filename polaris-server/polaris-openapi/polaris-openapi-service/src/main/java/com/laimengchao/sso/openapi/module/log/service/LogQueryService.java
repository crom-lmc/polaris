package com.laimengchao.sso.openapi.module.log.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.laimengchao.sso.openapi.module.log.entity.ApiCallLog;
import com.laimengchao.sso.openapi.module.log.vo.LogQuery;
import java.util.List;

public interface LogQueryService {

    /** 分页查询。 */
    IPage<ApiCallLog> page(LogQuery q);

    /** 导出：不分页，最多 5000 条，防止一次性拉爆内存。 */
    List<ApiCallLog> export(LogQuery q);

    /** 详情。 */
    ApiCallLog detail(Long id);
}
