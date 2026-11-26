package com.laimengchao.sso.meeting.module.meeting.service;

import com.laimengchao.sso.common.R;
import com.laimengchao.sso.meeting.module.meeting.dto.MeetingRequests;
import com.laimengchao.sso.meeting.module.meeting.dto.MeetingVOs;
import com.laimengchao.sso.meeting.module.meeting.entity.MeetingBooking;

import java.time.LocalDate;
import java.util.Map;

public interface BookingService {

    /** 时间轴：按房间 × 30 分钟格返回状态，过去/占用/不可预约由服务端判定 */
    MeetingVOs.ScheduleVO schedule(LocalDate date, String city, Long roomId);

    /** 新建预定：所有校验在服务端重做一遍，前端着色只做体验 */
    MeetingBooking create(MeetingRequests.BookingSave req);

    /** 修改：仅本人且未开始；增删参会人时补发/关闭通知 */
    void update(Long id, MeetingRequests.BookingSave req);

    /** 签到：开始前 15 分钟至开始后宽限期内开放 */
    void checkin(Long id);

    /** 延时：签到后申请，重新跑冲突检测 */
    void extend(Long id, MeetingRequests.ActionRequest req);

    void cancel(Long id);

    /** 提前结束释放时段（与超时未签到释放区分：原因 MANUAL） */
    void release(Long id);

    /** 我的预定：服务端强制按 creator_id 过滤，前端不能伪造创建人 */
    R.Page<MeetingBooking> myBookings(String tab, String city, String status, String keyword, LocalDate from, LocalDate to, int page, int size);

    /** 页签角标：与列表同一套筛选（含日期范围），范围内无数据则各页签均为 0 */
    Map<String, Long> myBookingCounts(String city, String status, String keyword, LocalDate from, LocalDate to);

    /** 详情：创建人本人或被邀请人（需前端仍具备基础查看权限，由 @Permit 保证） */
    MeetingBooking detail(Long id);

    /** 概览：统计卡按所选日期+地区取全平台口径；月历与日程为本人创建或被邀请并按地区过滤 */
    MeetingVOs.OverviewVO overview(LocalDate date, String month, String city);

    /** 爽约状态：按会议开始时间统计窗口内的 NO_SHOW 释放，达到阈值后从最近一次爽约起禁约 banDays 天 */
    MeetingVOs.NoShowStatusVO noShowStatus(Long account);

    /** job 中心派发：超时未签到释放 + 已结束单据自动完成；宽限期来自埋点，主键游标分批 + 条件更新保证幂等 */
    int scanExpired();
}
