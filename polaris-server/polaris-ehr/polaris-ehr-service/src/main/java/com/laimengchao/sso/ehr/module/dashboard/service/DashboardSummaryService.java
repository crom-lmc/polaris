package com.laimengchao.sso.ehr.module.dashboard.service;

import com.laimengchao.sso.ehr.module.dashboard.vo.DashboardSummaryVO;
import java.time.LocalDate;

public interface DashboardSummaryService {

    DashboardSummaryVO summary(Long orgId, LocalDate from, LocalDate to);
}
