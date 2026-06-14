package com.eduplatform.course.controller;

import com.eduplatform.common.result.Result;
import com.eduplatform.common.security.RequestContext;
import com.eduplatform.course.dto.ChapterDTO;
import com.eduplatform.course.entity.Course;
import com.eduplatform.course.mapper.CourseMapper;
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
import static org.mockito.Mockito.lenient;
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

    @Mock
    private CourseMapper courseMapper;

    @Mock
    private RequestContext requestContext;

    @Test
    @DisplayName("创建章节-学生角色被拒绝")
    void createChapterShouldDenyStudentRole() {
        // 默认 isTeacherOrAdmin() 返回 false（boolean 默认），等价于学生角色
        Result<ChapterVO> result = chapterController.createChapter(new ChapterDTO(), null, "student");

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

        // 教师角色通过权限校验，并解析身份为 1L（与课程归属教师一致）
        lenient().when(requestContext.isTeacherOrAdmin()).thenReturn(true);
        lenient().when(requestContext.parseUserId("1")).thenReturn(1L);

        // Mock课程归属验证
        Course course = new Course();
        course.setId(100L);
        course.setTeacherId(1L);
        when(courseMapper.selectById(100L)).thenReturn(course);

        when(chapterService.createChapter(any())).thenThrow(new RuntimeException("DB connection refused"));

        Result<ChapterVO> result = chapterController.createChapter(request, "1", "teacher");

        assertNotNull(result);
        assertEquals(500, result.getCode());
        assertEquals("创建失败，请稍后重试", result.getMessage());
        // 断言返回信息不包含内部异常文本，防止信息泄露
        assertFalse(result.getMessage().contains("DB connection refused"));
    }
}
