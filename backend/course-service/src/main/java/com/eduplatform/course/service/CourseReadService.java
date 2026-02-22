package com.eduplatform.course.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.eduplatform.common.result.Result;
import com.eduplatform.course.dto.UserBriefDTO;
import com.eduplatform.course.entity.Chapter;
import com.eduplatform.course.entity.Course;
import com.eduplatform.course.feign.UserServiceClient;
import com.eduplatform.course.mapper.ChapterMapper;
import com.eduplatform.course.mapper.CourseMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 课程读模型服务。
 * 说明：集中承接课程查询、列表装配和统计分析，降低 CourseService 的职责复杂度。
 */
@Service
@RequiredArgsConstructor
public class CourseReadService {

    private final CourseMapper courseMapper;
    private final ChapterMapper chapterMapper;
    private final UserServiceClient userServiceClient;

    /**
     * 填充章节总数字段，避免列表页逐行 count 产生 N+1 查询。
     */
    private void fillChapterCounts(List<Course> courses) {
        if (courses == null || courses.isEmpty()) {
            return;
        }

        List<Long> courseIds = courses.stream()
                .map(Course::getId)
                .filter(Objects::nonNull)
                .toList();
        if (courseIds.isEmpty()) {
            return;
        }

        List<Chapter> chapters = chapterMapper.selectList(
                new LambdaQueryWrapper<Chapter>()
                        .select(Chapter::getCourseId)
                        .in(Chapter::getCourseId, courseIds));

        Map<Long, Long> chapterCountMap = chapters.stream()
                .filter(chapter -> chapter.getCourseId() != null)
                .collect(Collectors.groupingBy(Chapter::getCourseId, Collectors.counting()));

        for (Course course : courses) {
            int count = chapterCountMap.getOrDefault(course.getId(), 0L).intValue();
            course.setTotalChapters(count);
        }
    }

    /**
     * 填充教师姓名，优先批量查询，失败后降级逐条查询。
     */
    private void fillTeacherNames(List<Course> courses) {
        if (courses == null || courses.isEmpty()) {
            return;
        }

        boolean optimizedApplied = false;
        Set<Long> teacherIds = courses.stream()
                .map(Course::getTeacherId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        if (!teacherIds.isEmpty()) {
            try {
                Result<List<UserBriefDTO>> result = userServiceClient.getUsersByIds(new ArrayList<>(teacherIds));
                if (result != null && result.getData() != null && !result.getData().isEmpty()) {
                    Map<Long, String> teacherNameMap = result.getData().stream()
                            .filter(user -> user != null && user.getId() != null)
                            .collect(Collectors.toMap(
                                    UserBriefDTO::getId,
                                    user -> {
                                        String teacherName = user.getName();
                                        if (teacherName == null || teacherName.isBlank()) {
                                            return user.getUsername();
                                        }
                                        return teacherName;
                                    },
                                    (left, right) -> left));

                    for (Course course : courses) {
                        if (course.getTeacherId() == null) {
                            continue;
                        }
                        String teacherName = teacherNameMap.get(course.getTeacherId());
                        if (teacherName != null && !teacherName.isBlank()) {
                            course.setTeacherName(teacherName);
                        }
                    }

                    optimizedApplied = true;
                }
            } catch (Exception ignored) {
                // 批量查询异常时走逐条降级逻辑
            }
        }

        if (optimizedApplied) {
            for (Course course : courses) {
                if (course.getTeacherId() != null
                        && (course.getTeacherName() == null || course.getTeacherName().isBlank())) {
                    course.setTeacherName("未知教师");
                }
            }
            return;
        }

        for (Course course : courses) {
            if (course.getTeacherId() == null) {
                continue;
            }
            try {
                Result<UserBriefDTO> result = userServiceClient.getUserById(course.getTeacherId());
                if (result != null && result.getData() != null) {
                    UserBriefDTO user = result.getData();
                    String teacherName = user.getName();
                    if (teacherName == null || teacherName.isBlank()) {
                        teacherName = user.getUsername();
                    }
                    course.setTeacherName(teacherName);
                }
            } catch (Exception e) {
                if (course.getTeacherName() == null) {
                    course.setTeacherName("未知教师");
                }
            }
        }
    }

    /**
     * 状态归一化，兼容数字状态码与标准状态值。
     */
    private String normalizeStatus(String status) {
        if (status == null) {
            return Course.STATUS_DRAFT;
        }
        return switch (status) {
            case "0" -> Course.STATUS_REVIEWING;
            case "1" -> Course.STATUS_PUBLISHED;
            case "2" -> Course.STATUS_OFFLINE;
            case "DRAFT", "REVIEWING", "PUBLISHED", "OFFLINE", "REJECTED", "BANNED" -> status;
            default -> Course.STATUS_DRAFT;
        };
    }

    /**
     * 管理员/内部多维度课程检索。
     */
    public List<Course> getAllCourses(String subject, String status) {
        LambdaQueryWrapper<Course> wrapper = new LambdaQueryWrapper<>();
        if (subject != null && !subject.isEmpty() && !"all".equals(subject)) {
            wrapper.eq(Course::getSubject, subject);
        }
        if (status != null && !status.isEmpty()) {
            wrapper.eq(Course::getStatus, normalizeStatus(status));
        }
        wrapper.orderByDesc(Course::getCreatedAt);
        List<Course> courses = courseMapper.selectList(wrapper);
        fillChapterCounts(courses);
        fillTeacherNames(courses);
        return courses;
    }

    /**
     * 管理端课程列表（默认排除草稿）。
     */
    public List<Course> getAdminVisibleCourses(String subject, String status) {
        String normalizedStatus = null;
        if (status != null && !status.isEmpty() && !"all".equalsIgnoreCase(status)) {
            normalizedStatus = normalizeStatus(status);
            if (Course.STATUS_DRAFT.equals(normalizedStatus)) {
                return Collections.emptyList();
            }
        }

        LambdaQueryWrapper<Course> wrapper = new LambdaQueryWrapper<>();
        if (subject != null && !subject.isEmpty() && !"all".equals(subject)) {
            wrapper.eq(Course::getSubject, subject);
        }
        if (normalizedStatus != null) {
            wrapper.eq(Course::getStatus, normalizedStatus);
        } else {
            wrapper.ne(Course::getStatus, Course.STATUS_DRAFT);
        }
        wrapper.orderByDesc(Course::getCreatedAt);
        List<Course> courses = courseMapper.selectList(wrapper);
        fillChapterCounts(courses);
        fillTeacherNames(courses);
        return courses;
    }

    /**
     * 教师端课程列表（支持学科/状态筛选）。
     */
    public List<Course> getTeacherCourses(Long teacherId, String subject, String status) {
        LambdaQueryWrapper<Course> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Course::getTeacherId, teacherId);
        if (subject != null && !subject.isEmpty() && !"all".equals(subject)) {
            wrapper.eq(Course::getSubject, subject);
        }
        if (status != null && !status.isEmpty() && !"all".equalsIgnoreCase(status)) {
            wrapper.eq(Course::getStatus, normalizeStatus(status));
        }
        wrapper.orderByDesc(Course::getCreatedAt);
        List<Course> courses = courseMapper.selectList(wrapper);
        fillChapterCounts(courses);
        fillTeacherNames(courses);
        return courses;
    }

    /**
     * 获取教师名下所有课程。
     */
    public List<Course> getTeacherCourses(Long teacherId) {
        return getTeacherCourses(teacherId, null, null);
    }

    /**
     * 学生端已发布课程列表（带缓存）。
     */
    @org.springframework.cache.annotation.Cacheable(value = "course_list", key = "'published:subject:' + (#subject == null ? 'all' : #subject)")
    public List<Course> getPublishedCourses(String subject) {
        LambdaQueryWrapper<Course> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Course::getStatus, Course.STATUS_PUBLISHED);
        if (subject != null && !subject.isEmpty() && !"all".equals(subject)) {
            wrapper.eq(Course::getSubject, subject);
        }
        wrapper.orderByDesc(Course::getCreatedAt);
        List<Course> courses = courseMapper.selectList(wrapper);
        fillChapterCounts(courses);
        fillTeacherNames(courses);
        return courses;
    }

    /**
     * 按 ID 查询课程详情。
     */
    public Course getById(Long id) {
        Course course = courseMapper.selectById(id);
        if (course != null) {
            fillTeacherNames(Collections.singletonList(course));
        }
        return course;
    }

    /**
     * 获取待审核课程列表。
     */
    public List<Course> getReviewingCourses() {
        List<Course> courses = courseMapper.selectList(
                new LambdaQueryWrapper<Course>()
                        .eq(Course::getStatus, Course.STATUS_REVIEWING)
                        .orderByAsc(Course::getSubmitTime));
        fillChapterCounts(courses);
        return courses;
    }

    /**
     * 按状态统计课程数量。
     */
    public long countByStatus(String status) {
        LambdaQueryWrapper<Course> wrapper = new LambdaQueryWrapper<>();
        if (status != null && !status.isEmpty()) {
            wrapper.eq(Course::getStatus, status);
        }
        return courseMapper.selectCount(wrapper);
    }

    /**
     * 按学科统计已发布课程数量。
     */
    public long countBySubject(String subject) {
        return courseMapper.selectCount(
                new LambdaQueryWrapper<Course>()
                        .eq(Course::getSubject, subject)
                        .eq(Course::getStatus, Course.STATUS_PUBLISHED));
    }

    /**
     * 获取课程看板统计（带缓存）。
     */
    @org.springframework.cache.annotation.Cacheable(value = "course_stats", key = "'dashboard'")
    public Map<String, Object> getCourseStatistics() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("total", countByStatus(null));
        stats.put("draft", countByStatus(Course.STATUS_DRAFT));
        stats.put("reviewing", countByStatus(Course.STATUS_REVIEWING));
        stats.put("published", countByStatus(Course.STATUS_PUBLISHED));
        stats.put("offline", countByStatus(Course.STATUS_OFFLINE));

        Map<String, Long> subjectStats = new HashMap<>();
        String[] subjects = {"语文", "数学", "英语", "物理", "化学", "生物", "政治", "历史", "地理"};
        for (String subject : subjects) {
            subjectStats.put(subject, countBySubject(subject));
        }
        stats.put("subjectStats", subjectStats);

        return stats;
    }

    /**
     * 获取按学科分组的课程分布统计。
     */
    public Map<String, Object> getCourseStatsBySubject() {
        Map<String, Object> result = new HashMap<>();
        List<String> subjects = new ArrayList<>();
        List<Long> courseCounts = new ArrayList<>();
        List<Long> studentCounts = new ArrayList<>();

        String[] allSubjects = {"语文", "数学", "英语", "物理", "化学", "生物", "政治", "历史", "地理"};

        for (String subject : allSubjects) {
            long courseCount = courseMapper.selectCount(
                    new LambdaQueryWrapper<Course>()
                            .eq(Course::getSubject, subject)
                            .eq(Course::getStatus, Course.STATUS_PUBLISHED));

            if (courseCount > 0) {
                subjects.add(subject);
                courseCounts.add(courseCount);

                List<Course> subjectCourses = courseMapper.selectList(
                        new LambdaQueryWrapper<Course>()
                                .eq(Course::getSubject, subject)
                                .eq(Course::getStatus, Course.STATUS_PUBLISHED));
                long totalStudents = subjectCourses.stream()
                        .mapToLong(c -> c.getStudentCount() != null ? c.getStudentCount() : 0)
                        .sum();
                studentCounts.add(totalStudents);
            }
        }

        result.put("subjects", subjects);
        result.put("courseCounts", courseCounts);
        result.put("studentCounts", studentCounts);
        return result;
    }
}
