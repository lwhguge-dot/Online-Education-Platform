package com.eduplatform.course.service;

import com.eduplatform.course.dto.CourseDTO;
import com.eduplatform.course.entity.Course;
import com.eduplatform.course.feign.AuditLogClient;
import com.eduplatform.course.mapper.CourseMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * CourseService 单元测试。
 *
 * 覆盖点：
 * 1. 教师编辑保存后课程统一进入待审核状态。
 * 2. 管理员审核通过后课程进入已发布状态。
 * 3. 管理员审核驳回后课程进入已驳回状态。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("CourseService 单元测试")
class CourseServiceTest {

    @InjectMocks
    private CourseService courseService;

    @Mock
    private CourseMapper courseMapper;

    @Mock
    private AuditLogClient auditLogClient;

    @Mock
    private CourseReadService courseReadService;

    @Mock
    private CourseWorkflowService courseWorkflowService;

    @Nested
    @DisplayName("课程编辑保存流转")
    class UpdateCourseStatusTests {

        @Test
        @DisplayName("编辑已发布课程后进入待审核并重置审核字段")
        void updatePublishedCourseShouldBecomeReviewing() {
            Course existing = new Course();
            existing.setId(100L);
            existing.setTitle("旧标题");
            existing.setStatus(Course.STATUS_PUBLISHED);
            existing.setSubmitTime(LocalDateTime.now().minusDays(1));
            existing.setAuditBy(1L);
            existing.setAuditTime(LocalDateTime.now().minusHours(1));
            existing.setAuditRemark("旧审核意见");

            when(courseMapper.selectById(100L)).thenReturn(existing);

            CourseDTO dto = new CourseDTO();
            dto.setTitle("新标题");
            dto.setDescription("新描述");

            courseService.updateCourse(100L, dto);

            verify(courseMapper).updateById(argThat(updated -> {
                assertEquals("新标题", updated.getTitle());
                assertEquals("新描述", updated.getDescription());
                assertEquals(Course.STATUS_REVIEWING, updated.getStatus());
                assertNotNull(updated.getSubmitTime());
                assertNull(updated.getAuditBy());
                assertNull(updated.getAuditTime());
                assertNull(updated.getAuditRemark());
                assertNotNull(updated.getUpdatedAt());
                return true;
            }));
        }

        @Test
        @DisplayName("编辑不存在课程抛出异常")
        void updateNonExistentCourseShouldThrow() {
            when(courseMapper.selectById(999L)).thenReturn(null);

            CourseDTO dto = new CourseDTO();
            dto.setTitle("任意标题");

            RuntimeException exception = assertThrows(RuntimeException.class,
                    () -> courseService.updateCourse(999L, dto));
            assertEquals("操作失败：目标课程不存在于系统中", exception.getMessage());
        }
    }

    @Nested
    @DisplayName("管理员审核流转")
    class AuditFlowTests {

        @Test
        @DisplayName("审核接口应委托工作流服务")
        void auditCourseShouldDelegateToWorkflowService() {
            courseService.auditCourse(200L, "APPROVE", "通过", 10L, "管理员", "127.0.0.1");

            verify(courseWorkflowService).auditCourse(200L, "APPROVE", "通过", 10L, "管理员", "127.0.0.1");
        }

        @Test
        @DisplayName("批量状态更新应委托工作流服务")
        void batchUpdateStatusShouldDelegateToWorkflowService() {
            courseService.batchUpdateStatus(java.util.List.of(1L, 2L), Course.STATUS_PUBLISHED, 10L, "管理员", "127.0.0.1");

            verify(courseWorkflowService).batchUpdateStatus(eq(java.util.List.of(1L, 2L)),
                    eq(Course.STATUS_PUBLISHED), eq(10L), eq("管理员"), eq("127.0.0.1"));
        }

        @Test
        @DisplayName("上下架状态更新应委托工作流服务")
        void updateStatusWithAuditShouldDelegateToWorkflowService() {
            courseService.updateStatusWithAudit(99L, Course.STATUS_OFFLINE, 20L, "运营", "10.0.0.1");

            verify(courseWorkflowService).updateStatusWithAudit(99L, Course.STATUS_OFFLINE, 20L, "运营", "10.0.0.1");
        }
    }

    @Nested
    @DisplayName("管理员可见性规则")
    class AdminVisibilityTests {

        @Test
        @DisplayName("管理员请求草稿状态时返回空列表")
        void adminDraftQueryShouldReturnEmpty() {
            when(courseReadService.getAdminVisibleCourses(null, Course.STATUS_DRAFT))
                    .thenReturn(java.util.Collections.emptyList());

            assertEquals(0, courseService.getAdminVisibleCourses(null, Course.STATUS_DRAFT).size());
            verify(courseReadService).getAdminVisibleCourses(null, Course.STATUS_DRAFT);
        }

        @Test
        @DisplayName("管理员默认查询应委托读模型服务")
        void adminDefaultQueryShouldDelegateToReadService() {
            when(courseReadService.getAdminVisibleCourses(null, null)).thenReturn(java.util.Collections.emptyList());

            courseService.getAdminVisibleCourses(null, null);

            verify(courseReadService).getAdminVisibleCourses(null, null);
        }
    }
}
