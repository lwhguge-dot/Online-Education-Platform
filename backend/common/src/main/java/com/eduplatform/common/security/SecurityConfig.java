package com.eduplatform.common.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

/**
 * 公共 Spring Security 最小配置（业务服务共享）。
 *
 * <p>项目采用「网关统一鉴权 + 业务服务从 X-User-Id / X-User-Role 请求头解析身份」的架构，
 * 业务服务无需 Spring Security 的认证拦截，权限校验由 Controller 内部的 RequestContext
 * 与 @RequestHeader 完成。
 *
 * <p>注意：本配置<b>不</b>启用 {@code @EnableMethodSecurity}。
 * 原因：项目启用虚拟线程（{@code spring.threads.virtual.enabled=true}）后，
 * Controller 方法在虚拟线程上执行，而 Spring Security 过滤器在 platform thread 设置的
 * SecurityContext 跨线程不可见，导致 {@code @PreAuthorize} 评估时 authentication 为 null，
 * SpEL 表达式 {@code authentication.principal.toString()} 抛 NPE。所有业务 Controller 已通过
 * {@code RequestContext} 自行完成权限校验，无需依赖 AOP 注解。
 *
 * <p><b>CSRF 禁用说明（lgtm [java/spring-disabled-csrf-protection]）</b>：
 * 本项目为纯无状态（{@link SessionCreationPolicy#STATELESS}）的 REST API，前端通过
 * {@code Authorization: Bearer <jwt>} 头部携带凭据，不使用 Cookie 会话。CSRF 攻击的载体
 * 是基于 Cookie 的自动凭据携带，无 Cookie 即无 CSRF 攻击面；网关侧另有限流与签名校验。
 * 故禁用 CSRF 是与威胁模型相符的设计决策。
 *
 * <p>Bean 名显式指定为 {@code commonSecurityConfig}，避免与各业务模块
 * （如 {@code com.eduplatform.user.config.SecurityConfig}）的同名类冲突。
 */
@Configuration("commonSecurityConfig")
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth.anyRequest().permitAll());
        return http.build();
    }
}
