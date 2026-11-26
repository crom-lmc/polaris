package com.laimengchao.sso.job.module.job.service;

import com.laimengchao.sso.job.module.job.entity.JobInfo;
import java.time.LocalDateTime;

public interface JobDispatchService {

    /**
     * 阻塞策略判定用：同一任务是否还有执行中的实例。
     * 本机内存命中即可；未命中再查 job_log 里未回写的执行中记录，让 SERIAL/COVER 在多实例下同样生效。
     */
    boolean isRunning(JobInfo job);

    /** 手动执行端点用：按 ID 取任务 */
    JobInfo getJob(Long jobId);

    /** 同步执行一个任务（由 JobScheduler 的线程池调用） */
    void run(JobInfo job, LocalDateTime plannedAt);

    void shutdown();
}
