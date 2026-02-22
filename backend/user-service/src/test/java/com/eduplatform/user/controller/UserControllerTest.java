package com.eduplatform.user.controller;

import com.eduplatform.common.result.Result;
import com.eduplatform.user.service.StudentProfileService;
import com.eduplatform.user.service.TeacherProfileService;
import com.eduplatform.user.service.UserCascadeDeleteService;
import com.eduplatform.user.service.UserService;
import com.eduplatform.user.service.UserSessionService;
import com.eduplatform.user.vo.UserBriefVO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * UserController 输入校验测试。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("UserController 单元测试")
class UserControllerTest {

    @InjectMocks
    private UserController userController;

    @Mock
    private UserService userService;

    @Mock
    private StudentProfileService studentProfileService;

    @Mock
    private TeacherProfileService teacherProfileService;

    @Mock
    private UserSessionService sessionService;

    @Mock
    private UserCascadeDeleteService userCascadeDeleteService;

    @Test
    @DisplayName("批量查询用户应拒绝非法ID")
    void getUsersByIdsShouldRejectInvalidIds() {
        Result<List<UserBriefVO>> result = userController.getUsersByIds(List.of(1L, 0L));

        assertNotNull(result);
        assertEquals(400, result.getCode());
        assertEquals("ids中存在非法用户ID", result.getMessage());
        verify(userService, never()).getByIds(org.mockito.ArgumentMatchers.anyList());
    }

    @Test
    @DisplayName("批量查询用户空列表应返回空结果")
    void getUsersByIdsShouldReturnEmptyListWhenInputEmpty() {
        Result<List<UserBriefVO>> result = userController.getUsersByIds(List.of());

        assertNotNull(result);
        assertEquals(200, result.getCode());
        assertTrue(result.getData().isEmpty());
        verify(userService, never()).getByIds(org.mockito.ArgumentMatchers.anyList());
    }
}
