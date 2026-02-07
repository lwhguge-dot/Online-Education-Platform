package com.eduplatform.course.feign;

import com.eduplatform.common.result.Result;
import com.eduplatform.course.dto.UserBriefDTO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.List;

/**
 * 用户服务Feign客户端
 * 用于 course-service 跨服务调用 user-service 获取用户信息。
 *
 * @author Antigravity
 */
@FeignClient(name = "user-service", contextId = "userServiceClient", fallback = UserServiceClientFallback.class)
public interface UserServiceClient {

    /**
     * 根据用户 ID 获取单个用户信息。
     *
     * @param id 用户ID
     * @return 用户简要信息
     */
    @GetMapping("/api/users/{id}")
    Result<UserBriefDTO> getUserById(@PathVariable("id") Long id);

    /**
     * 批量获取用户简要信息。
     * 业务原因：导出学生列表时一次性获取所有学生的真实姓名和邮箱，避免 N+1 问题。
     *
     * @param ids 用户 ID 列表
     * @return 用户简要信息列表
     */
    @PostMapping("/api/users/batch")
    Result<List<UserBriefDTO>> getUsersByIds(@RequestBody List<Long> ids);
}
