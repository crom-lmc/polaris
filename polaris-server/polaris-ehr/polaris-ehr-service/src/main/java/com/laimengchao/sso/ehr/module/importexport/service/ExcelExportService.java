package com.laimengchao.sso.ehr.module.importexport.service;

public interface ExcelExportService {

    byte[] employees(String keyword, Long orgId, Integer status);

    byte[] contracts();

    byte[] attendance(String month);

}
