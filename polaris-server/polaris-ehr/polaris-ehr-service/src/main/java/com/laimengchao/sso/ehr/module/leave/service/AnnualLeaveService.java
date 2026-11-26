package com.laimengchao.sso.ehr.module.leave.service;

import com.laimengchao.sso.common.R;
import com.laimengchao.sso.ehr.module.employee.entity.Employee;
import com.laimengchao.sso.ehr.module.leave.entity.AnnualLeavePolicy;
import com.laimengchao.sso.ehr.module.leave.entity.LeaveQuota;
import com.laimengchao.sso.ehr.module.leave.vo.AnnualLeaveLedgerVO;

import java.math.BigDecimal;
import java.util.List;

public interface AnnualLeaveService {

    /** 折算结果 */
    public static class CalcResult {
        public BigDecimal days = BigDecimal.ZERO;
        public String detail = "";
    }

    List<AnnualLeavePolicy> listPolicies();

    /**
     * 新增 / 更新策略。enabled=1 时自动停用其他策略，保证全局只有一条启用。
     * 更新时未传字段保持原值（updateById 跳过 null）。
     */
    AnnualLeavePolicy savePolicy(AnnualLeavePolicy policy);

    AnnualLeavePolicy enablePolicy(Long id);

    /** 取启用中的策略；未配置时使用法定默认档位 */
    AnnualLeavePolicy activePolicy();

    /**
     * 生成某员工某自然年的年假额度：按统一折算公式计算当年产生额度，再全额结转上年剩余（无上限）。
     * 同时给上一年额度写「结转剩余年底作废」的 SETTLE 流水（幂等）。
     */
    LeaveQuota generateYear(Long employeeId, int year);

    /**
     * 统一折算公式：返回当年产生年假天数与计算说明。
     */
    CalcResult calcAnnualDays(Employee emp, int year, AnnualLeavePolicy policy);

    /** 手动生成全员当年额度（联调用），返回本次新建数量 */
    int generateAll(int year);

    /**
     * 每日 02:30（调度入口见 schedule 包的 AnnualLeaveRefreshJob）：
     * 缺失额度补建；source=AUTO 的额度按新规则重算（年中入职/离职/跨档/条例排除都会反映），
     * 重算值 = 折算天数 + 结转 - 已签放弃，下限为 已用+冻结（提前离职多休部分从工资扣回，不回收为负）。
     */
    void dailyRefresh();

    /**
     * 重算某年度全员额度（admin 手动触发 / 每日调度）。
     *
     * @param includeResigned 是否包含已离职员工（年度重算历史数据时为 true）
     * @return 新建 + 重算有变化的额度条数
     */
    int recalculateYear(int year, boolean includeResigned);

    /** 该额度已签署放弃的结转天数合计 */
    BigDecimal waivedDays(Long quotaId);

    /**
     * 年假台账：在职员工按年展示「当年产生 / 上年结转 / 已用 / 剩余」。
     * 当年产生天数用统一折算公式实时计算，不依赖已生成额度（未生成的也会显示应产生天数）。
     */
    R.Page<AnnualLeaveLedgerVO> pageLedger(String keyword, Integer year, int page, int size);

    /**
     * 年假档位文案：按当年在册区间的起止工龄档位展示（5 / 10 / 15；年内跨档显示 5→10）。
     * 当年无在册资格区间（未满 1 年 / 已离职）返回 “—”。
     */
    String tierText(Employee emp, int year, AnnualLeavePolicy policy);
}
