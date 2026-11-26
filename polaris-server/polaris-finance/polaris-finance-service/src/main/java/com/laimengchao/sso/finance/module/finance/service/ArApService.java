package com.laimengchao.sso.finance.module.finance.service;

import com.laimengchao.sso.common.R;
import com.laimengchao.sso.finance.module.finance.dto.FinanceRequests;
import com.laimengchao.sso.finance.module.finance.vo.FinanceVOs;
import java.time.LocalDate;
import java.util.List;

public interface ArApService {

    R.Page<FinanceVOs.ArApVO> page(String type, String status, String keyword, String contractNo, LocalDate from, LocalDate to, int page, int size);

    FinanceVOs.ArApVO detail(Long id);

    FinanceVOs.ArApVO create(FinanceRequests.ArApSave req);

    void approve(Long id);

    void post(Long id);

    /**
     * 核销：FIFO 按日期从早到晚冲抵同单位未核销单据；MANUAL 指定目标单号。
     * 全部核销完的单据状态置 PAID，不再计入往来余额与账龄。
     */
    void writeOff(Long id, FinanceRequests.WriteOffRequest req);

    /** 跨服核销入口：按单号定位后复用同一套核销规则 */
    void writeOffByNo(String no, FinanceRequests.WriteOffRequest req);

    /**
     * 销售合同验收推应收：先读合同域验收单（读失败按默认值兜底），
     * 验收不合格直接拒绝；请求参数优先，缺失部分用验收单补齐。
     */
    FinanceVOs.ArApVO createFromAcceptance(FinanceRequests.AcceptanceArRequest req);

    /** 应收账龄：分档来自埋点 finance.aging.buckets，未核销（APPROVED/POSTED）才计入 */
    List<FinanceVOs.AgingBucketVO> aging();

    /** 合同域按合同号查应付单（系统间调用，不经用户数据范围） */
    List<FinanceVOs.ArApVO> payablesByContract(String contractNo);

    void delete(Long id);
}
