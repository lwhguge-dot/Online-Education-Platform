package com.eduplatform.homework.feign;

import com.eduplatform.common.result.Result;
import com.eduplatform.homework.dto.NotificationRequest;
import com.eduplatform.homework.dto.UserBriefDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import java.util.Collections;
import java.util.List;

/**
 * 用户服务Feign客户端降级处理
 */
@Slf4j
@Component
public class UserServiceClientFallback implements UserServiceClient {

    @Override
    public Result<UserBriefDTO> getUserById(Long id) {
        UserBriefDTO data = new UserBriefDTO();
        data.setId(id);
        data.setUsername("学生" + id);
        return Result.failure(503, "用户服务暂时不可用", data);
    }

    @Override
    public Result<List<UserBriefDTO>> getUsersByIds(List<Long> ids) {
        log.warn("用户服务不可用，批量查询用户信息失败: {}", ids);
        return Result.failure(503, "用户服务暂时不可用", Collections.emptyList());
    }

    @Override
    public Result<Void> sendNotification(NotificationRequest request) {
        log.warn("用户服务不可用，通知发送失败: {}", request);
        return Result.failure(503, "用户服务暂时不可用，通知发送失败");
    }
}
