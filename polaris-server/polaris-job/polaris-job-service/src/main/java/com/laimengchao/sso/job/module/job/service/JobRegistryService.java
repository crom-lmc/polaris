package com.laimengchao.sso.job.module.job.service;

import com.laimengchao.sso.job.client.dto.JobDefinitionsRequest;
import com.laimengchao.sso.job.client.dto.JobHeartbeatRequest;
import com.laimengchao.sso.job.module.job.vo.ExecutorVO;
import java.util.List;
import java.util.Set;

public interface JobRegistryService {

    /** 启动上报：覆盖 Handler 清单并刷新心跳 */
    void reportDefinitions(JobDefinitionsRequest request);

    /** 心跳：只刷新时间；行不存在时补一条空清单，避免早期心跳丢失 */
    void ping(JobHeartbeatRequest request);

    /** 优雅下线：保留清单，仅标记离线 */
    void offline(JobHeartbeatRequest request);

    /** 管理端列表：online 按心跳实时计算 */
    List<ExecutorVO> list(String appCode, Boolean online);

    /** 某应用全部实例上报的 Handler 并集 */
    Set<String> knownHandlers(String appCode);
}
