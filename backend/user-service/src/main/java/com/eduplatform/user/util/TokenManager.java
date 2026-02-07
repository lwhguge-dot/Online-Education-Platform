package com.eduplatform.user.util;

import org.springframework.stereotype.Component;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class TokenManager {
    
    // 存储每个用户的最新token: userId -> token
    private final ConcurrentHashMap<Long, String> userTokens = new ConcurrentHashMap<>();
    
    // 登录时设置用户的最新token
    public void setUserToken(Long userId, String token) {
        userTokens.put(userId, token);
    }
    
    // 验证token是否是用户的最新token
    public boolean isValidToken(Long userId, String token) {
        String currentToken = userTokens.get(userId);
        return currentToken != null && currentToken.equals(token);
    }
    
    // 用户登出时移除token
    public void removeUserToken(Long userId) {
        userTokens.remove(userId);
    }
    
    // 获取用户当前token
    public String getUserToken(Long userId) {
        return userTokens.get(userId);
    }
}
