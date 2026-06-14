package com.eduplatform.common.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;

import java.util.Collection;

/**
 * 基于网关注入请求头的认证对象。
 * 实现 Spring Security Authentication 接口，用于 @PreAuthorize 注解校验。
 */
public class UserHeaderAuthentication implements Authentication {

    private final String userId;
    private final String userName;
    private final String userRole;
    private final Collection<? extends GrantedAuthority> authorities;
    private boolean authenticated = true;

    public UserHeaderAuthentication(String userId, String userName, String userRole,
            Collection<? extends GrantedAuthority> authorities) {
        this.userId = userId;
        this.userName = userName;
        this.userRole = userRole;
        this.authorities = authorities;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return authorities;
    }

    @Override
    public Object getCredentials() {
        return null;
    }

    @Override
    public Object getDetails() {
        return null;
    }

    @Override
    public Object getPrincipal() {
        return userId;
    }

    @Override
    public boolean isAuthenticated() {
        return authenticated;
    }

    @Override
    public void setAuthenticated(boolean isAuthenticated) throws IllegalArgumentException {
        this.authenticated = isAuthenticated;
    }

    @Override
    public String getName() {
        return userName;
    }

    public String getUserId() {
        return userId;
    }

    public String getUserRole() {
        return userRole;
    }
}
