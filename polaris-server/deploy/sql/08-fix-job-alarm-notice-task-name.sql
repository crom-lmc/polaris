-- ============================================================================
-- 修复调度报警站内消息「任务名为空」（幂等，可重复执行）
-- ============================================================================
-- 背景：cfg_notice_rule 中 job.alarm 规则模板为
--         title_tpl   = '调度任务报警：{bizNo}'
--         content_tpl = '任务「{bizNo}」触发报警。请及时排查调度中心。'
--       JobAlarmService.notify() 之前只调用 dto.setBizNo(job.getName())，
--       没有把变量写入 NoticeSendDTO.vars；NoticeService.render() 只从 vars
--       替换占位符，未提供的 {bizNo} 被正则抹成空串，于是消息显示成
--       「调度任务报警：」/「任务「」触发报警…」。
--       任务名其实已落在 notice_message.biz_no 字段（如「定时发布扫描」），
--       本脚本按 biz_no 回填历史消息。后端代码已同步修复（仅对新消息生效）。
--
-- 幂等：WHERE 精确匹配旧空文案，重复执行第二次影响 0 行。
-- ============================================================================

UPDATE settings.notice_message
SET title   = CONCAT('调度任务报警：', biz_no),
    content = CONCAT('任务「', biz_no, '」触发报警。请及时排查调度中心。')
WHERE rule_code = 'job.alarm'
  AND biz_no IS NOT NULL
  AND biz_no <> ''
  AND title = '调度任务报警：'
  AND content = '任务「」触发报警。请及时排查调度中心。';
