package com.laimengchao.sso.finance.module.finance.service;

import com.laimengchao.sso.common.R;
import com.laimengchao.sso.finance.module.finance.dto.FinanceRequests;
import com.laimengchao.sso.finance.module.finance.entity.FinVoucher;
import com.laimengchao.sso.finance.module.finance.vo.FinanceVOs;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public interface VoucherService {

    /** 列表：来源追溯 + 数据范围 */
    R.Page<FinanceVOs.VoucherVO> page(String sourceType, String sourceNo, String status, String keyword, LocalDate from, LocalDate to, int page, int size);

    /** 详情：先列表范围过滤口径，再单条二次校验 */
    FinanceVOs.VoucherVO detail(Long id);

    /** 手工新增：直接生成一张已过账凭证 */
    FinanceVOs.VoucherVO createManual(FinanceRequests.VoucherSave req);

    /** 过账草稿凭证 */
    void post(Long id);

    /**
     * 由业务单据生成凭证并过账；同来源+来源单号幂等，避免重复过账。
     * 供报销、应收应付及 internal 回写统一调用。
     */
    FinVoucher createAndPost(String sourceType, String sourceNo, String summary, String subject, BigDecimal amount, LocalDate date, String creator, String dept, String contractNo, String orgCode);

    /** 跨服回写入口：按业务单幂等生成凭证 */
    FinanceVOs.VoucherVO postFromBiz(FinanceRequests.VoucherPostRequest req);

    /** 按业务单号查凭证（来源追溯，内部接口用） */
    List<FinanceVOs.VoucherVO> bySourceNo(String sourceNo);

    void delete(Long id);
}
