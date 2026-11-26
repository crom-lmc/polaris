package com.laimengchao.sso.forum.module.report.service;


import com.laimengchao.sso.forum.module.report.dto.ReportCreateRequest;
import com.laimengchao.sso.forum.module.report.dto.ReportHandleRequest;
import com.laimengchao.sso.forum.module.report.vo.ReportVO;
import java.util.List;


public interface ReportService {

    ReportVO create(ReportCreateRequest body);

    List<ReportVO> list(String status, String targetType);

    ReportVO handle(long id, ReportHandleRequest body);
}
