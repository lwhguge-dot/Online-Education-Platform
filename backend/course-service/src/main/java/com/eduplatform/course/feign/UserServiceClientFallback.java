package com.eduplatform.course.feign;

import com.eduplatform.common.result.Result;
import com.eduplatform.course.dto.UserBriefDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;

/**
 * 用户服务Feign客户端降级处理
 * 当 user-service 不可用时，返回安全的默认值以保证 course-service 核心功能正常运行。
 *
 * @author Antigravity
 */
@Slf4j
@Component
public class UserServiceClientFallback implements UserServiceClient {

    /**
     * 单用户查询降级处理。
     * 降级策略：返回占位用户信息（用户名为"用户"+ID），前端可据此显示提示。
     */
    @Override
    public Result<UserBriefDTO> getUserById(Long id) {
        log.warn("用户服务不可用，无法获取用户信息: userId={}", id);
        UserBriefDTO data = new UserBriefDTO();
        data.setId(id);
        data.setUsername("用户" + id);
        data.setName("未知用户");
        data.setEmail("unknown@example.com");
        return Result.failure(503, "用户服务不可用", data);
    }

    /**
     * 批量用户查询降级处理。
     * 降级策略：返回空列表，调用方需自行处理空结果（如显示占位信息）。
     */
    @Override
    public Result<List<UserBriefDTO>> getUsersByIds(List<Long> ids) {
        log.warn("用户服务不可用，无法批量获取用户信息: ids={}", ids);
        return Result.success(Collections.emptyList());
    }
}
