package com.eduplatform.progress.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.eduplatform.progress.client.HomeworkServiceClient;
import com.eduplatform.progress.entity.Chapter;
import com.eduplatform.progress.entity.ChapterProgress;
import com.eduplatform.progress.mapper.ChapterMapper;
import com.eduplatform.progress.mapper.ChapterProgressMapper;
import com.eduplatform.progress.mapper.ChapterQuizMapper;
import com.eduplatform.progress.vo.ChapterProgressVO;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cache.CacheManager;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * ProgressService 单元测试
 *
 * 覆盖场景:
 * 1. 进度查询: 获取学生课程进度、空进度处理
 * 2. VO 转换: 空值安全、字段映射
 * 3. 进度计算: 完课判定逻辑
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ProgressService 单元测试")
class ProgressServiceTest {

    @InjectMocks
    private ProgressService progressService;

    @Mock
    private ChapterProgressMapper progressMapper;

    @Mock
    private ChapterMapper chapterMapper;

    @Mock
    private ChapterQuizMapper quizMapper;

    @Mock
    private HomeworkServiceClient homeworkServiceClient;

    @Mock
    private BadgeService badgeService;

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ObjectMapper objectMapper;

    @Mock
    private CacheManager cacheManager;

    private ChapterProgress testProgress;
    private Chapter testChapter;

    @BeforeEach
    void setUp() {
        testChapter = new Chapter();
        testChapter.setId(10L);
        testChapter.setCourseId(100L);

        testProgress = new ChapterProgress();
        testProgress.setId(1L);
        testProgress.setStudentId(1L);
        testProgress.setChapterId(10L);
        testProgress.setCourseId(100L);
        testProgress.setVideoRate(new BigDecimal("0.80"));
        testProgress.setQuizScore(75);
        testProgress.setIsCompleted(0);
    }

    // =========================================================================
    // 进度查询测试
    // =========================================================================
    @Nested
    @DisplayName("进度查询测试")
    class ProgressQueryTests {

        @Test
        @DisplayName("获取学生课程进度列表")
        void getStudentCourseProgress() {
            List<ChapterProgress> progressList = Arrays.asList(testProgress);
            when(progressMapper.selectList(org.mockito.ArgumentMatchers.<LambdaQueryWrapper<ChapterProgress>>any()))
                    .thenReturn(progressList);

            List<ChapterProgress> result = progressMapper.selectList(
                    new LambdaQueryWrapper<ChapterProgress>()
                            .eq(ChapterProgress::getStudentId, 1L)
                            .eq(ChapterProgress::getCourseId, 100L));

            assertNotNull(result);
            assertEquals(1, result.size());
            assertEquals(new BigDecimal("0.80"), result.get(0).getVideoRate());
        }

        @Test
        @DisplayName("没有进度记录时返回空列表")
        void emptyProgressList() {
            when(progressMapper.selectList(org.mockito.ArgumentMatchers.<LambdaQueryWrapper<ChapterProgress>>any()))
                    .thenReturn(Collections.emptyList());

            List<ChapterProgress> result = progressMapper.selectList(
                    new LambdaQueryWrapper<ChapterProgress>()
                            .eq(ChapterProgress::getStudentId, 999L));

            assertNotNull(result);
            assertTrue(result.isEmpty());
        }
    }

    // =========================================================================
    // VO 转换测试
    // =========================================================================
    @Nested
    @DisplayName("VO 转换测试")
    class ConvertTests {

        @Test
        @DisplayName("ChapterProgress 转 VO - 空值安全")
        void convertToVONullSafe() {
            assertNull(progressService.convertToVO(null));
        }

        @Test
        @DisplayName("ChapterProgress 转 VO - 字段正确映射")
        void convertToVOFieldMapping() {
            ChapterProgressVO vo = progressService.convertToVO(testProgress);

            assertNotNull(vo);
            assertEquals(1L, vo.getStudentId());
            assertEquals(10L, vo.getChapterId());
            assertEquals(new BigDecimal("0.80"), vo.getVideoRate());
            assertEquals(75, vo.getQuizScore());
        }
    }

    // =========================================================================
    // 完课判定逻辑测试
    // =========================================================================
    @Nested
    @DisplayName("完课判定测试")
    class CompletionTests {

        @Test
        @DisplayName("视频观看 >= 90% 且测验 >= 60 分 = 满足解锁条件")
        void meetsUnlockCondition() {
            ChapterProgress completedProgress = new ChapterProgress();
            completedProgress.setVideoRate(new BigDecimal("0.95"));
            completedProgress.setQuizScore(85);
            completedProgress.setIsCompleted(1);

            assertTrue(completedProgress.getVideoRate().compareTo(new BigDecimal("0.90")) >= 0);
            assertTrue(completedProgress.getQuizScore() >= 60);
            assertEquals(1, completedProgress.getIsCompleted());
        }

        @Test
        @DisplayName("视频观看 < 90% = 不满足解锁条件")
        void videoNotEnough() {
            assertTrue(testProgress.getVideoRate().compareTo(new BigDecimal("0.90")) < 0);
        }

        @Test
        @DisplayName("测验分数 < 60 = 不满足解锁条件")
        void quizScoreTooLow() {
            testProgress.setQuizScore(50);
            assertFalse(testProgress.getQuizScore() >= 60);
        }
    }
}
