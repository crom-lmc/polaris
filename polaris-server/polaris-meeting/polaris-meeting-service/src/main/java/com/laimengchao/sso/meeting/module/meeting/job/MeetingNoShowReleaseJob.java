package com.laimengchao.sso.meeting.module.meeting.job;

import com.laimengchao.sso.job.client.dto.JobResult;
import com.laimengchao.sso.job.client.handler.JobContext;
import com.laimengchao.sso.job.client.handler.JobHandler;
import com.laimengchao.sso.meeting.module.meeting.service.BookingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/** 由 polaris-job 调度中心派发：开始后未签到自动释放（宽限期来自埋点配置）；应用内不起 @Scheduled。 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MeetingNoShowReleaseJob implements JobHandler {

    private final BookingService bookingService;

    @Override
    public String name() {
        return "meetingNoShowRelease";
    }

    @Override
    public JobResult execute(JobContext ctx) {
        try {
            int released = bookingService.scanExpired();
            return JobResult.ok("超时释放 " + released + " 条");
        } catch (Exception e) {
            log.error("会议室未签到扫描失败", e);
            return JobResult.fail(e.getMessage());
        }
    }
}
