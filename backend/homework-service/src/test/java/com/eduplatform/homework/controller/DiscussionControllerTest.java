package com.eduplatform.homework.controller;

import com.eduplatform.common.result.Result;
import com.eduplatform.homework.dto.DiscussionReplyRequest;
import com.eduplatform.homework.service.DiscussionService;
import com.eduplatform.homework.vo.SubjectiveCommentVO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * DiscussionController 权限与身份校验测试。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("DiscussionController 单元测试")
class DiscussionControllerTest {

    @InjectMocks
    private DiscussionController discussionController;

    @Mock
    private DiscussionService discussionService;

    @Test
    @DisplayName("回复讨论-学生角色被拒绝")
    void replyShouldDenyStudentRole() {
        DiscussionReplyRequest request = new DiscussionReplyRequest();
        request.setContent("测试回复");

        Result<SubjectiveCommentVO> result = discussionController.reply(1L, request, "10", "student");

        assertNotNull(result);
        assertEquals(403, result.getCode());
        assertEquals("权限不足，仅教师或管理员可回复讨论", result.getMessage());
        verifyNoInteractions(discussionService);
    }

    @Test
    @DisplayName("回复讨论-缺少身份头被拒绝")
    void replyShouldRequireUserIdentity() {
        DiscussionReplyRequest request = new DiscussionReplyRequest();
        request.setContent("测试回复");

        Result<SubjectiveCommentVO> result = discussionController.reply(1L, request, null, "teacher");

        assertNotNull(result);
        assertEquals(401, result.getCode());
        assertEquals("身份信息缺失，请重新登录", result.getMessage());
        verifyNoInteractions(discussionService);
    }

    @Test
    @DisplayName("回复讨论-教师角色可成功调用服务")
    void replyShouldAllowTeacherRole() {
        DiscussionReplyRequest request = new DiscussionReplyRequest();
        request.setContent("测试回复");
        request.setCourseId(100L);
        request.setChapterId(200L);

        SubjectiveCommentVO commentVO = new SubjectiveCommentVO();
        when(discussionService.reply(1L, 10L, "测试回复", 100L, 200L)).thenReturn(commentVO);

        Result<SubjectiveCommentVO> result = discussionController.reply(1L, request, "10", "teacher");

        assertNotNull(result);
        assertEquals(200, result.getCode());
        assertEquals("回复成功", result.getMessage());
        verify(discussionService).reply(1L, 10L, "测试回复", 100L, 200L);
    }
}
