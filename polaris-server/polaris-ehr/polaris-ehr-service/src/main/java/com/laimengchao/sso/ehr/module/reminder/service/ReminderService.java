package com.laimengchao.sso.ehr.module.reminder.service;

import com.laimengchao.sso.common.R;
import com.laimengchao.sso.ehr.module.reminder.entity.Reminder;

public interface ReminderService {

    /** 每日生成提醒；返回本次新增条数（0 表示没抢到锁或没有命中） */
    int generateDaily();

    /**
     * 每日生成合同到期提醒：扫描「生效中 + 结束日期 = 今天 + N 天」的合同，
     * 给员工本人、直属主管与 HR 各写一条。已有续签/终止在途审批的跳过，
     * 避免同一合同重复打扰。
     */
    int generateContractDaily();

    R.Page<Reminder> my(boolean unreadOnly, String type, int page, int size);

    int unreadCount(String type);

    void markRead(Long id);

    void markAllRead();
}
