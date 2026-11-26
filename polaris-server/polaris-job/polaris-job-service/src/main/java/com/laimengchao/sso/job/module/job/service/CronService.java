package com.laimengchao.sso.job.module.job.service;

import java.time.LocalDateTime;
import java.util.List;

public interface CronService {

    /** 规范化：trim + 多空白折叠为单空格，避免同义不同串 */
    String normalize(String cron);

    /** 校验并返回未来 3 次执行时间 */
    List<LocalDateTime> preview(String cron);

    List<LocalDateTime> preview(String cron, LocalDateTime base);

    /** 保存时取第一次触发时间 */
    LocalDateTime nextFireTime(String cron, LocalDateTime base);
}
