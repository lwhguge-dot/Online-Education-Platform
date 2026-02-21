package com.eduplatform.homework.service;

import com.eduplatform.homework.dto.HomeworkCreateDTO;
import com.eduplatform.homework.entity.Homework;
import com.eduplatform.homework.entity.HomeworkQuestion;
import com.eduplatform.homework.mapper.HomeworkMapper;
import com.eduplatform.homework.mapper.HomeworkQuestionMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 作业编排写模型服务。
 * 说明：集中承接作业复制与题目导入写流程，降低 HomeworkService 的职责复杂度。
 */
@Service
@RequiredArgsConstructor
public class HomeworkAuthoringService {

    private final HomeworkMapper homeworkMapper;
    private final HomeworkQuestionMapper questionMapper;

    /**
     * 发布新作业（教师功能）。
     * 包含作业主体元数据保存及题库的原子化持久化。
     */
    @Transactional
    public Homework createHomework(HomeworkCreateDTO dto) {
        Homework homework = new Homework();
        homework.setChapterId(dto.getChapterId());
        homework.setCourseId(dto.getCourseId());
        homework.setTitle(dto.getTitle());
        homework.setDescription(dto.getDescription());
        homework.setHomeworkType(dto.getHomeworkType());
        homework.setTotalScore(dto.getTotalScore());
        homework.setDeadline(dto.getDeadline());
        homeworkMapper.insert(homework);

        // 处理关联题目
        if (dto.getQuestions() != null && !dto.getQuestions().isEmpty()) {
            int order = 1;
            for (HomeworkCreateDTO.QuestionDTO q : dto.getQuestions()) {
                HomeworkQuestion question = new HomeworkQuestion();
                question.setHomeworkId(homework.getId());
                question.setQuestionType(q.getQuestionType());
                question.setContent(q.getContent());
                question.setOptions(q.getOptions());
                question.setCorrectAnswer(q.getCorrectAnswer());
                question.setAnswerAnalysis(q.getAnswerAnalysis());
                question.setScore(q.getScore() != null ? q.getScore() : 10);
                question.setSortOrder(q.getSortOrder() != null ? q.getSortOrder() : order++);
                questionMapper.insert(question);
            }
        }

        return homework;
    }

    /**
     * 复制作业到指定章节。
     */
    @Transactional
    public Homework duplicateHomework(Long homeworkId, Long targetChapterId, String newTitle) {
        Homework source = homeworkMapper.selectById(homeworkId);
        if (source == null) {
            throw new RuntimeException("源作业不存在");
        }

        // 创建新作业，截止日期清空由教师重新设置
        Homework newHomework = new Homework();
        newHomework.setChapterId(targetChapterId != null ? targetChapterId : source.getChapterId());
        newHomework.setTitle(newTitle != null ? newTitle : source.getTitle() + " (副本)");
        newHomework.setDescription(source.getDescription());
        newHomework.setHomeworkType(source.getHomeworkType());
        newHomework.setTotalScore(source.getTotalScore());
        newHomework.setDeadline(null);
        homeworkMapper.insert(newHomework);

        // 批量复制题目内容到新作业
        List<HomeworkQuestion> questions = questionMapper.findByHomeworkId(homeworkId);
        for (HomeworkQuestion q : questions) {
            HomeworkQuestion newQuestion = new HomeworkQuestion();
            newQuestion.setHomeworkId(newHomework.getId());
            newQuestion.setQuestionType(q.getQuestionType());
            newQuestion.setContent(q.getContent());
            newQuestion.setOptions(q.getOptions());
            newQuestion.setCorrectAnswer(q.getCorrectAnswer());
            newQuestion.setAnswerAnalysis(q.getAnswerAnalysis());
            newQuestion.setScore(q.getScore());
            newQuestion.setSortOrder(q.getSortOrder());
            questionMapper.insert(newQuestion);
        }

        return newHomework;
    }

    /**
     * 批量导入题目并回写作业总分。
     */
    @Transactional
    public Map<String, Object> importQuestions(Long homeworkId, List<HomeworkCreateDTO.QuestionDTO> questions) {
        Homework homework = homeworkMapper.selectById(homeworkId);
        if (homework == null) {
            throw new RuntimeException("作业不存在");
        }

        // 获取当前最大排序号
        List<HomeworkQuestion> existingQuestions = questionMapper.findByHomeworkId(homeworkId);
        int maxOrder = existingQuestions.stream()
                .mapToInt(q -> q.getSortOrder() != null ? q.getSortOrder() : 0)
                .max()
                .orElse(0);

        int successCount = 0;
        int failCount = 0;
        int totalScore = homework.getTotalScore() != null ? homework.getTotalScore() : 0;

        for (HomeworkCreateDTO.QuestionDTO q : questions) {
            try {
                HomeworkQuestion question = new HomeworkQuestion();
                question.setHomeworkId(homeworkId);
                question.setQuestionType(q.getQuestionType());
                question.setContent(q.getContent());
                question.setOptions(q.getOptions());
                question.setCorrectAnswer(q.getCorrectAnswer());
                question.setAnswerAnalysis(q.getAnswerAnalysis());
                question.setScore(q.getScore() != null ? q.getScore() : 10);
                question.setSortOrder(++maxOrder);
                questionMapper.insert(question);

                totalScore += question.getScore();
                successCount++;
            } catch (Exception e) {
                failCount++;
            }
        }

        // 更新作业总分
        homework.setTotalScore(totalScore);
        homeworkMapper.updateById(homework);

        Map<String, Object> result = new HashMap<>();
        result.put("successCount", successCount);
        result.put("failCount", failCount);
        result.put("total", questions.size());
        result.put("newTotalScore", totalScore);
        return result;
    }
}
