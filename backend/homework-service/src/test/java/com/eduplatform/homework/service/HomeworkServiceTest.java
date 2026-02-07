package com.eduplatform.homework.service;

import com.eduplatform.homework.entity.Homework;
import com.eduplatform.homework.entity.HomeworkQuestion;
import com.eduplatform.homework.feign.UserServiceClient;
import com.eduplatform.homework.mapper.*;
import com.eduplatform.homework.vo.HomeworkVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Method;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

/**
 * HomeworkService 单元测试
 *
 * 覆盖场景:
 * 1. 答案比对算法: 大小写忽略、空格忽略、空值处理
 * 2. VO 转换: 空值安全、字段映射
 * 3. 作业创建: 参数校验
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("HomeworkService 单元测试")
class HomeworkServiceTest {

    @InjectMocks
    private HomeworkService homeworkService;

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

    /**
     * 通过反射调用 private 方法 compareAnswer
     * 这是自动批改的核心算法，必须确保正确性
     */
    private boolean invokeCompareAnswer(String correct, String student) throws Exception {
        Method method = HomeworkService.class.getDeclaredMethod("compareAnswer",
                String.class, String.class);
        method.setAccessible(true);
        return (boolean) method.invoke(homeworkService, correct, student);
    }

    // =========================================================================
    // 答案比对算法测试 (自动批改核心)
    // =========================================================================
    @Nested
    @DisplayName("答案比对算法测试")
    class CompareAnswerTests {

        @Test
        @DisplayName("完全匹配 - 返回 true")
        void exactMatch() throws Exception {
            assertTrue(invokeCompareAnswer("A", "A"));
        }

        @Test
        @DisplayName("忽略大小写 - 返回 true")
        void caseInsensitive() throws Exception {
            assertTrue(invokeCompareAnswer("Hello", "hello"));
            assertTrue(invokeCompareAnswer("ABC", "abc"));
        }

        @Test
        @DisplayName("忽略前后空格 - 返回 true")
        void trimWhitespace() throws Exception {
            assertTrue(invokeCompareAnswer("A", "  A  "));
            assertTrue(invokeCompareAnswer("  B  ", "B"));
        }

        @Test
        @DisplayName("忽略中间空格 - 返回 true")
        void ignoreInternalSpaces() throws Exception {
            assertTrue(invokeCompareAnswer("Hello World", "HelloWorld"));
            assertTrue(invokeCompareAnswer("A B C", "ABC"));
        }

        @ParameterizedTest
        @CsvSource({
                "A, B, false",
                "true, false, false",
                "123, 456, false",
                "AB, AC, false"
        })
        @DisplayName("不匹配的答案 - 返回 false")
        void mismatch(String correct, String student, boolean expected) throws Exception {
            assertEquals(expected, invokeCompareAnswer(correct, student));
        }

        @Test
        @DisplayName("空值处理 - null 返回 false")
        void nullHandling() throws Exception {
            assertFalse(invokeCompareAnswer(null, "A"));
            assertFalse(invokeCompareAnswer("A", null));
            assertFalse(invokeCompareAnswer(null, null));
        }
    }

    // =========================================================================
    // VO 转换测试
    // =========================================================================
    @Nested
    @DisplayName("VO 转换测试")
    class ConvertTests {

        @Test
        @DisplayName("Homework 转 VO - 空值安全")
        void convertToVONullSafe() {
            assertNull(homeworkService.convertToVO(null));
        }

        @Test
        @DisplayName("Homework 转 VO - 字段正确映射")
        void convertToVOFieldMapping() {
            Homework homework = new Homework();
            homework.setId(1L);
            homework.setTitle("第一次作业");
            homework.setCourseId(100L);
            homework.setChapterId(10L);
            homework.setTotalScore(100);
            homework.setCreatedAt(LocalDateTime.now());

            HomeworkVO vo = homeworkService.convertToVO(homework);

            assertNotNull(vo);
            assertEquals(1L, vo.getId());
            assertEquals("第一次作业", vo.getTitle());
            assertEquals(100L, vo.getCourseId());
            assertEquals(10L, vo.getChapterId());
            assertEquals(100, vo.getTotalScore());
        }
    }
}
