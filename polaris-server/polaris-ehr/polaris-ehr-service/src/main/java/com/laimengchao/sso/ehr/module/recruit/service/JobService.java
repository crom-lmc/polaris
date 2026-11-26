package com.laimengchao.sso.ehr.module.recruit.service;

import com.laimengchao.sso.common.R;
import com.laimengchao.sso.ehr.module.recruit.dto.JobPostRequest;
import com.laimengchao.sso.ehr.module.recruit.entity.JobPost;
import com.laimengchao.sso.ehr.module.recruit.vo.JobPostVO;

public interface JobService {

    R.Page<JobPostVO> page(String status, String keyword, int page, int size);

    /** 门户职位列表：只返回已发布且未截止的职位 */
    R.Page<JobPostVO> publicPage(String keyword, String jobType, int page, int size);

    JobPostVO publicDetail(Long id);

    JobPostVO detail(Long id);

    JobPostVO create(JobPostRequest req);

    JobPostVO update(Long id, JobPostRequest req);

    void publish(Long id);

    void close(Long id);

    void delete(Long id);

    JobPost requirePublished(Long id);
}
