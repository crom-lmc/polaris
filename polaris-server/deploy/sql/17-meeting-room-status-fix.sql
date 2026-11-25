-- 修复会议室状态被前端按钮误写为英文编码的问题
-- 字典 meeting.room_status 的 value 与后端 RoomService 常量均为中文：可用/维护/停用
-- 幂等，可重复执行；生产 sql.init.mode=never，需手工执行本脚本，无需重启服务

UPDATE `meeting_room` SET `status` = '可用' WHERE `status` = 'AVAILABLE';
UPDATE `meeting_room` SET `status` = '维护' WHERE `status` = 'MAINTAIN';
UPDATE `meeting_room` SET `status` = '停用' WHERE `status` = 'DISABLED';
