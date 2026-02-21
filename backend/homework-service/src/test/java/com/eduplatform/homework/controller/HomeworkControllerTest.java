package com.eduplatform.homework.controller;

import com.eduplatform.common.result.Result;
import com.eduplatform.homework.dto.DuplicateHomeworkRequest;
import com.eduplatform.homework.dto.StudentHomeworkDTO;
import com.eduplatform.homework.service.HomeworkCascadeDeleteService;
import com.eduplatform.homework.service.HomeworkService;
import com.eduplatform.homework.vo.HomeworkVO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * HomeworkController 权限与错误收敛测试。
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
    @DisplayName("复制作业-学生角色应被拒绝")
    void duplicateHomeworkShouldDenyStudentRole() {
        Result<HomeworkVO> result = homeworkController.duplicateHomework(1L, null, "student");

        assertNotNull(result);
        assertEquals(403, result.getCode());
        assertEquals("权限不足，仅教师或管理员可复制作业", result.getMessage());
        verifyNoInteractions(homeworkService);
    }

    @Test
    @DisplayName("复制作业-异常信息不应外泄")
    void duplicateHomeworkShouldHideInternalExceptionMessage() {
        when(homeworkService.duplicateHomework(1L, null, "复制标题"))
                .thenThrow(new RuntimeException("duplicate key value violates unique constraint"));

        DuplicateHomeworkRequest request = new DuplicateHomeworkRequest();
        request.setTitle("复制标题");

        Result<HomeworkVO> result = homeworkController.duplicateHomework(
                1L,
                request,
                "teacher");

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

        homeworkController.duplicateHomework(1L, request, "teacher");

        verify(homeworkService).duplicateHomework(1L, 200L, "新标题");
    }

    @Test
    @DisplayName("学生作业列表-学生角色只能访问本人数据")
    void getStudentHomeworksShouldDenyOtherStudent() {
        Result<List<StudentHomeworkDTO>> result = homeworkController.getStudentHomeworks(
                2L,
                100L,
                "1",
                "student");

        assertNotNull(result);
        assertEquals(403, result.getCode());
        assertEquals("权限不足，仅本人、教师或管理员可查看学生作业", result.getMessage());
        verifyNoInteractions(homeworkService);
    }
}
