package com.eduplatform.course.service;

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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * CourseWorkflowService 单元测试。
 * 说明：覆盖课程审核状态流转的核心行为，确保拆分后工作流语义不回退。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("CourseWorkflowService 单元测试")
class CourseWorkflowServiceTest {

    @InjectMocks
    private CourseWorkflowService courseWorkflowService;

    @Mock
    private CourseMapper courseMapper;

    @Mock
    private AuditLogClient auditLogClient;

    @Nested
    @DisplayName("管理员审核流转")
    class AuditFlowTests {

        @Test
        @DisplayName("审核通过后课程变为已发布")
        void auditApproveShouldBecomePublished() {
            Course reviewing = new Course();
            reviewing.setId(200L);
            reviewing.setTitle("审核课程");
            reviewing.setStatus(Course.STATUS_REVIEWING);

            when(courseMapper.selectById(200L)).thenReturn(reviewing);

            courseWorkflowService.auditCourse(200L, "APPROVE", "通过", 10L, "管理员", "127.0.0.1");

            verify(courseMapper).updateById(argThat(updated -> {
                assertEquals(Course.STATUS_PUBLISHED, updated.getStatus());
                assertEquals(10L, updated.getAuditBy());
                assertNotNull(updated.getAuditTime());
                assertEquals("通过", updated.getAuditRemark());
                return true;
            }));
        }

        @Test
        @DisplayName("审核驳回后课程变为已驳回")
        void auditRejectShouldBecomeRejected() {
            Course reviewing = new Course();
            reviewing.setId(201L);
            reviewing.setTitle("驳回课程");
            reviewing.setStatus(Course.STATUS_REVIEWING);

            when(courseMapper.selectById(201L)).thenReturn(reviewing);

            courseWorkflowService.auditCourse(201L, "REJECT", "内容待完善", 11L, "管理员", "127.0.0.1");

            verify(courseMapper).updateById(argThat(updated -> {
                assertEquals(Course.STATUS_REJECTED, updated.getStatus());
                assertEquals(11L, updated.getAuditBy());
                assertNotNull(updated.getAuditTime());
                assertEquals("内容待完善", updated.getAuditRemark());
                return true;
            }));
        }
    }
}
