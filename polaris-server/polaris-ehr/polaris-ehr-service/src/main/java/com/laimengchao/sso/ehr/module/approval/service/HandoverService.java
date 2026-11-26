package com.laimengchao.sso.ehr.module.approval.service;

import com.laimengchao.sso.ehr.module.approval.service.impl.HandoverServiceImpl;
import com.laimengchao.sso.ehr.module.approval.entity.HandoverItem;
import com.laimengchao.sso.ehr.module.approval.vo.HandoverItemVO;
import java.util.Collection;
import java.util.List;

public interface HandoverService {

    /** 确认结果：已确认数 / 总数 / 是否已全部确认 */
    public record Progress(int done, int total, boolean allDone) {
        public String text() {
            return total == 0 ? "无需交接" : done + "/" + total + " 已确认";
        }
    }

    /**
     * 清洗并校验交接内容：去空白、去空行、去重，最多 {@link #MAX_ITEMS} 条。
     * 校验放在写库之前，免得半截数据落库。
     */
    List<String> normalize(Collection<String> raw);

    /** 落清单：先清空该单旧项，再按顺序插入（重提时不会有残留） */
    void replaceItems(Long applyId, List<String> contents);

    /**
     * 落清单（带所属员工）：{@code employeeId} 是被交接的员工，冗余写到每一行，
     * 这样不 join 申请单也能按人查出全部交接项。
     */
    void replaceItems(Long applyId, Long employeeId, List<String> contents);

    /**
     * 交接人确认：itemIds 为空表示全部确认，否则只确认指定项（已确认的跳过，幂等）。
     */
    Progress confirm(Long applyId, List<Long> itemIds, String note, Long accountId);

    List<HandoverItem> listByApply(Long applyId);




    static Progress progressOf(List<HandoverItem> items) {
        return HandoverServiceImpl.progressOf(items);
    }


    static List<HandoverItemVO> toVOList(List<HandoverItem> items) {
        return HandoverServiceImpl.toVOList(items);
    }
}
