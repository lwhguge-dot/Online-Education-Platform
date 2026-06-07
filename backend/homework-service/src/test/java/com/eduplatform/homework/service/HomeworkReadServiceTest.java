package com.eduplatform.homework.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.eduplatform.homework.entity.Homework;
import com.eduplatform.homework.entity.HomeworkQuestionDiscussion;
import com.eduplatform.homework.feign.UserServiceClient;
import com.eduplatform.homework.mapper.HomeworkAnswerMapper;
import com.eduplatform.homework.mapper.HomeworkMapper;
import com.eduplatform.homework.mapper.HomeworkQuestionDiscussionMapper;
import com.eduplatform.homework.mapper.HomeworkQuestionMapper;
import com.eduplatform.homework.mapper.HomeworkSubmissionMapper;
import com.eduplatform.homework.mapper.HomeworkUnlockMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.Collections;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * HomeworkReadService 单元测试。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("HomeworkReadService 单元测试")
class HomeworkReadServiceTest {

    @InjectMocks
    private HomeworkReadService homeworkReadService;

    @Mock
    private HomeworkMapper homeworkMapper;

    @Mock
    private HomeworkQuestionMapper questionMapper;

    @Mock
    private HomeworkUnlockMapper unlockMapper;

    @Mock
    private HomeworkSubmissionMapper submissionMapper;

    @Mock
    private HomeworkAnswerMapper answerMapper;

    @Mock
    private HomeworkQuestionDiscussionMapper discussionMapper;

    @Mock
    private UserServiceClient userServiceClient;

    @Nested
    @DisplayName("基础读取能力")
    class BasicReadTests {

        @Test
        @DisplayName("作业不存在时详情返回 null")
        void getHomeworkDetailShouldReturnNullWhenHomeworkMissing() {
            when(homeworkMapper.selectById(999L)).thenReturn(null);

            Map<String, Object> detail = homeworkReadService.getHomeworkDetail(999L);

            assertNull(detail);
        }

        @Test
        @DisplayName("教师待回复问题数量应只统计 pending 状态")
        @SuppressWarnings("unchecked")
        void getTeacherPendingQuestionsCountShouldOnlyCountPending() {
            HomeworkQuestionDiscussion pending1 = new HomeworkQuestionDiscussion();
            pending1.setId(1L);
            pending1.setStatus("pending");

            HomeworkQuestionDiscussion pending2 = new HomeworkQuestionDiscussion();
            pending2.setId(2L);
            pending2.setStatus("pending");

            when(discussionMapper.selectList(any(LambdaQueryWrapper.class)))
                    .thenReturn(Arrays.asList(pending1, pending2));

            int count = homeworkReadService.getTeacherPendingQuestionsCount(101L);

            assertEquals(2, count);
            verify(discussionMapper).selectList(any(LambdaQueryWrapper.class));
        }

        @Test
        @DisplayName("紧急作业在无解锁记录时返回空列表")
        @SuppressWarnings("unchecked")
        void urgentHomeworksShouldReturnEmptyWhenNoUnlocks() {
            when(unlockMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(Collections.emptyList());

            assertNotNull(homeworkReadService.getStudentUrgentHomeworks(1001L, 2));
            assertEquals(0, homeworkReadService.getStudentUrgentHomeworks(1001L, 2).size());
        }
    }
}
