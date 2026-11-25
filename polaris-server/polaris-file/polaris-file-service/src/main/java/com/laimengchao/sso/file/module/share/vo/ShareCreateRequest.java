package com.laimengchao.sso.file.module.share.vo;

import lombok.Data;

/** 创建分享请求。 */
@Data
public class ShareCreateRequest {

    private String fileId;

    /** 提取码（明文，留空表示无密码）；后端只存哈希 */
    private String password;

    /** 有效期小时数，留空表示长期有效；填写则必须 > 0 */
    private Long expiresHours;

    /** 最大查看次数，留空表示不限次数；填写则必须 > 0 */
    private Integer maxViews;
}
