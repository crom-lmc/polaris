package com.laimengchao.sso.finance.common;


import java.util.List;





public interface EmbedPointService {

    /** 审批通过自动生成凭证，默认 false（保留手动过账动作） */
    boolean autoPost();

    /** 核销方式 FIFO / MANUAL，默认 FIFO */
    String writeoffMode();

    /** 预算执行率预警线（%），默认 80 */
    int alertRatio();

    /** 是否校验凭证期间（默认 true，fail-closed 侧：开启才拦截） */
    boolean periodCheck();

    /** 次月关账日（默认 5 号），范围 1-28 防止月底日期溢出 */
    int closeDay();

    /** 报销高级审批线（元）；金额为负或不可解析时回退默认 5000 */
    int expenseApprovalAmount();

    /** 预算控制模式 REMIND / STRICT，默认 REMIND（提醒不拦截） */
    String budgetControlMode();

    /** 应收账龄分档上界（天），默认 [30,60,90,180,365] */
    List<Integer> agingBuckets();
}
