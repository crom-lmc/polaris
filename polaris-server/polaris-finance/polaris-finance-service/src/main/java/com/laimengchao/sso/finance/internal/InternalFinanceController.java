package com.laimengchao.sso.finance.internal;

import com.laimengchao.sso.common.R;
import com.laimengchao.sso.finance.module.finance.dto.FinanceRequests;
import com.laimengchao.sso.finance.module.finance.service.ArApService;
import com.laimengchao.sso.finance.module.finance.service.PaymentService;
import com.laimengchao.sso.finance.module.finance.vo.FinanceVOs;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 合同域只读/写入口：应付单按合同查询、应收创建与核销、付款单创建。
 * 无 @Permit，由 common 的 InternalTokenInterceptor 统一校验 X-Internal-Token（fail-closed）。
 */
@RestController
@RequestMapping("/internal/finance")
@RequiredArgsConstructor
public class InternalFinanceController {

    private final ArApService arApService;
    private final PaymentService paymentService;

    /**
     * 合同已付回显：按合同号查应付单。
     * /ar-ap/payables 供管理端内部追溯；/payables/by-contract/{contractNo} 是 polaris-finance-client/PayableApi 契约。
     */
    @GetMapping("/ar-ap/payables")
    public R<List<FinanceVOs.ArApVO>> payablesByContract(@RequestParam("contractNo") String contractNo) {
        return R.ok(arApService.payablesByContract(contractNo));
    }

    /** polaris-finance-client/PayableApi 契约路径 */
    @GetMapping("/payables/by-contract/{contractNo}")
    public R<List<FinanceVOs.ArApVO>> payablesByContractPath(@PathVariable("contractNo") String contractNo) {
        return R.ok(arApService.payablesByContract(contractNo));
    }

    /**
     * 销售合同验收推应收。
     * /receivables/create 是 polaris-finance-client/ReceivableApi 契约；
     * client 的 ReceivableCreateRequest 无 acceptanceNo，按 contractNo 建来源追溯（已生成验收单时由合同域写 acceptanceNo 到 summary）。
     */
    @PostMapping("/receivables")
    public R<FinanceVOs.ArApVO> createReceivable(@RequestBody FinanceRequests.AcceptanceArRequest req) {
        return R.ok(arApService.createFromAcceptance(req));
    }

    /** client 契约：无验收单号时按普通应收登记（合同域验收已由 /receivables 路径覆盖） */
    @PostMapping("/receivables/create")
    public R<FinanceVOs.ArApVO> createReceivableByContract(@RequestBody java.util.Map<String, Object> body) {
        FinanceRequests.ArApSave req = new FinanceRequests.ArApSave();
        req.setParty(str(body.get("party")));
        req.setAmount(dec(body.get("amount")));
        req.setContractNo(str(body.get("contractNo")));
        req.setSummary(str(body.get("summary")));
        req.setType("应收");
        req.setSubject("主营业务收入");
        req.setDate(parseDate(body.get("date")));
        req.setOrgCode(str(body.get("orgCode")));
        return R.ok(arApService.create(req));
    }

    private String str(Object v) { return v == null ? null : String.valueOf(v).trim(); }
    private java.math.BigDecimal dec(Object v) {
        if (v == null) return null;
        try { return new java.math.BigDecimal(String.valueOf(v)); } catch (Exception e) { return null; }
    }
    private java.time.LocalDate parseDate(Object v) {
        if (v == null || String.valueOf(v).isBlank()) return null;
        try { return java.time.LocalDate.parse(String.valueOf(v).substring(0, 10)); } catch (Exception e) { return null; }
    }

    /**
     * 应收核销：FIFO / MANUAL 由埋点决定。
     * 两条路径并存：/{no}/write-off 由管理端调用，/write-off 是 client 契约（单号放请求体）。
     */
    @PostMapping("/receivables/{no}/write-off")
    public R<Void> writeOff(@PathVariable("no") String no,
                            @RequestBody FinanceRequests.WriteOffRequest req) {
        arApService.writeOffByNo(no, req);
        return R.ok();
    }

    /** polaris-finance-client/ReceivableApi 契约路径：单号在请求体 targetNo */
    @PostMapping("/receivables/write-off")
    public R<Void> writeOffByBody(@RequestBody FinanceRequests.WriteOffRequest req) {
        String no = req.getTargetNo() != null && !req.getTargetNo().isBlank() ? req.getTargetNo() : req.getNo();
        arApService.writeOffByNo(no, req);
        return R.ok();
    }

    /** 合同付款申请生成付款单（FK 单号由 settings 取号） */
    @PostMapping("/payments")
    public R<FinanceVOs.PaymentVO> createPayment(@RequestBody FinanceRequests.PaymentSave req) {
        return R.ok(paymentService.create(req));
    }

    /** client 契约：按合同付款申请登记应付/付款单 */
    @PostMapping("/payables/create")
    public R<FinanceVOs.PaymentVO> createPayableByContract(@RequestBody java.util.Map<String, Object> body) {
        FinanceRequests.PaymentSave req = new FinanceRequests.PaymentSave();
        req.setContractNo(str(body.get("contractNo")));
        req.setParty(str(body.get("party")));
        req.setAmount(dec(body.get("amount")));
        req.setDate(parseDate(body.get("dueDate")));
        req.setOrgCode(str(body.get("orgCode")));
        return R.ok(paymentService.create(req));
    }
}
