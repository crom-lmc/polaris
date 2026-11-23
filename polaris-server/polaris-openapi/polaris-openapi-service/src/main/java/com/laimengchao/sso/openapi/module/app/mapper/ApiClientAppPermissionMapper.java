package com.laimengchao.sso.openapi.module.app.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.laimengchao.sso.openapi.module.app.entity.ApiClientAppPermission;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/** 第三方应用接口授权 mapper。 */
public interface ApiClientAppPermissionMapper extends BaseMapper<ApiClientAppPermission> {

    @Select("SELECT * FROM api_client_app_permission WHERE app_code = #{appCode} ORDER BY id")
    List<ApiClientAppPermission> selectByAppCode(@Param("appCode") String appCode);

    /**
     * 令牌 perms 唯一来源：只取启用状态的授权接口编码，停用后下次换票即失效。
     * 授权粒度为「一行一个接口」，故直接发 api_code，避免同一权限码下停用单个接口不生效。
     */
    @Select("SELECT api_code FROM api_client_app_permission WHERE app_code = #{appCode} AND status = 1 AND api_code IS NOT NULL AND api_code <> ''")
    List<String> selectApiCodes(@Param("appCode") String appCode);
}
