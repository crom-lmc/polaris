package com.laimengchao.sso.meeting.module.meeting.service;

import com.laimengchao.sso.meeting.module.meeting.dto.MeetingVOs;
import java.util.Collection;
import java.util.List;
import java.util.Map;

public interface MeetingSupportService {

    /** 爽约规则：windowDays 内达到 threshold 次未签到释放，禁约 banDays 天；次数字段为 0 表示不启用 */
    public record NoShowRule(int windowDays, int threshold, int banDays) {
        public boolean enabled() {
            return windowDays > 0 && threshold > 0 && banDays > 0;
        }
    }

    /** 当前账号；未登录直接拒绝（fail-closed，绝不返回全量） */
    Long requireAccountId();

    /** 当前人城市：EHR 组织节点的二级单位 city；取不到返回空串（调用方按 fail-closed 处理） */
    String currentCity();

    /** 可提前预约天数：埋点缺失/非法兜底 7；0 表示只能约当天 */
    int maxAdvanceDays();

    /** 未签到自动释放宽限期：埋点缺失/非法兜底 15 分钟，0 表示会议开始即释放 */
    int noShowGraceMinutes();

    /** 单次预约最长时长（分钟）：0 表示不限制；缺失/非法按 0 兜底，保持原有可用范围 */
    int maxBookingMinutes();

    /** 爽约限制规则：统计天数 / 触发次数 / 禁约天数，任一字段非法时仅该字段回退默认值 */
    NoShowRule noShowRule();

    /** 一次性返回本应用全部字典（前端统一从会议服务读取，不直连 settings internal API） */
    Map<String, List<MeetingVOs.DictItemVO>> dicts();

    /** 员工搜索：复用 EHR 在职员工主数据，按姓名/工号过滤，不新建人员表 */
    List<MeetingVOs.EmployeeVO> searchEmployees(String keyword, int limit);

    /** 按账号 ID 批量取员工姓名（详情/修改回显用）；查不到时回退为 ID */
    Map<String, String> employeeNames(Collection<Long> ids);

    /** 当前组织编码：仅写入预留字段，本期查询不使用 */
    String orgCode();

    Collection<Long> parseAccountIds(Collection<Long> ids);
}
