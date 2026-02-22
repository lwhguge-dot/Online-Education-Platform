package com.eduplatform.homework.feign;

import com.eduplatform.common.result.Result;
import com.eduplatform.homework.dto.UserBriefDTO;
import com.eduplatform.homework.dto.NotificationRequest;
import com.eduplatform.homework.config.InternalApiFeignConfig;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.List;

/**
 * 用户服务Feign客户端
 */
@FeignClient(name = "user-service", configuration = InternalApiFeignConfig.class, fallback = UserServiceClientFallback.class)
public interface UserServiceClient {

    @GetMapping("/api/users/{id}")
    Result<UserBriefDTO> getUserById(@PathVariable("id") Long id);

    /**
     * 批量获取用户简要信息
     */
    @PostMapping("/api/users/batch")
    Result<List<UserBriefDTO>> getUsersByIds(@RequestBody List<Long> ids);

    /**
     * 发送通知给指定用户
     */
    @PostMapping("/api/notifications/send")
    Result<Void> sendNotification(@RequestBody NotificationRequest request);
}
