package com.laimengchao.sso.file.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.laimengchao.sso.common.ErrorCode;
import com.laimengchao.sso.common.R;
import com.laimengchao.sso.file.security.SsoHeaderAuthFilter;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.context.SecurityContextHolderFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * 文件服务安全边界：
 * <ul>
 *   <li>管理端 /api/file/admin/** → SsoHeaderAuthFilter 读网关注入头建登录态，这里只拦未登录，细粒度交给 @Permit；</li>
 *   <li>公共文件能力 /api/file/my/** → 同上建登录态，只要求登录、不挂权限码（上传/下载/分享是所有登录用户的基础能力）；</li>
 *   <li>服务间 /internal/files/** → 由 common 的 InternalTokenInterceptor 校验（fail-closed）；</li>
 *   <li>公开读取 /api/file/public/** 与签名下载 /api/file/secure/** → 鉴权在控制器内按签名令牌/身份头完成，这里放行。</li>
 * </ul>
 */
@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final SsoHeaderAuthFilter ssoHeaderAuthFilter;
    private final ObjectMapper objectMapper;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(Customizer.withDefaults())
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint((request, response, e) ->
                                writeJson(response, HttpServletResponse.SC_UNAUTHORIZED, ErrorCode.UNAUTHORIZED))
                        .accessDeniedHandler((request, response, e) ->
                                writeJson(response, HttpServletResponse.SC_FORBIDDEN, ErrorCode.FORBIDDEN)))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/file/admin/**", "/api/file/my/**").authenticated()
                        .anyRequest().permitAll())
                .addFilterAfter(ssoHeaderAuthFilter, SecurityContextHolderFilter.class);
        return http.build();
    }

    private void writeJson(HttpServletResponse response, int status, ErrorCode code) throws IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write(objectMapper.writeValueAsString(R.fail(code)));
    }
}
