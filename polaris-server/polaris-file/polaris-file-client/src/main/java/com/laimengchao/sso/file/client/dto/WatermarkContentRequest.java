package com.laimengchao.sso.file.client.dto;

import lombok.Data;

/**
 * 下载水印烧录请求：业务服务下载附件时调用，传入当前登录人身份，
 * 由 file 服务按 bizType 查水印配置并对字节流烧录后返回（base64）。
 */
@Data
public class WatermarkContentRequest {

    /** 文件 ID */
    private String fileId;

    /** 生效环节：VIEW/SHARE/DOWNLOAD */
    private String scene;

    /** 当前下载人用户名（用于 {username} 变量） */
    private String username;

    /** 当前下载人账号 ID（用于 {userId} 变量） */
    private String userId;

    /** 客户端 IP（用于 {ip} 变量） */
    private String ip;
}
