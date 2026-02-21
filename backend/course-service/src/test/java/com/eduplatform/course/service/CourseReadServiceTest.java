package com.eduplatform.course.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.eduplatform.course.entity.Course;
import com.eduplatform.course.feign.UserServiceClient;
import com.eduplatform.course.mapper.ChapterMapper;
import com.eduplatform.course.mapper.CourseMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * CourseReadService 单元测试。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("CourseReadService 单元测试")
class CourseReadServiceTest {

    @InjectMocks
    private CourseReadService courseReadService;

    @Mock
    private CourseMapper courseMapper;

    @Mock
    private ChapterMapper chapterMapper;

    @Mock
    private UserServiceClient userServiceClient;

    @Nested
    @DisplayName("管理员可见性规则")
    class AdminVisibilityTests {

        @Test
        @DisplayName("管理员请求草稿状态时返回空列表")
        void adminDraftQueryShouldReturnEmpty() {
            assertEquals(0, courseReadService.getAdminVisibleCourses(null, Course.STATUS_DRAFT).size());
        }

        @Test
        @DisplayName("管理员默认查询会执行排除草稿条件")
        @SuppressWarnings("unchecked")
        void adminDefaultQueryShouldExcludeDraft() {
            when(courseMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(Collections.emptyList());

            courseReadService.getAdminVisibleCourses(null, null);

            verify(courseMapper).selectList(any(LambdaQueryWrapper.class));
        }
    }
}
