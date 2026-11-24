package com.laimengchao.sso.file.module.file.vo;

import lombok.Data;

/** 分片上传初始化请求：声明目标文件的业务元数据与总大小。 */
@Data
public class MultipartInitRequest {

    private String bizType;
    private String visibility;
    private String originalName;
    private String contentType;
    private Long totalSize;
    private String ownerType;
    private String ownerId;
}
