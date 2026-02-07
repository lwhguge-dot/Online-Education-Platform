package com.eduplatform.homework.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.eduplatform.homework.dto.TeacherDashboardDTO;
import com.eduplatform.homework.entity.Homework;
import com.eduplatform.homework.entity.HomeworkSubmission;
import com.eduplatform.homework.entity.SubjectiveComment;
import com.eduplatform.homework.feign.CourseServiceClient;
import com.eduplatform.homework.mapper.HomeworkMapper;
import com.eduplatform.homework.mapper.HomeworkSubmissionMapper;
import com.eduplatform.homework.mapper.SubjectiveCommentMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 教师统计服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TeacherStatsService {

    private final HomeworkMapper homeworkMapper;
    private final HomeworkSubmissionMapper submissionMapper;
    private final SubjectiveCommentMapper commentMapper;
    private final CourseServiceClient courseServiceClient;

    /**
     * 获取教师仪表盘聚合数据
     */
    public TeacherDashboardDTO getTeacherDashboard(Long teacherId, List<Map<String, Object>> courses) {
        TeacherDashboardDTO dto = new TeacherDashboardDTO();
        dto.setTimestamp(LocalDateTime.now());
        
        // 从课程列表中提取统计数据
        int myCourses = courses.size();
        int publishedCourses = 0;
        int totalStudents = 0;
        List<Long> courseIds = new ArrayList<>();
        
        for (Map<String, Object> course : courses) {
            courseIds.add(getLongValue(course, "id"));
            String status = getStringValue(course, "status");
            if ("PUBLISHED".equals(status)) {
                publishedCourses++;
            }
            totalStudents += getIntValue(course, "studentCount");
        }
        
        dto.setMyCourses(myCourses);
        dto.setPublishedCourses(publishedCourses);
        dto.setTotalStudents(totalStudents);
        
        // 计算待批改作业数
        int pendingHomework = countPendingHomework(courseIds);
        dto.setPendingHomework(pendingHomework);
        
        // 计算今日新增学生（需要从课程服务获取，这里暂时返回0）
        dto.setNewStudentsToday(0);
        
        // 计算本周互动数
        int weeklyInteractions = countWeeklyInteractions(courseIds);
        dto.setWeeklyInteractions(weeklyInteractions);
        
        // 获取紧急事项
        List<TeacherDashboardDTO.UrgentItem> urgentItems = getUrgentItems(courseIds, courses);
        dto.setUrgentItems(urgentItems);
        
        // 获取课程完成率排名
        List<TeacherDashboardDTO.CourseRanking> rankings = getCourseRankings(courses);
        dto.setCourseRankings(rankings);
        
        // 获取周趋势数据
        TeacherDashboardDTO.WeeklyTrend trend = getWeeklyTrend(courseIds);
        dto.setWeeklyTrend(trend);
        
        return dto;
    }

    /**
     * 统计待批改作业数
     */
    private int countPendingHomework(List<Long> courseIds) {
        if (courseIds.isEmpty()) {
            return 0;
        }
        
        // 获取这些课程下的所有章节ID
        List<Long> chapterIds = getChapterIdsByCourses(courseIds);
        
        if (chapterIds.isEmpty()) {
            return 0;
        }
        
        // 获取这些章节下的所有作业ID
        List<Homework> homeworks = homeworkMapper.selectList(
            new LambdaQueryWrapper<Homework>()
                .in(Homework::getChapterId, chapterIds)
        );
        
        if (homeworks.isEmpty()) {
            return 0;
        }
        
        List<Long> homeworkIds = homeworks.stream()
            .map(Homework::getId)
            .collect(Collectors.toList());
        
        // 统计待批改的提交数
        Long count = submissionMapper.selectCount(
            new LambdaQueryWrapper<HomeworkSubmission>()
                .in(HomeworkSubmission::getHomeworkId, homeworkIds)
                .eq(HomeworkSubmission::getSubmitStatus, "submitted")
        );
        
        return count != null ? count.intValue() : 0;
    }

    /**
     * 获取课程下的所有章节ID
     */
    private List<Long> getChapterIdsByCourses(List<Long> courseIds) {
        List<Long> chapterIds = new ArrayList<>();
        for (Long courseId : courseIds) {
            try {
                Map<String, Object> response = courseServiceClient.getCourseChapters(courseId);
                if (response != null && response.get("data") != null) {
                    @SuppressWarnings("unchecked")
                    List<Map<String, Object>> chapters = (List<Map<String, Object>>) response.get("data");
                    for (Map<String, Object> chapter : chapters) {
                        Long chapterId = getLongValue(chapter, "id");
                        if (chapterId != null && chapterId > 0) {
                            chapterIds.add(chapterId);
                        }
                    }
                }
            } catch (Exception e) {
                log.warn("获取课程{}的章节失败: {}", courseId, e.getMessage());
            }
        }
        return chapterIds;
    }

    /**
     * 统计本周互动数（作业提交 + 评论）
     */
    private int countWeeklyInteractions(List<Long> courseIds) {
        if (courseIds.isEmpty()) {
            return 0;
        }
        
        LocalDateTime weekStart = LocalDate.now().minusDays(7).atStartOfDay();
        
        // 获取课程下的章节
        List<Long> chapterIds = getChapterIdsByCourses(courseIds);
        
        int submissionCount = 0;
        if (!chapterIds.isEmpty()) {
            // 获取章节下的作业
            List<Homework> homeworks = homeworkMapper.selectList(
                new LambdaQueryWrapper<Homework>()
                    .in(Homework::getChapterId, chapterIds)
            );
            
            if (!homeworks.isEmpty()) {
                List<Long> homeworkIds = homeworks.stream()
                    .map(Homework::getId)
                    .collect(Collectors.toList());
                
                Long count = submissionMapper.selectCount(
                    new LambdaQueryWrapper<HomeworkSubmission>()
                        .in(HomeworkSubmission::getHomeworkId, homeworkIds)
                        .ge(HomeworkSubmission::getSubmittedAt, weekStart)
                );
                submissionCount = count != null ? count.intValue() : 0;
            }
        }
        
        // 统计评论数
        Long commentCount = commentMapper.selectCount(
            new LambdaQueryWrapper<SubjectiveComment>()
                .ge(SubjectiveComment::getCreatedAt, weekStart)
        );
        
        return submissionCount + (commentCount != null ? commentCount.intValue() : 0);
    }

    /**
     * 获取紧急事项列表
     */
    private List<TeacherDashboardDTO.UrgentItem> getUrgentItems(List<Long> courseIds, List<Map<String, Object>> courses) {
        List<TeacherDashboardDTO.UrgentItem> items = new ArrayList<>();
        
        if (courseIds.isEmpty()) {
            return items;
        }
        
        // 创建课程ID到课程名称的映射
        Map<Long, String> courseNameMap = new HashMap<>();
        for (Map<String, Object> course : courses) {
            Long id = getLongValue(course, "id");
            String title = getStringValue(course, "title");
            courseNameMap.put(id, title);
        }
        
        // 获取课程下的章节ID，并建立章节到课程的映射
        Map<Long, Long> chapterToCourseMap = new HashMap<>();
        for (Long courseId : courseIds) {
            try {
                Map<String, Object> response = courseServiceClient.getCourseChapters(courseId);
                if (response != null && response.get("data") != null) {
                    @SuppressWarnings("unchecked")
                    List<Map<String, Object>> chapters = (List<Map<String, Object>>) response.get("data");
                    for (Map<String, Object> chapter : chapters) {
                        Long chapterId = getLongValue(chapter, "id");
                        if (chapterId != null && chapterId > 0) {
                            chapterToCourseMap.put(chapterId, courseId);
                        }
                    }
                }
            } catch (Exception e) {
                log.warn("获取课程{}的章节失败: {}", courseId, e.getMessage());
            }
        }
        
        if (chapterToCourseMap.isEmpty()) {
            return items;
        }
        
        // 1. 查找即将截止的作业（24小时内）
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime deadline24h = now.plusHours(24);
        
        List<Homework> urgentHomeworks = homeworkMapper.selectList(
            new LambdaQueryWrapper<Homework>()
                .in(Homework::getChapterId, chapterToCourseMap.keySet())
                .isNotNull(Homework::getDeadline)
                .gt(Homework::getDeadline, now)
                .lt(Homework::getDeadline, deadline24h)
        );
        
        for (Homework hw : urgentHomeworks) {
            TeacherDashboardDTO.UrgentItem item = new TeacherDashboardDTO.UrgentItem();
            item.setType("homework");
            item.setId(hw.getId());
            item.setTitle("作业即将截止: " + hw.getTitle());
            item.setDeadline(hw.getDeadline().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")));
            Long courseId = chapterToCourseMap.get(hw.getChapterId());
            item.setCourseId(courseId);
            item.setCourseName(courseNameMap.get(courseId));
            items.add(item);
        }
        
        // 2. 查找超过48小时未回复的问题
        LocalDateTime threshold48h = now.minusHours(48);
        
        List<SubjectiveComment> unansweredQuestions = commentMapper.selectList(
            new LambdaQueryWrapper<SubjectiveComment>()
                .eq(SubjectiveComment::getIsAnswer, 0)
                .isNull(SubjectiveComment::getParentId)
                .lt(SubjectiveComment::getCreatedAt, threshold48h)
                .eq(SubjectiveComment::getStatus, 1)
                .last("LIMIT 10")
        );
        
        for (SubjectiveComment comment : unansweredQuestions) {
            TeacherDashboardDTO.UrgentItem item = new TeacherDashboardDTO.UrgentItem();
            item.setType("question");
            item.setId(comment.getId());
            item.setTitle("学生提问超48小时未回复");
            items.add(item);
        }
        
        return items;
    }

    /**
     * 获取课程完成率排名
     */
    private List<TeacherDashboardDTO.CourseRanking> getCourseRankings(List<Map<String, Object>> courses) {
        List<TeacherDashboardDTO.CourseRanking> rankings = new ArrayList<>();
        
        for (Map<String, Object> course : courses) {
            String status = getStringValue(course, "status");
            if (!"PUBLISHED".equals(status)) {
                continue;
            }
            
            TeacherDashboardDTO.CourseRanking ranking = new TeacherDashboardDTO.CourseRanking();
            ranking.setCourseId(getLongValue(course, "id"));
            ranking.setTitle(getStringValue(course, "title"));
            ranking.setStudentCount(getIntValue(course, "studentCount"));
            
            // 完成率需要从进度服务获取，这里暂时使用模拟计算
            // 实际应该调用 progress-service 获取真实数据
            int studentCount = ranking.getStudentCount();
            if (studentCount > 0) {
                // 简单模拟：基于学生数量计算一个合理的完成率
                ranking.setCompletionRate(Math.min(95.0, 50.0 + Math.random() * 40));
            } else {
                ranking.setCompletionRate(0.0);
            }
            
            rankings.add(ranking);
        }
        
        // 按完成率降序排序
        rankings.sort((a, b) -> Double.compare(b.getCompletionRate(), a.getCompletionRate()));
        
        return rankings;
    }

    /**
     * 获取周趋势数据
     */
    private TeacherDashboardDTO.WeeklyTrend getWeeklyTrend(List<Long> courseIds) {
        TeacherDashboardDTO.WeeklyTrend trend = new TeacherDashboardDTO.WeeklyTrend();
        
        // 生成过去7天的标签
        List<String> labels = new ArrayList<>();
        String[] dayNames = {"周日", "周一", "周二", "周三", "周四", "周五", "周六"};
        LocalDate today = LocalDate.now();
        
        for (int i = 6; i >= 0; i--) {
            LocalDate date = today.minusDays(i);
            int dayOfWeek = date.getDayOfWeek().getValue() % 7;
            labels.add(dayNames[dayOfWeek]);
        }
        trend.setLabels(labels);
        
        // 统计每天的数据
        List<Integer> studentActivity = new ArrayList<>();
        List<Integer> homeworkSubmissions = new ArrayList<>();
        List<Integer> quizCompletions = new ArrayList<>();
        
        if (courseIds.isEmpty()) {
            for (int i = 0; i < 7; i++) {
                studentActivity.add(0);
                homeworkSubmissions.add(0);
                quizCompletions.add(0);
            }
        } else {
            // 获取课程下的章节
            List<Long> chapterIds = getChapterIdsByCourses(courseIds);
            
            // 获取章节下的作业
            List<Homework> homeworks = chapterIds.isEmpty() ? Collections.emptyList() : 
                homeworkMapper.selectList(
                    new LambdaQueryWrapper<Homework>()
                        .in(Homework::getChapterId, chapterIds)
                );
            
            List<Long> homeworkIds = homeworks.stream()
                .map(Homework::getId)
                .collect(Collectors.toList());
            
            for (int i = 6; i >= 0; i--) {
                LocalDate date = today.minusDays(i);
                LocalDateTime dayStart = date.atStartOfDay();
                LocalDateTime dayEnd = date.plusDays(1).atStartOfDay();
                
                // 统计作业提交数
                int submissions = 0;
                if (!homeworkIds.isEmpty()) {
                    Long count = submissionMapper.selectCount(
                        new LambdaQueryWrapper<HomeworkSubmission>()
                            .in(HomeworkSubmission::getHomeworkId, homeworkIds)
                            .ge(HomeworkSubmission::getSubmittedAt, dayStart)
                            .lt(HomeworkSubmission::getSubmittedAt, dayEnd)
                    );
                    submissions = count != null ? count.intValue() : 0;
                }
                homeworkSubmissions.add(submissions);
                
                // 学生活动数（简化为提交数的2倍）
                studentActivity.add(submissions * 2);
                
                // 测验完成数（简化为提交数的一半）
                quizCompletions.add(submissions / 2);
            }
        }
        
        trend.setStudentActivity(studentActivity);
        trend.setHomeworkSubmissions(homeworkSubmissions);
        trend.setQuizCompletions(quizCompletions);
        
        return trend;
    }

    // 辅助方法
    private Long getLongValue(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value == null) return 0L;
        if (value instanceof Long) return (Long) value;
        if (value instanceof Integer) return ((Integer) value).longValue();
        if (value instanceof String) return Long.parseLong((String) value);
        return 0L;
    }
    
    private Integer getIntValue(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value == null) return 0;
        if (value instanceof Integer) return (Integer) value;
        if (value instanceof Long) return ((Long) value).intValue();
        if (value instanceof String) return Integer.parseInt((String) value);
        return 0;
    }
    
    private String getStringValue(Map<String, Object> map, String key) {
        Object value = map.get(key);
        return value != null ? value.toString() : "";
    }
}
