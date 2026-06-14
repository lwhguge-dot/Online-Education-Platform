package com.eduplatform.homework.controller;

import com.eduplatform.common.result.Result;
import com.eduplatform.homework.dto.DuplicateHomeworkRequest;
import com.eduplatform.homework.service.HomeworkCascadeDeleteService;
import com.eduplatform.homework.service.HomeworkService;
import com.eduplatform.homework.vo.HomeworkVO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * HomeworkController 权限与错误收敛测试。
 *
 * <p>注：原测试通过 UserHeaderAuthentication 设置 SecurityContext 来配合 @PreAuthorize，
 * 但项目未启用 @EnableMethodSecurity，@PreAuthorize 不会生效，权限校验由 Controller
 * 内部的 RequestContext 完成。因此这些测试移除 SecurityContext 设置，
 * 聚焦于异常收敛与字段透传的纯逻辑校验。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("HomeworkController 单元测试")
class HomeworkControllerTest {

    @InjectMocks
    private HomeworkController homeworkController;

    @Mock
    private HomeworkService homeworkService;

    @Mock
    private HomeworkCascadeDeleteService homeworkCascadeDeleteService;

    @Test
    @DisplayName("复制作业-异常信息不应外泄")
    void duplicateHomeworkShouldHideInternalExceptionMessage() {
        when(homeworkService.duplicateHomework(1L, null, "复制标题"))
                .thenThrow(new RuntimeException("duplicate key value violates unique constraint"));

        DuplicateHomeworkRequest request = new DuplicateHomeworkRequest();
        request.setTitle("复制标题");

        Result<HomeworkVO> result = homeworkController.duplicateHomework(1L, request);

        assertNotNull(result);
        assertEquals(500, result.getCode());
        assertEquals("复制失败，请稍后重试", result.getMessage());
        assertFalse(result.getMessage().contains("duplicate key value"));
    }

    @Test
    @DisplayName("复制作业-应透传强类型字段到服务层")
    void duplicateHomeworkShouldPassTypedFieldsToService() {
        DuplicateHomeworkRequest request = new DuplicateHomeworkRequest();
        request.setChapterId(200L);
        request.setTitle("新标题");

        homeworkController.duplicateHomework(1L, request);

        verify(homeworkService).duplicateHomework(1L, 200L, "新标题");
    }
}
