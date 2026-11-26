package com.laimengchao.sso.audit.module.audit.service;

import com.laimengchao.sso.audit.client.dto.AuditEventRequest;
import com.laimengchao.sso.audit.client.dto.LoginStatsQuery;
import com.laimengchao.sso.audit.client.dto.LoginStatsResult;
import com.laimengchao.sso.audit.module.audit.entity.AuditEvent;
import com.laimengchao.sso.audit.module.audit.vo.LoginEventVO;
import com.laimengchao.sso.common.PageResult;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

public interface AuditEventService {

    /** 批量写入，逐条转换，单条异常不影响同批其他记录。 */
    void recordBatch(List<AuditEventRequest> requests);

    void record(AuditEventRequest req);

    /** 审计事件分页查询（管理端「审计事件」页）。 */
    PageResult<AuditEvent> page(int page, int size, String app, String eventCategory, String action, String operator, String httpMethod, String result, LocalDate startDate, LocalDate endDate);

    /**
     * 审计事件导出：按与列表完全相同的筛选条件取数，条数受全局设置 export.maxRows 限制。
     * 返回条数不超过上限，防止拖库式导出；水印/确认由调用方按全局设置处理。
     */
    List<AuditEvent> export(String app, String eventCategory, String action, String operator, String httpMethod, String result, LocalDate startDate, LocalDate endDate);

    /** 登录审计分页：仍按原 sso_login_log 的字段形状返回，前端不用改列。 */
    PageResult<LoginEventVO> loginPage(int page, int size, String keyword, String loginType);

    /** 审计概览：今日/本周/累计数量、登录失败数、按应用与类别分布、最近 7 天趋势。 */
    Map<String, Object> overview();

    /** 账号中心概览的登录统计：口径与原 sso_login_log 聚合 SQL 一致。 */
    LoginStatsResult loginStats(LoginStatsQuery query);
}
