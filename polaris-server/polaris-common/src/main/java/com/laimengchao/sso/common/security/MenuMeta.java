package com.laimengchao.sso.common.security;

import lombok.Data;

/**
 * 应用菜单元信息：名称与排序来自 SSO 权限树（sso_permission），随身份快照下发。
 *
 * <p>业务应用的前端侧栏只负责把 {@code code} 映射到路由与图标；
 * 显示名称、显示顺序、父子层级一律以此为准，禁止在前端另写一套名称/顺序。
 */
@Data
public class MenuMeta {

    /** 权限码，与 @Permit / meta.perm 同一字符串 */
    private String code;

    /** SSO 权限树里维护的菜单名称 */
    private String name;

    /** SSO 权限树里维护的排序值，小的在前 */
    private int sort;

    /** 父菜单权限码；一级菜单为 null */
    private String parentCode;
}
