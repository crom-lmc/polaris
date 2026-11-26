package com.laimengchao.sso.finance.common;





public interface BizNoService {

    /**
     * 取下一个单号。空号/异常直接抛业务错误（fail-closed）。
     */
    String next(String tableName);

    /**
     * 应付单号：配置中心 fin_ar_ap 规则产出 YS 前缀，按单据类型换成 YF。
     * 流水仍来自取号服务，业务侧只做前缀语义修正，不自行拼号。
     */
    String nextPayable();
}
