package com.eduplatform.progress.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.eduplatform.progress.entity.ChapterProgress;
import com.eduplatform.progress.mapper.ChapterMapper;
import com.eduplatform.progress.mapper.ChapterProgressMapper;
import com.eduplatform.progress.mapper.ChapterQuizMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * ProgressAnalyticsService 单元测试
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ProgressAnalyticsService 单元测试")
class ProgressAnalyticsServiceTest {

    @InjectMocks
    private ProgressAnalyticsService progressAnalyticsService;

    @Mock
    private ChapterProgressMapper progressMapper;

    @Mock
    private ChapterMapper chapterMapper;

    @Mock
    private ChapterQuizMapper quizMapper;

    @Test
    @DisplayName("知识掌握度等级应返回 UTF-8 可读中文文案")
    void knowledgeMasteryLevelShouldUseReadableChineseText() {
        ChapterProgress progressA = new ChapterProgress();
        progressA.setStudentId(1L);
        progressA.setCourseId(100L);
        progressA.setChapterId(11L);
        progressA.setVideoRate(new BigDecimal("0.80"));
        progressA.setQuizScore(70);
        progressA.setIsCompleted(1);

        ChapterProgress progressB = new ChapterProgress();
        progressB.setStudentId(1L);
        progressB.setCourseId(100L);
        progressB.setChapterId(12L);
        progressB.setVideoRate(new BigDecimal("0.90"));
        progressB.setQuizScore(80);
        progressB.setIsCompleted(1);

        List<ChapterProgress> progressList = Arrays.asList(progressA, progressB);
        when(progressMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(progressList);

        // 平均分 75，对应“熟练”；该断言用于防止乱码文本回归
        Map<String, Object> mastery = progressAnalyticsService.getKnowledgeMastery(1L);

        assertNotNull(mastery);
        assertEquals("熟练", mastery.get("masteryLevel"));
        assertEquals(75, mastery.get("avgQuizScore"));
        assertEquals(85, mastery.get("avgVideoCompletion"));
    }
}
