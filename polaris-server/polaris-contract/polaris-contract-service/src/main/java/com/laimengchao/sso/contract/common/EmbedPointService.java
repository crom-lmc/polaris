package com.laimengchao.sso.contract.common;





public interface EmbedPointService {

    /** 采购合同审批升级线（元），默认 100000；非法回退默认 */
    int purchaseApprovalAmount();

    /** 付款计划审批升级线（元），默认 50000；非法回退默认 */
    int paymentApprovalAmount();

    /** 销售合同是否开启信用额度校验，默认 false（保护存量，开启后才拦截） */
    boolean salesCreditCheck();

    /** 合同变更次数上限，默认 3；小于 0 视为不限 */
    int changeMaxTimes();

    /** 付款前是否必须完成验收，默认 true（fail-closed，防未验收先付款） */
    boolean acceptanceRequiredBeforePay();

    /** 付款到期提前提醒天数，默认 7，合法范围 1-365 */
    int paymentRemindDays();

    /**
     * 物料编码规则埋点。返回 [levels, width, serial]；解析失败或缺失返回 null。
     * 合法性（是否允许改变存量编码结构）由调用方判断，避免埋点误配破坏已有编码。
     */
    int[] materialCodePattern();

    /** 读取原始埋点值；读不到返回 null（已记 WARN） */
    String raw(String code);

    /**
     * 布尔开关：兼容裸值 "true"/"false"、JSON 布尔与对象字段三种写法。
     * 任何异常都回落到 defaultValue，调用方按业务语义选择 fail-closed 的默认值。
     */
    boolean bool(String code, boolean defaultValue);

    /** 整数阈值；缺失或非法返回 defaultValue */
    int number(String code, int defaultValue);
}
