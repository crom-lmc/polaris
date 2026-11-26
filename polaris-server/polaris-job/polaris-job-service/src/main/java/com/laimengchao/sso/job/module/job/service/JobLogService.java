package com.laimengchao.sso.job.module.job.service;

import com.laimengchao.sso.common.PageResult;
import com.laimengchao.sso.job.module.job.vo.JobLogVO;
import java.time.LocalDateTime;

public interface JobLogService {

    PageResult<JobLogVO> list(long pageNo, long size, Long jobId, String appCode, Integer status, LocalDateTime startTime, LocalDateTime endTime);

    JobLogVO get(Long id);
}
