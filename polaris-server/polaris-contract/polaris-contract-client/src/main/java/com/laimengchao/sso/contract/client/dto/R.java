package com.laimengchao.sso.contract.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

/**
 * 合同服务统一响应壳的客户端侧映射：{@code {code, message, data}}。
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class R<T> {

    /** 0 = 成功 */
    private Integer code;

    private String message;

    private T data;

    public boolean ok() {
        return code != null && code == 0;
    }
}
