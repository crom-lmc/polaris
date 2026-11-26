package com.laimengchao.sso.ehr.module.importexport.service;

import com.laimengchao.sso.ehr.module.importexport.vo.ImportVos;
import org.springframework.web.multipart.MultipartFile;

public interface ExcelImportService {

    byte[] template();

    ImportVos.Preview preview(MultipartFile file, Long accountId);

    ImportVos.Result confirm(Long taskId, Long accountId);

    ImportVos.TaskView task(Long taskId, Long accountId);
}
