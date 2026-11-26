package com.laimengchao.sso.settings.module.coderule.service;

import com.laimengchao.sso.settings.module.coderule.entity.CodeRule;
import java.util.List;

public interface CodeRuleService {

    List<CodeRule> list(String keyword);

    CodeRule create(CodeRule req);

    /**
     * 编辑规则：应用与数据库表名是取号的业务标识，创建后不允许修改，这里也不接受客户端传值。
     */
    CodeRule update(Long id, CodeRule req);

    /**
     * 调整「下一个」流水号，只能往后调整，避免调到已用过的号导致重复。
     *
     * @param nextNo 下一次取号要返回的流水号
     */
    CodeRule adjustNextNo(Long id, Integer nextNo);

    /**
     * 按业务表名生成下一个单号；未配置规则时返回空串，调用方自行决定是否继续。
     * 业务方在生成数据前调用，把返回值写进自己的单据字段即可。
     * <p>
     * 用 {@code SELECT ... FOR UPDATE} 锁住规则行，并发取号串行执行，保证不重号；
     * 放在事务里，锁持有到调用方事务结束（未在事务中调用时由本方法自己开事务）。
     */
    String nextCode(String tableName);
}
