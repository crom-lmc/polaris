package com.laimengchao.sso.settings.module.approval.service;

import com.laimengchao.sso.settings.module.approval.dto.SceneTableBindingsDto;
import com.laimengchao.sso.settings.module.approval.dto.TemplateDetailVO;
import com.laimengchao.sso.settings.module.approval.dto.TemplateSaveRequest;
import com.laimengchao.sso.settings.module.approval.entity.ApprovalPage;
import com.laimengchao.sso.settings.module.approval.entity.BizScene;
import java.util.List;

public interface ApprovalConfigService {

    List<TemplateDetailVO> list();

    TemplateDetailVO detail(Long id);

    TemplateDetailVO save(TemplateSaveRequest req);

    void toggle(Long id, Integer enabled);

    void remove(Long id);

    ApprovalPage getPage(Long templateId, String view);

    /** 按业务场景取页面配置（业务侧渲染审批弹窗用） */
    ApprovalPage getPageByBizType(String bizType, String view);

    ApprovalPage savePage(Long templateId, TemplateSaveRequest.PageDto dto);

    List<BizScene> scenes();

    /**
     * 业务方启动时注册场景（幂等 upsert），字段字典是配置器的数据来源。
     * <p>
     * 业务点没有独立表，就藏在 formSchema 里，前端保存是<b>整列覆盖</b>。
     * 所以这里不只是「写字典」，还是删除业务点的唯一入口：必须拿新旧编码差集
     * 做一次引用反查，被引用的编码一律拒绝删除。前端把按钮置灰只是提示，
     * 真正兜底在这一层——绕过界面直接 POST /scenes/register 也删不掉。
     */
    BizScene registerScene(BizScene scene);

    /**
     * 设置场景的数据表绑定（审批页面「与表绑定 / 真绑定」）。仅由管理端调用——
     * 业务方 registerScene 走 null 不覆盖约定，不会碰这一列。
     * <p>
     * 校验：整个场景最多一个主体表（main=true）；子表必须指定 fk 外键列（如 employee_id）。
     * 存的是 {@link SceneTableBindingsDto} 序列化后的 JSON 字符串，渲染期由 ApprovalContextService 解析。
     */
    BizScene saveTableBindings(String bizType, SceneTableBindingsDto dto);
}
