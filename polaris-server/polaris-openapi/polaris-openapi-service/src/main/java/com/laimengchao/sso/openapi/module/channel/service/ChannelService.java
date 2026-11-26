package com.laimengchao.sso.openapi.module.channel.service;

import com.laimengchao.sso.openapi.module.channel.entity.ApiOutboundChannel;
import com.laimengchao.sso.openapi.module.channel.vo.ChannelSaveRequest;
import com.laimengchao.sso.openapi.module.channel.vo.ChannelVO;
import java.util.List;
import java.util.Map;

public interface ChannelService {

    /** 渠道列表（凭证只回显是否已配置）。 */
    List<ChannelVO> list();

    /** 渠道详情（凭证只回显是否已配置）。 */
    ChannelVO get(String channelCode);

    /** 新建或按 channel_code 更新渠道基础信息（不动凭证）。 */
    void save(ChannelSaveRequest req);

    /** 启停渠道。 */
    void toggle(String channelCode, int status);

    /** 维护凭证：明文 JSON → 加密 → 只存密文。 */
    void saveCredential(String channelCode, Map<String, Object> credential);

    /** 取解密后的凭证（仅出站链路内部使用，绝不经 controller 返回）。 */
    Map<String, Object> resolveCredential(String channelCode);

    /** 删除渠道（逻辑删）。 */
    void delete(String channelCode);

    /** 内部：按编码取启用渠道，未找到/停用抛业务异常。 */
    ApiOutboundChannel requireEnabled(String channelCode);
}
