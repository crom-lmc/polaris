package com.laimengchao.sso.openapi.module.app.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.laimengchao.sso.openapi.module.app.entity.ApiClientApp;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/** 第三方对接应用 mapper。 */
public interface ApiClientAppMapper extends BaseMapper<ApiClientApp> {

    @Select("SELECT * FROM api_client_app WHERE app_code = #{appCode} AND deleted = 0")
    ApiClientApp selectByAppCode(@Param("appCode") String appCode);
}
