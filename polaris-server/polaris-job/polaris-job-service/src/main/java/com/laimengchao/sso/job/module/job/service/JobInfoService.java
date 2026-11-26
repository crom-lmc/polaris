package com.laimengchao.sso.job.module.job.service;

import com.laimengchao.sso.common.PageResult;
import com.laimengchao.sso.job.module.job.dto.JobSaveRequest;
import com.laimengchao.sso.job.module.job.vo.JobInfoVO;

public interface JobInfoService {

    PageResult<JobInfoVO> list(long pageNo, long size, String keyword, String appCode, Integer status, String triggerType);

    JobInfoVO get(Long id);

    JobInfoVO create(JobSaveRequest request);

    JobInfoVO update(Long id, JobSaveRequest request);

    void delete(Long id);

    JobInfoVO toggleStatus(Long id, Integer status);
}
