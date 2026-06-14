package com.eduplatform.user.controller;

import com.eduplatform.common.result.Result;
import com.eduplatform.common.security.InternalTokenVerifier;
import com.eduplatform.common.security.RequestContext;
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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
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

    @Mock
    private InternalTokenVerifier internalTokenVerifier;

    @Mock
    private RequestContext requestContext;

    @org.junit.jupiter.api.BeforeEach
    void setUp() {
        // 模拟内部调用通过令牌校验，避免在数据校验测试中被前置拦截
        lenient().when(internalTokenVerifier.isValid(anyString())).thenReturn(true);
    }

    @Test
    @DisplayName("批量查询用户应拒绝非法ID")
    void getUsersByIdsShouldRejectInvalidIds() {
        Result<List<UserBriefVO>> result = userController.getUsersByIds(List.of(1L, 0L), "valid-token");

        assertNotNull(result);
        assertEquals(400, result.getCode());
        assertEquals("ids中存在非法用户ID", result.getMessage());
        verify(userService, never()).getByIds(org.mockito.ArgumentMatchers.anyList());
    }

    @Test
    @DisplayName("批量查询用户空列表应返回空结果")
    void getUsersByIdsShouldReturnEmptyListWhenInputEmpty() {
        Result<List<UserBriefVO>> result = userController.getUsersByIds(List.of(), "valid-token");

        assertNotNull(result);
        assertEquals(200, result.getCode());
        assertTrue(result.getData().isEmpty());
        verify(userService, never()).getByIds(org.mockito.ArgumentMatchers.anyList());
    }
}
