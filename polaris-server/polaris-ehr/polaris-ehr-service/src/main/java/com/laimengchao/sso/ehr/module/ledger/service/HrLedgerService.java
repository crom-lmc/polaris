package com.laimengchao.sso.ehr.module.ledger.service;

import com.laimengchao.sso.ehr.module.ledger.vo.HrLedgerVO.AttendanceLedger;
import com.laimengchao.sso.ehr.module.ledger.vo.HrLedgerVO.ContractLedger;
import com.laimengchao.sso.ehr.module.ledger.vo.HrLedgerVO.LeaveLedger;
import com.laimengchao.sso.ehr.module.ledger.vo.HrLedgerVO.MovementLedger;
import com.laimengchao.sso.ehr.module.ledger.vo.HrLedgerVO.OvertimeLedger;
import com.laimengchao.sso.ehr.module.ledger.vo.HrLedgerVO.RecruitmentLedger;
import com.laimengchao.sso.ehr.module.ledger.vo.HrLedgerVO.RegularizationLedger;
import com.laimengchao.sso.ehr.module.ledger.vo.HrLedgerVO.WorkforceLedger;

public interface HrLedgerService {

    ContractLedger contracts(Long orgId, String expiry, String type, String keyword, int page, int size);

    MovementLedger movement(String fromMonth, String toMonth, Long orgId, String event, int page, int size);

    AttendanceLedger attendance(String month, Long orgId, String type, String status, String keyword, int page, int size);

    WorkforceLedger workforce(Long orgId, String status, String position, String keyword, int page, int size);

    RegularizationLedger regularization(Long orgId, String expiry, String approvalStatus, String keyword, int page, int size);

    LeaveLedger leaves(String from, String to, Long orgId, String type, String status, String keyword, int page, int size);

    OvertimeLedger overtimes(String from, String to, Long orgId, String type, String status, String settleStatus, String keyword, int page, int size);

    RecruitmentLedger recruitment(String from, String to, Long orgId, String stage, String source, String keyword, int page, int size);
}
