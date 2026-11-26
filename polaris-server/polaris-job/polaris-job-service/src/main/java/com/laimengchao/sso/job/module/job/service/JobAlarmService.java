package com.laimengchao.sso.job.module.job.service;

import com.laimengchao.sso.common.PageResult;
import com.laimengchao.sso.job.module.job.dto.AlarmChannelRequest;
import com.laimengchao.sso.job.module.job.dto.AlarmRuleRequest;
import com.laimengchao.sso.job.module.job.entity.JobAlarm;
import com.laimengchao.sso.job.module.job.entity.JobAlarmChannel;
import com.laimengchao.sso.job.module.job.entity.JobInfo;
import com.laimengchao.sso.job.module.job.vo.AlarmRecordVO;
import java.util.List;

public interface JobAlarmService {

    /** 固定返回 SITE / EMAIL 两行，缺失时自动补一条停用记录 */
    List<JobAlarmChannel> listChannels();

    void saveChannels(List<AlarmChannelRequest> items);

    PageResult<JobAlarm> listRules(long pageNo, long size);

    JobAlarm saveRule(Long id, AlarmRuleRequest req);

    void deleteRule(Long id);

    PageResult<AlarmRecordVO> listRecords(long pageNo, long size);

    /** 最终失败（重试次数已用尽）时调用 */
    void onJobFailure(JobInfo job, Integer status, String message);

    /** 执行成功时调用：若上一次是失败报警，发一条「已恢复」 */
    void onJobSuccess(JobInfo job);
}
