package com.eduplatform.course.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.eduplatform.course.entity.Course;
import com.eduplatform.course.entity.Enrollment;
import com.eduplatform.course.mapper.ChapterMapper;
import com.eduplatform.course.mapper.CourseMapper;
import com.eduplatform.course.mapper.EnrollmentMapper;
import com.eduplatform.course.feign.UserServiceClient;
import com.eduplatform.course.config.LearningStatusConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * EnrollmentService 单元测试
 *
 * 覆盖场景:
 * 1. 选课: 正常选课、重复选课、课程未发布、课程不存在
 * 2. 退课: 正常退课、未选课退课
 * 3. 数据一致性: 选课/退课时课程学生数同步更新
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("EnrollmentService 单元测试")
class EnrollmentServiceTest {

    @InjectMocks
    private EnrollmentService enrollmentService;

    @Mock
    private EnrollmentMapper enrollmentMapper;

    @Mock
    private CourseMapper courseMapper;

    @Mock
    private ChapterMapper chapterMapper;

    @Mock
    private UserServiceClient userServiceClient;

    @Mock
    private LearningStatusConfig learningStatusConfig;

    private Course publishedCourse;

    @BeforeEach
    void setUp() {
        publishedCourse = new Course();
        publishedCourse.setId(100L);
        publishedCourse.setTitle("Java 入门");
        publishedCourse.setStatus(Course.STATUS_PUBLISHED);
        publishedCourse.setStudentCount(10);
    }

    // =========================================================================
    // 选课测试
    // =========================================================================
    @Nested
    @DisplayName("选课测试")
    class EnrollTests {

        @Test
        @DisplayName("正常选课 - 创建记录并增加课程学生数")
        void enrollSuccess() {
            when(courseMapper.selectById(100L)).thenReturn(publishedCourse);
            when(enrollmentMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);

            Enrollment result = enrollmentService.enroll(1L, 100L);

            assertNotNull(result);
            assertEquals(1L, result.getStudentId());
            assertEquals(100L, result.getCourseId());
            assertEquals(0, result.getProgress());
            assertEquals(Enrollment.STATUS_ACTIVE, result.getStatus());

            // 验证插入了选课记录
            verify(enrollmentMapper).insert(any(Enrollment.class));
            // 验证课程学生数 +1
            verify(courseMapper).updateById(argThat(course ->
                    course.getStudentCount() == 11));
        }

        @Test
        @DisplayName("选课失败 - 课程不存在")
        void enrollFailCourseNotFound() {
            when(courseMapper.selectById(999L)).thenReturn(null);

            RuntimeException ex = assertThrows(RuntimeException.class,
                    () -> enrollmentService.enroll(1L, 999L));
            assertTrue(ex.getMessage().contains("课程不存在"));
        }

        @Test
        @DisplayName("选课失败 - 课程未发布")
        void enrollFailCourseNotPublished() {
            Course draft = new Course();
            draft.setId(200L);
            draft.setStatus(Course.STATUS_DRAFT);

            when(courseMapper.selectById(200L)).thenReturn(draft);

            RuntimeException ex = assertThrows(RuntimeException.class,
                    () -> enrollmentService.enroll(1L, 200L));
            assertTrue(ex.getMessage().contains("未处于发布状态"));
        }

        @Test
        @DisplayName("选课失败 - 重复选课")
        void enrollFailAlreadyEnrolled() {
            when(courseMapper.selectById(100L)).thenReturn(publishedCourse);
            // 已有选课记录
            when(enrollmentMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(1L);

            RuntimeException ex = assertThrows(RuntimeException.class,
                    () -> enrollmentService.enroll(1L, 100L));
            assertTrue(ex.getMessage().contains("已参与"));
        }
    }

    // =========================================================================
    // 退课测试
    // =========================================================================
    @Nested
    @DisplayName("退课测试")
    class DropTests {

        @Test
        @DisplayName("正常退课 - 标记为 DROPPED 并扣减学生数")
        void dropSuccess() {
            Enrollment enrollment = new Enrollment();
            enrollment.setId(1L);
            enrollment.setStudentId(1L);
            enrollment.setCourseId(100L);
            enrollment.setStatus(Enrollment.STATUS_ACTIVE);

            when(enrollmentMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(enrollment);
            when(courseMapper.selectById(100L)).thenReturn(publishedCourse);

            enrollmentService.drop(1L, 100L);

            // 验证状态变为 DROPPED
            verify(enrollmentMapper).updateById(argThat(e ->
                    Enrollment.STATUS_DROPPED.equals(e.getStatus())));
            // 验证课程学生数 -1
            verify(courseMapper).updateById(argThat(course ->
                    course.getStudentCount() == 9));
        }

        @Test
        @DisplayName("退课失败 - 没有选课记录")
        void dropFailNotEnrolled() {
            when(enrollmentMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);

            RuntimeException ex = assertThrows(RuntimeException.class,
                    () -> enrollmentService.drop(1L, 100L));
            assertTrue(ex.getMessage().contains("未匹配到有效的报名记录"));
        }

        @Test
        @DisplayName("退课时课程学生数不会变负数")
        void dropStudentCountNotNegative() {
            Enrollment enrollment = new Enrollment();
            enrollment.setId(1L);
            enrollment.setStudentId(1L);
            enrollment.setCourseId(100L);
            enrollment.setStatus(Enrollment.STATUS_ACTIVE);

            Course zeroCourse = new Course();
            zeroCourse.setId(100L);
            zeroCourse.setStudentCount(0);

            when(enrollmentMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(enrollment);
            when(courseMapper.selectById(100L)).thenReturn(zeroCourse);

            enrollmentService.drop(1L, 100L);

            // 学生数为 0 时不应该再减
            verify(courseMapper, never()).updateById(any(Course.class));
        }
    }

    // =========================================================================
    // VO 转换测试
    // =========================================================================
    @Nested
    @DisplayName("VO 转换测试")
    class ConvertTests {

        @Test
        @DisplayName("Entity 转 VO - 空值安全")
        void convertToVONullSafe() {
            assertNull(enrollmentService.convertToVO(null));
        }

        @Test
        @DisplayName("空列表转换")
        void convertEmptyList() {
            assertTrue(enrollmentService.convertToVOList(null).isEmpty());
            assertTrue(enrollmentService.convertToVOList(java.util.Collections.emptyList()).isEmpty());
        }
    }
}
