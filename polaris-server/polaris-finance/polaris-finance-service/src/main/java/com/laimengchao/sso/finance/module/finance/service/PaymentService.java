package com.laimengchao.sso.finance.module.finance.service;

import com.laimengchao.sso.common.R;
import com.laimengchao.sso.finance.module.finance.dto.FinanceRequests;
import com.laimengchao.sso.finance.module.finance.vo.FinanceVOs;

public interface PaymentService {

    R.Page<FinanceVOs.PaymentVO> page(String contractNo, String status, String keyword, int page, int size);

    FinanceVOs.PaymentVO detail(Long id);

    /** 手工/跨服创建：合同付款申请生成付款单 */
    FinanceVOs.PaymentVO create(FinanceRequests.PaymentSave req);

    void pay(Long id);

    /** 支付并记录过账凭证号 */
    void pay(Long id, String voucherNo);

    void cancel(Long id);
}
