package com.eduplatform.course.controller;

import com.eduplatform.common.result.Result;
import com.eduplatform.course.dto.ChapterDTO;
import com.eduplatform.course.service.ChapterService;
import com.eduplatform.course.vo.ChapterVO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * ChapterController 权限与异常返回测试。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ChapterController 单元测试")
class ChapterControllerTest {

    @InjectMocks
    private ChapterController chapterController;

    @Mock
    private ChapterService chapterService;

    @Test
    @DisplayName("创建章节-学生角色被拒绝")
    void createChapterShouldDenyStudentRole() {
        Result<ChapterVO> result = chapterController.createChapter(new ChapterDTO(), "student");

        assertNotNull(result);
        assertEquals(403, result.getCode());
        assertEquals("权限不足，仅教师或管理员可创建章节", result.getMessage());
        verifyNoInteractions(chapterService);
    }

    @Test
    @DisplayName("创建章节-异常信息不应外泄")
    void createChapterShouldHideInternalExceptionMessage() {
        ChapterDTO request = new ChapterDTO();
        request.setCourseId(100L);
        request.setTitle("测试章节");

        when(chapterService.createChapter(any())).thenThrow(new RuntimeException("DB connection refused"));

        Result<ChapterVO> result = chapterController.createChapter(request, "teacher");

        assertNotNull(result);
        assertEquals(500, result.getCode());
        assertEquals("创建失败，请稍后重试", result.getMessage());
        // 断言返回信息不包含内部异常文本，防止信息泄露
        assertFalse(result.getMessage().contains("DB connection refused"));
    }
}
