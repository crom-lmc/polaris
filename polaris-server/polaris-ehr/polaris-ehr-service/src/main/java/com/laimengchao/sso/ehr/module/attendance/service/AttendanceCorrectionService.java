package com.laimengchao.sso.ehr.module.attendance.service;

import com.laimengchao.sso.common.R;
import com.laimengchao.sso.ehr.module.attendance.dto.AttendanceCorrectionApproveRequest;
import com.laimengchao.sso.ehr.module.attendance.dto.AttendanceCorrectionRequest;
import com.laimengchao.sso.ehr.module.attendance.vo.AttendanceCorrectionVO;

public interface AttendanceCorrectionService {

    AttendanceCorrectionVO apply(AttendanceCorrectionRequest req);

    R.Page<AttendanceCorrectionVO> page(Integer status, String month, int page, int size);

    AttendanceCorrectionVO approve(Long id, AttendanceCorrectionApproveRequest req);

    AttendanceCorrectionVO reject(Long id, AttendanceCorrectionApproveRequest req);

    void cancel(Long id);
}
