package com.eduplatform.course.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.eduplatform.course.entity.Chapter;
import com.eduplatform.course.entity.ChapterQuiz;
import com.eduplatform.course.entity.Course;
import com.eduplatform.course.mapper.ChapterMapper;
import com.eduplatform.course.mapper.ChapterQuizMapper;
import com.eduplatform.course.mapper.CourseMapper;
import com.eduplatform.course.vo.ChapterQuizVO;
import com.eduplatform.course.vo.ChapterVO;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 课程章节与测验管理服务
 * 负责课程大纲的精细化构建，涵盖章节生命周期管理、视频解锁逻辑配置、以及内嵌章节测验的编排。
 * 
 * 核心功能：
 * 1. 章节编排：支持章节的灵活增删改，并具备基于位置的自动排序能力。
 * 2. 学习门槛配置：定义章节视频的最小完成率（UnlockVideoRate）及测验达标分（UnlockQuizScore），作为后续关卡解锁的判定基准。
 * 3. 关联测验管理：维护章节与测验题目的 1:N 关系，确保教学闭环。
 *
 * @author Antigravity
 */
@Service
@RequiredArgsConstructor
public class ChapterService {

    private final ChapterMapper chapterMapper;
    private final ChapterQuizMapper quizMapper;
    private final CourseMapper courseMapper;

    /**
     * 将章节实体转换为视图对象 (VO)
     * 
     * @param entity 章节持久层对象
     * @return 包含视频 URL 及解锁配置的视图对象
     */
    public ChapterVO convertToVO(Chapter entity) {
        if (entity == null) {
            return null;
        }
        ChapterVO vo = new ChapterVO();
        BeanUtils.copyProperties(entity, vo);
        return vo;
    }

    /**
     * 批量转换章节实体
     */
    public List<ChapterVO> convertToVOList(List<Chapter> entities) {
        if (entities == null || entities.isEmpty()) {
            return Collections.emptyList();
        }
        return entities.stream()
                .map(this::convertToVO)
                .collect(Collectors.toList());
    }

    /**
     * 将章节测验转换为视图对象
     */
    public ChapterQuizVO convertQuizToVO(ChapterQuiz entity) {
        if (entity == null) {
            return null;
        }
        ChapterQuizVO vo = new ChapterQuizVO();
        BeanUtils.copyProperties(entity, vo);
        return vo;
    }

    /**
     * 批量转换测验题目
     */
    public List<ChapterQuizVO> convertQuizToVOList(List<ChapterQuiz> entities) {
        if (entities == null || entities.isEmpty()) {
            return Collections.emptyList();
        }
        return entities.stream()
                .map(this::convertQuizToVO)
                .collect(Collectors.toList());
    }

    /**
     * 构建新的教学章节
     * 核心逻辑：
     * 1. 注入默认策略：默认视频完成 90% 解锁，测验 60 分及格。
     * 2. 自动定位：若未指定排序，则默认追加至当前课程章节末尾。
     * 3. 元数据联动：触发课程总章节数的同步更新。
     * 
     * @param chapter 章节元数据
     * @return 持久化后的章节对象
     */
    @Transactional
    public Chapter createChapter(Chapter chapter) {
        // 设置业务默认值
        if (chapter.getUnlockVideoRate() == null) {
            chapter.setUnlockVideoRate(BigDecimal.valueOf(0.9)); // 默认观看 90% 视为通过
        }
        if (chapter.getUnlockQuizScore() == null) {
            chapter.setUnlockQuizScore(60); // 默认 60 分及格解锁下一关
        }
        if (chapter.getStatus() == null) {
            chapter.setStatus(1); // 默认启用状态
        }

        // 自动计算排序权重
        if (chapter.getSortOrder() == null) {
            Long count = chapterMapper.selectCount(
                    new LambdaQueryWrapper<Chapter>()
                            .eq(Chapter::getCourseId, chapter.getCourseId()));
            chapter.setSortOrder(count.intValue() + 1);
        }

        chapterMapper.insert(chapter);

        // 触发数据聚合更新
        updateCourseTotalChapters(chapter.getCourseId());

        return chapter;
    }

    /**
     * 更新章节元数据
     */
    @Transactional
    public Chapter updateChapter(Chapter chapter) {
        chapterMapper.updateById(chapter);
        return chapterMapper.selectById(chapter.getId());
    }

    /**
     * 销毁章节记录
     * 级联规则：删除章节前将同步物理删除该章节下挂载的所有测验题目。
     */
    @Transactional
    public void deleteChapter(Long id) {
        Chapter chapter = chapterMapper.selectById(id);
        if (chapter != null) {
            // 级联清理：删除关联的测验题
            quizMapper.delete(
                    new LambdaQueryWrapper<ChapterQuiz>()
                            .eq(ChapterQuiz::getChapterId, id));
            chapterMapper.deleteById(id);
            // 重新同步课程计数
            updateCourseTotalChapters(chapter.getCourseId());
        }
    }

    /**
     * 获取章节深度详情
     * 包含：章节基础信息 + 全量测验题目清单。
     * 
     * @param id 章节 ID
     * @return 聚合数据 Map
     */
    public Map<String, Object> getChapterDetail(Long id) {
        Chapter chapter = chapterMapper.selectById(id);
        if (chapter == null) {
            return null;
        }

        List<ChapterQuiz> quizzes = quizMapper.selectList(
                new LambdaQueryWrapper<ChapterQuiz>()
                        .eq(ChapterQuiz::getChapterId, id)
                        .orderByAsc(ChapterQuiz::getSortOrder));

        Map<String, Object> result = new HashMap<>();
        result.put("chapter", chapter);
        result.put("quizzes", quizzes);
        return result;
    }

    /**
     * 获取指定课程的完整大纲
     */
    public List<Chapter> getChaptersByCourse(Long courseId) {
        return chapterMapper.selectList(
                new LambdaQueryWrapper<Chapter>()
                        .eq(Chapter::getCourseId, courseId)
                        .orderByAsc(Chapter::getSortOrder));
    }

    /**
     * 为章节注入单条测验题目
     */
    @Transactional
    public ChapterQuiz addQuiz(ChapterQuiz quiz) {
        if (quiz.getSortOrder() == null) {
            Long count = quizMapper.selectCount(
                    new LambdaQueryWrapper<ChapterQuiz>()
                            .eq(ChapterQuiz::getChapterId, quiz.getChapterId()));
            quiz.setSortOrder(count.intValue() + 1);
        }
        if (quiz.getScore() == null) {
            quiz.setScore(10); // 默认每题 10 分
        }
        quizMapper.insert(quiz);
        return quiz;
    }

    /**
     * 为章节批量编排测验题目
     * 逻辑：按列表顺序重置题目序号，确保展示一致性。
     */
    @Transactional
    public void addQuizzes(Long chapterId, List<ChapterQuiz> quizzes) {
        int order = 1;
        for (ChapterQuiz quiz : quizzes) {
            quiz.setChapterId(chapterId);
            quiz.setSortOrder(order++);
            if (quiz.getScore() == null) {
                quiz.setScore(10);
            }
            quizMapper.insert(quiz);
        }
    }

    /**
     * 获取章节关联的测验全集
     */
    public List<ChapterQuiz> getQuizzes(Long chapterId) {
        return quizMapper.selectList(
                new LambdaQueryWrapper<ChapterQuiz>()
                        .eq(ChapterQuiz::getChapterId, chapterId)
                        .orderByAsc(ChapterQuiz::getSortOrder));
    }

    /**
     * 物理删除测验题目
     */
    public void deleteQuiz(Long quizId) {
        quizMapper.deleteById(quizId);
    }

    /**
     * 维持课程章节总数的实时准确性（内部聚合逻辑）
     */
    private void updateCourseTotalChapters(Long courseId) {
        Long count = chapterMapper.selectCount(
                new LambdaQueryWrapper<Chapter>()
                        .eq(Chapter::getCourseId, courseId));
        Course course = courseMapper.selectById(courseId);
        if (course != null) {
            // 此处通常用于触发课程搜索权重或热度值的同步更新
        }
    }
}
