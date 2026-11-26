package com.laimengchao.sso.account.service;

import com.laimengchao.sso.account.dto.request.CreatePermissionRequest;
import com.laimengchao.sso.account.dto.request.UpdatePermissionRequest;
import com.laimengchao.sso.account.dto.response.PermissionVO;

import java.util.List;

public interface PermissionService {

    /**
     * 权限树（菜单 -> 按钮），用于前端动态菜单 + 按钮渲染。
     *
     * @param appCode 应用编码；为空表示全部应用（管理台「全部」视图），各应用自成一棵树
     */
    List<PermissionVO> tree(String appCode);

    /** 全部权限（扁平）；appCode 为空表示全部应用 */
    List<PermissionVO> listAll(String appCode);

    /** 新建权限（appCode 缺省为 sso） */
    PermissionVO create(CreatePermissionRequest req);

    /** 编辑权限（不允许改所属应用） */
    PermissionVO update(Long id, UpdatePermissionRequest req);

    /** 删除权限（内置码/带子节点/被角色引用时拒绝） */
    void delete(Long id);

    /**
     * 批量导入某应用的权限点（幂等 upsert，供业务应用接入时一次性灌入按钮码）。
     *
     * @return 新建数量（已存在的只更新名称/排序，不计数）
     */
    int importForApp(String appCode, List<CreatePermissionRequest> items);

    /** 删除某应用的全部权限点（应用下线时级联清理，同时解除角色关联） */
    int deleteByApp(String appCode);

    /**
     * 授权接口权限码给第三方应用（开放平台内部调用）。仅接受 inbound:/outbound: 前缀，
     * 已存在的码跳过（幂等，不重复授权），返回本次新建数量。
     */
    int grantApiPermissions(String appCode, List<CreatePermissionRequest> items);

    /** 撤销第三方应用的某个接口权限码（仅 inbound:/outbound:），返回删除数量 */
    int revokeApiPermission(String appCode, String code);
}
