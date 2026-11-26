package com.laimengchao.sso.finance.module.finance.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.laimengchao.sso.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 会计科目主数据：code / name / level / balance_direction / category / status。 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("fin_subject")
public class FinSubject extends BaseEntity {

    private String code;
    private String name;
    /** 科目级次，1 级为顶层 */
    private Integer level;
    /** DEBIT 借 / CREDIT 贷 */
    private String balanceDirection;
    /** ASSET/LIABILITY/EQUITY/INCOME/EXPENSE 等科目类别 */
    private String category;
    /** ENABLED / DISABLED */
    private String status;
    /** 归属组织编码（数据权限） */
    private String orgCode;
}
