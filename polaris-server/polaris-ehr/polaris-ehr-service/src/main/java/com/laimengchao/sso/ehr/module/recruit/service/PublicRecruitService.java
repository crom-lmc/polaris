package com.laimengchao.sso.ehr.module.recruit.service;

import com.laimengchao.sso.ehr.module.recruit.vo.ApplyProgressVO;
import com.laimengchao.sso.ehr.module.recruit.vo.JobPostVO;
import com.laimengchao.sso.common.R;
import org.springframework.web.multipart.MultipartFile;

public interface PublicRecruitService {

    R.Page<JobPostVO> jobs(String keyword, String jobType, int page, int size);

    JobPostVO job(Long id);

    ApplyProgressVO apply(Long jobId, MultipartFile resume, String name, String mobile, String email, String education, String school, String major, String workYears, String currentCompany, String expectedSalary, String remark, Boolean consent, String ip);

    ApplyProgressVO progress(String trackCode, String mobile, String ip);
}
