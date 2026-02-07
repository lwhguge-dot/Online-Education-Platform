package com.eduplatform.course.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.eduplatform.common.event.EventType;
import com.eduplatform.common.event.RedisStreamConstants;
import com.eduplatform.common.event.RedisStreamPublisher;
import com.eduplatform.common.result.Result;
import com.eduplatform.course.config.LearningStatusConfig;
import com.eduplatform.course.dto.UserBriefDTO;
import com.eduplatform.course.entity.Chapter;
import com.eduplatform.course.entity.Course;
import com.eduplatform.course.entity.Enrollment;
import com.eduplatform.course.feign.UserServiceClient;
import com.eduplatform.course.mapper.ChapterMapper;
import com.eduplatform.course.mapper.CourseMapper;
import com.eduplatform.course.mapper.EnrollmentMapper;
import com.eduplatform.course.vo.EnrollmentVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 选课与报名核心服务
 * 负责维护学生与课程之间的契约关系，涵盖从选课报名、主动退课、到学习进度跟踪的全生命周期管理。
 *
 * 核心逻辑：
 * 1. 事务性报名：确保选课记录增加与课程热度（学生数）统计在同一事务内完成，保证数据一致性。
 * 2. 状态驱动可见性：通过 DROPPED 状态实现软删除逻辑，确保历史选课痕迹的可追溯性。
 * 3. 学习进度感知：作为 Progress-service 的核心上游，记录最后一次学习时间并实时驱动 Enrollment 状态由 ACTIVE 向
 * COMPLETED 跃迁。
 *
 * @author Antigravity
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EnrollmentService {

    private final EnrollmentMapper enrollmentMapper;
    private final CourseMapper courseMapper;
    private final ChapterMapper chapterMapper;
    private final UserServiceClient userServiceClient;
    private final LearningStatusConfig learningStatusConfig;
    private final RedisStreamPublisher redisStreamPublisher;

    /**
     * 将报名持久层实体转换为视图对象 (VO)
     * 用于前端展现用户的选课详情与进度百分比。
     *
     * @param entity 报名记录实体
     * @return 准备好传输的 EnrollmentVO
     */
    public EnrollmentVO convertToVO(Enrollment entity) {
        if (entity == null) {
            return null;
        }
        EnrollmentVO vo = new EnrollmentVO();
        BeanUtils.copyProperties(entity, vo);
        return vo;
    }

    /**
     * 批量持久层对象转换为视图列表
     * 
     * @param entities 实体列表
     * @return 视图对象列表
     */
    public List<EnrollmentVO> convertToVOList(List<Enrollment> entities) {
        if (entities == null || entities.isEmpty()) {
            return Collections.emptyList();
        }
        return entities.stream()
                .map(this::convertToVO)
                .collect(Collectors.toList());
    }

    /**
     * 判断学生是否已订阅某门课程
     * 逻辑包含：存在且未处于“已退课”状态。
     * 
     * @param studentId 学生 ID
     * @param courseId  课程 ID
     * @return 是否拥有有效订阅
     */
    public boolean isEnrolled(Long studentId, Long courseId) {
        Long count = enrollmentMapper.selectCount(
                new LambdaQueryWrapper<Enrollment>()
                        .eq(Enrollment::getStudentId, studentId)
                        .eq(Enrollment::getCourseId, courseId)
                        .ne(Enrollment::getStatus, Enrollment.STATUS_DROPPED));
        return count != null && count > 0;
    }

    /**
     * 开启一段学习旅程 (选课报名)
     * 操作流程：验证课程发布状态 -> 幂等性校验 -> 初始化报名记录(0进度/活跃态) -> 课程报名数自增。
     * 
     * @param studentId 发起选课的学生 ID
     * @param courseId  目标课程 ID
     * @return 初始化的报名对象
     */
    @Transactional
    public Enrollment enroll(Long studentId, Long courseId) {
        Course course = courseMapper.selectById(courseId);
        if (course == null) {
            throw new RuntimeException("操作失败：目标课程不存在");
        }
        if (!Course.STATUS_PUBLISHED.equals(course.getStatus())) {
            throw new RuntimeException("由于课程当前未处于发布状态，暂时无法接受报名");
        }

        if (isEnrolled(studentId, courseId)) {
            throw new RuntimeException("您已参与该课程的学习，请勿重复报名");
        }

        Enrollment enrollment = new Enrollment();
        enrollment.setStudentId(studentId);
        enrollment.setCourseId(courseId);
        enrollment.setEnrolledAt(LocalDateTime.now());
        enrollment.setProgress(0);
        enrollment.setStatus(Enrollment.STATUS_ACTIVE);
        enrollment.setCreatedAt(LocalDateTime.now());
        enrollment.setUpdatedAt(LocalDateTime.now());

        enrollmentMapper.insert(enrollment);

        // 热度数据聚合：同步增加课程在学人数
        course.setStudentCount(course.getStudentCount() + 1);
        course.setUpdatedAt(LocalDateTime.now());
        courseMapper.updateById(course);

        // 发布选课事件，由 user-service 异步消费发送通知
        publishEnrollmentEvent(EventType.COURSE_ENROLLED, studentId, courseId, course.getTitle());

        return enrollment;
    }

    /**
     * 主动中止学习旅程 (退课)
     * 核心规则：仅将记录标记为 DROPPED (软删除)，同时同步扣减课程的实时在学人数。
     */
    @Transactional
    public void drop(Long studentId, Long courseId) {
        Enrollment enrollment = enrollmentMapper.selectOne(
                new LambdaQueryWrapper<Enrollment>()
                        .eq(Enrollment::getStudentId, studentId)
                        .eq(Enrollment::getCourseId, courseId)
                        .ne(Enrollment::getStatus, Enrollment.STATUS_DROPPED));

        if (enrollment == null) {
            throw new RuntimeException("未匹配到有效的报名记录，无法执行退课");
        }

        enrollment.setStatus(Enrollment.STATUS_DROPPED);
        enrollment.setUpdatedAt(LocalDateTime.now());
        enrollmentMapper.updateById(enrollment);

        Course course = courseMapper.selectById(courseId);
        if (course != null && course.getStudentCount() > 0) {
            course.setStudentCount(course.getStudentCount() - 1);
            course.setUpdatedAt(LocalDateTime.now());
            courseMapper.updateById(course);
        }

        // 发布退课事件，由 user-service 异步消费发送通知
        String courseName = course != null ? course.getTitle() : null;
        publishEnrollmentEvent(EventType.COURSE_DROPPED, studentId, courseId, courseName);
    }

    /**
     * 获取学生名下所有的活跃订阅课程
     */
    public List<Enrollment> getStudentEnrollments(Long studentId) {
        return enrollmentMapper.selectList(
                new LambdaQueryWrapper<Enrollment>()
                        .eq(Enrollment::getStudentId, studentId)
                        .ne(Enrollment::getStatus, Enrollment.STATUS_DROPPED)
                        .orderByDesc(Enrollment::getEnrolledAt));
    }

    /**
     * 获取某门课程当前所有的学生参与记录
     */
    public List<Enrollment> getCourseEnrollments(Long courseId) {
        return enrollmentMapper.selectList(
                new LambdaQueryWrapper<Enrollment>()
                        .eq(Enrollment::getCourseId, courseId)
                        .ne(Enrollment::getStatus, Enrollment.STATUS_DROPPED)
                        .orderByDesc(Enrollment::getEnrolledAt));
    }

    /**
     * 精确检索单一报名契约记录
     */
    public Enrollment getEnrollment(Long studentId, Long courseId) {
        return enrollmentMapper.selectOne(
                new LambdaQueryWrapper<Enrollment>()
                        .eq(Enrollment::getStudentId, studentId)
                        .eq(Enrollment::getCourseId, courseId)
                        .ne(Enrollment::getStatus, Enrollment.STATUS_DROPPED));
    }

    /**
     * 同步学习进度与结课逻辑
     * 业务操作：由 Progress-service 触发，更新当前百分比并记录最后活跃时间。
     * 触发阈值：当 progress 达到 100 时，系统自动将契约状态置为 COMPLETED。
     *
     * @param studentId 学生 ID
     * @param courseId  课程 ID
     * @param progress  当前总进度百分比 (0-100)
     */
    public void updateProgress(Long studentId, Long courseId, Integer progress) {
        Enrollment enrollment = getEnrollment(studentId, courseId);
        if (enrollment == null) {
            throw new RuntimeException("操作异常：未找到对应的报名记录");
        }

        enrollment.setProgress(progress);
        enrollment.setLastStudyAt(LocalDateTime.now());
        enrollment.setUpdatedAt(LocalDateTime.now());

        // 自动化状态跃迁：满额即视为结课
        if (progress >= 100) {
            enrollment.setStatus(Enrollment.STATUS_COMPLETED);
        }

        enrollmentMapper.updateById(enrollment);
    }

    /**
     * 维护学生维度的有效选课基数 (不计入已退课)
     */
    public long countByStudent(Long studentId) {
        return enrollmentMapper.selectCount(
                new LambdaQueryWrapper<Enrollment>()
                        .eq(Enrollment::getStudentId, studentId)
                        .ne(Enrollment::getStatus, Enrollment.STATUS_DROPPED));
    }

    /**
     * 维护课程维度的实时在学学生总数
     */
    public long countByCourse(Long courseId) {
        return enrollmentMapper.selectCount(
                new LambdaQueryWrapper<Enrollment>()
                        .eq(Enrollment::getCourseId, courseId)
                        .ne(Enrollment::getStatus, Enrollment.STATUS_DROPPED));
    }

    /**
     * 智能探测课程内容更新情况
     * 核心规则：比较“用户最后一次学习/报名时间”与“最新章节创建时间”的压差。
     * 常用于前端“有新章节”的小红点提示。
     *
     * @param studentId 目标学生
     * @param courseId  目标课程
     * @return 包含 hasNewChapters (布尔) 与 newChaptersCount (计数值) 的分析结果包
     */
    public Map<String, Object> checkNewChapters(Long studentId, Long courseId) {
        Map<String, Object> result = new HashMap<>();
        result.put("hasNewChapters", false);
        result.put("newChaptersCount", 0);

        Enrollment enrollment = getEnrollment(studentId, courseId);
        if (enrollment == null) {
            return result;
        }

        // 基准线选择：优先使用最后学习时间，兜底使用报名时间
        LocalDateTime lastAccessTime = enrollment.getLastStudyAt();
        if (lastAccessTime == null) {
            lastAccessTime = enrollment.getEnrolledAt();
        }

        if (lastAccessTime == null) {
            return result;
        }

        // 执行增量扫描
        List<Chapter> newChapters = chapterMapper.selectList(
                new LambdaQueryWrapper<Chapter>()
                        .eq(Chapter::getCourseId, courseId)
                        .gt(Chapter::getCreatedAt, lastAccessTime));

        if (newChapters != null && !newChapters.isEmpty()) {
            result.put("hasNewChapters", true);
            result.put("newChaptersCount", newChapters.size());
        }

        return result;
    }

    /**
     * 获取带有内容更新标识的学生选课清单
     * 场景：学生个人中心“我的课程”列表，直观展示哪些课程有新内容。
     */
    public List<Map<String, Object>> getStudentEnrollmentsWithNewChapters(Long studentId) {
        List<Enrollment> enrollments = getStudentEnrollments(studentId);
        return enrollments.stream().map(enrollment -> {
            Map<String, Object> data = new HashMap<>();
            data.put("enrollment", enrollment);

            // 联动检查：检测当前课程相对于用户进度的更新
            Map<String, Object> newChapterInfo = checkNewChapters(studentId, enrollment.getCourseId());
            data.put("hasNewChapters", newChapterInfo.get("hasNewChapters"));
            data.put("newChaptersCount", newChapterInfo.get("newChaptersCount"));

            return data;
        }).toList();
    }

    /**
     * 单门课程今日增长情况
     */
    public int countTodayEnrollments(Long courseId) {
        LocalDateTime todayStart = LocalDateTime.now().toLocalDate().atStartOfDay();
        Long count = enrollmentMapper.selectCount(
                new LambdaQueryWrapper<Enrollment>()
                        .eq(Enrollment::getCourseId, courseId)
                        .ge(Enrollment::getEnrolledAt, todayStart)
                        .ne(Enrollment::getStatus, Enrollment.STATUS_DROPPED));
        return count != null ? count.intValue() : 0;
    }

    /**
     * 教师名下全量课程的今日增量统计
     * 业务场景：教师工作台首页统计。
     * 流程：获取教师所有课程 ID 列表 -> 执行 IN 查询完成日增长计数。
     */
    public int countTeacherTodayEnrollments(Long teacherId) {
        List<Course> courses = courseMapper.selectList(
                new LambdaQueryWrapper<Course>()
                        .eq(Course::getTeacherId, teacherId));

        if (courses.isEmpty()) {
            return 0;
        }

        List<Long> courseIds = courses.stream()
                .map(Course::getId)
                .toList();

        LocalDateTime todayStart = LocalDateTime.now().toLocalDate().atStartOfDay();
        Long count = enrollmentMapper.selectCount(
                new LambdaQueryWrapper<Enrollment>()
                        .in(Enrollment::getCourseId, courseIds)
                        .ge(Enrollment::getEnrolledAt, todayStart)
                        .ne(Enrollment::getStatus, Enrollment.STATUS_DROPPED));
        return count != null ? count.intValue() : 0;
    }

    /**
     * 教师全量学生全景透视 (跨课程聚合)
     * 核心规则：按学生 ID 强聚合，将该学生在当前教师名下跨多门课程的行为（进度、活跃时间）进行归约。
     * 
     * @param teacherId 教师 ID
     * @param page      页码
     * @param size      页长
     * @return 包含聚合后的学生属性、课程集锦、平均进度及分页元数据的 Map 结果集
     */
    public Map<String, Object> getTeacherStudents(Long teacherId, int page, int size) {
        Map<String, Object> result = new HashMap<>();

        // 1. 获取业务边界：该教师管理的所有课程
        List<Course> courses = courseMapper.selectList(
                new LambdaQueryWrapper<Course>()
                        .eq(Course::getTeacherId, teacherId));

        if (courses.isEmpty()) {
            result.put("students", List.of());
            result.put("total", 0);
            result.put("page", page);
            result.put("size", size);
            return result;
        }

        List<Long> courseIds = courses.stream()
                .map(Course::getId)
                .toList();

        Map<Long, String> courseNameMap = new HashMap<>();
        for (Course course : courses) {
            courseNameMap.put(course.getId(), course.getTitle());
        }

        // 2. 加载原始流量数据：获取所有相关联的有效报名契约
        List<Enrollment> allEnrollments = enrollmentMapper.selectList(
                new LambdaQueryWrapper<Enrollment>()
                        .in(Enrollment::getCourseId, courseIds)
                        .ne(Enrollment::getStatus, Enrollment.STATUS_DROPPED)
                        .orderByDesc(Enrollment::getEnrolledAt));

        // 3. 内存聚合排序：多对多关系降维
        Map<Long, Map<String, Object>> studentMap = new HashMap<>();
        for (Enrollment enrollment : allEnrollments) {
            Long studentId = enrollment.getStudentId();

            if (!studentMap.containsKey(studentId)) {
                Map<String, Object> studentData = new HashMap<>();
                studentData.put("id", studentId);
                studentData.put("name", "学生" + studentId);
                studentData.put("courses", new java.util.ArrayList<String>());
                studentData.put("progress", 0);
                studentData.put("lastActive", "未知");
                // 辅助统计字段 (后续清理)
                studentData.put("enrollmentCount", 0);
                studentData.put("totalProgress", 0);
                studentMap.put(studentId, studentData);
            }

            Map<String, Object> studentData = studentMap.get(studentId);

            // 课程集锦构建
            @SuppressWarnings("unchecked")
            List<String> courseList = (List<String>) studentData.get("courses");
            String courseName = courseNameMap.get(enrollment.getCourseId());
            if (courseName != null && !courseList.contains(courseName)) {
                courseList.add(courseName);
            }

            // 权重归约：计算该学生所有在读课程的平均进度
            int enrollmentCount = (int) studentData.get("enrollmentCount") + 1;
            int totalProgress = (int) studentData.get("totalProgress")
                    + (enrollment.getProgress() != null ? enrollment.getProgress() : 0);
            studentData.put("enrollmentCount", enrollmentCount);
            studentData.put("totalProgress", totalProgress);
            studentData.put("progress", totalProgress / enrollmentCount);

            // 活跃度探针：获取所有课程中最晚的一条学习足迹
            if (enrollment.getLastStudyAt() != null) {
                String lastActive = enrollment.getLastStudyAt()
                        .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));
                String currentLastActive = (String) studentData.get("lastActive");
                if ("未知".equals(currentLastActive) || lastActive.compareTo(currentLastActive) > 0) {
                    studentData.put("lastActive", lastActive);
                }
            }
        }

        // 4. 分页列表排序
        List<Map<String, Object>> studentList = new java.util.ArrayList<>(studentMap.values());

        // 按近度排序：最近活跃的学生排在最前方
        studentList.sort((a, b) -> {
            String lastActiveA = (String) a.get("lastActive");
            String lastActiveB = (String) b.get("lastActive");
            if ("未知".equals(lastActiveA))
                return 1;
            if ("未知".equals(lastActiveB))
                return -1;
            return lastActiveB.compareTo(lastActiveA);
        });

        int total = studentList.size();
        int start = (page - 1) * size;
        int end = Math.min(start + size, total);

        List<Map<String, Object>> pagedStudents = start < total ? studentList.subList(start, end) : List.of();

        // 5. 结果标准化：移除内存中间状态属性
        for (Map<String, Object> student : pagedStudents) {
            student.remove("enrollmentCount");
            student.remove("totalProgress");
        }

        result.put("students", pagedStudents);
        result.put("total", total);
        result.put("page", page);
        result.put("size", size);
        result.put("pages", (int) Math.ceil((double) total / size));

        return result;
    }

    /**
     * 获取教师全量课程的学生分布概览
     * 业务逻辑：遍历教师名下所有课程 -> 统计各课程学生基数 -> 动态评估处于“风险”及“静默”状态的学生占比。
     * 常用于教师端首页的“学情仪表盘”。
     */
    public Map<String, Object> getTeacherStudentsOverview(Long teacherId) {
        Map<String, Object> result = new HashMap<>();

        // 获取该教师全量课程上下文
        List<Course> courses = courseMapper.selectList(
                new LambdaQueryWrapper<Course>()
                        .eq(Course::getTeacherId, teacherId));

        if (courses.isEmpty()) {
            result.put("courses", List.of());
            result.put("totalStudents", 0);
            result.put("totalAtRisk", 0);
            result.put("totalInactive", 0);
            return result;
        }

        List<Map<String, Object>> courseOverviews = new java.util.ArrayList<>();
        int totalStudents = 0;
        int totalAtRisk = 0;
        int totalInactive = 0;

        for (Course course : courses) {
            List<Enrollment> enrollments = enrollmentMapper.selectList(
                    new LambdaQueryWrapper<Enrollment>()
                            .eq(Enrollment::getCourseId, course.getId())
                            .ne(Enrollment::getStatus, Enrollment.STATUS_DROPPED));

            int courseStudents = enrollments.size();
            int atRiskCount = 0;
            int inactiveCount = 0;

            LocalDateTime now = LocalDateTime.now();
            LocalDateTime inactiveThreshold = now.minusDays(learningStatusConfig.getInactiveDays());

            for (Enrollment enrollment : enrollments) {
                String status = calculateLearningStatus(enrollment, inactiveThreshold);
                if ("at-risk".equals(status)) {
                    atRiskCount++;
                } else if ("inactive".equals(status)) {
                    inactiveCount++;
                }
            }

            Map<String, Object> courseOverview = new HashMap<>();
            courseOverview.put("courseId", course.getId());
            courseOverview.put("courseTitle", course.getTitle());
            courseOverview.put("totalStudents", courseStudents);
            courseOverview.put("atRiskCount", atRiskCount);
            courseOverview.put("inactiveCount", inactiveCount);
            courseOverviews.add(courseOverview);

            totalStudents += courseStudents;
            totalAtRisk += atRiskCount;
            totalInactive += inactiveCount;
        }

        result.put("courses", courseOverviews);
        result.put("totalStudents", totalStudents);
        result.put("totalAtRisk", totalAtRisk);
        result.put("totalInactive", totalInactive);

        return result;
    }

    /**
     * 精确获取课程维度的学情关联列表 (含预警标识)
     * 
     * @param courseId     课程 ID
     * @param page         页码
     * @param size         页长
     * @param statusFilter 状态过滤器 (all/excellent/good/at-risk/inactive)
     * @return 包含课程快照、学情汇总及带有 Alert 标签的分页学生列表
     */
    public Map<String, Object> getCourseStudentsWithStatus(Long courseId, int page, int size, String statusFilter) {
        Map<String, Object> result = new HashMap<>();

        Course course = courseMapper.selectById(courseId);
        if (course == null) {
            result.put("course", null);
            result.put("students", List.of());
            result.put("summary", Map.of("total", 0, "excellent", 0, "good", 0, "atRisk", 0, "inactive", 0));
            return result;
        }

        Map<String, Object> courseInfo = new HashMap<>();
        courseInfo.put("id", course.getId());
        courseInfo.put("title", course.getTitle());
        result.put("course", courseInfo);

        // 检索课程当前全量活跃学员
        List<Enrollment> enrollments = enrollmentMapper.selectList(
                new LambdaQueryWrapper<Enrollment>()
                        .eq(Enrollment::getCourseId, courseId)
                        .ne(Enrollment::getStatus, Enrollment.STATUS_DROPPED)
                        .orderByDesc(Enrollment::getLastStudyAt));

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime inactiveThreshold = now.minusDays(learningStatusConfig.getInactiveDays());

        int excellent = 0, good = 0, atRisk = 0, inactive = 0;
        List<Map<String, Object>> studentList = new java.util.ArrayList<>();

        for (Enrollment enrollment : enrollments) {
            // 执行内置学情预判模型
            String learningStatus = calculateLearningStatus(enrollment, inactiveThreshold);

            // 分维度计数聚合
            switch (learningStatus) {
                case "excellent" -> excellent++;
                case "good" -> good++;
                case "at-risk" -> atRisk++;
                case "inactive" -> inactive++;
            }

            // 执行过滤协议
            if (!"all".equals(statusFilter) && !statusFilter.equals(learningStatus)) {
                continue;
            }

            Map<String, Object> studentData = new HashMap<>();
            studentData.put("id", enrollment.getStudentId());
            studentData.put("name", "学生" + enrollment.getStudentId());
            studentData.put("enrolledAt", enrollment.getEnrolledAt() != null ? enrollment.getEnrolledAt()
                    .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")) : null);
            studentData.put("courseProgress", enrollment.getProgress() != null ? enrollment.getProgress() : 0);
            studentData.put("lastActiveAt", enrollment.getLastStudyAt() != null ? enrollment.getLastStudyAt()
                    .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")) : "未知");
            studentData.put("learningStatus", learningStatus);
            // 绑定异常预警文案
            studentData.put("alerts", getStudentAlerts(enrollment, inactiveThreshold));

            studentList.add(studentData);
        }

        // 分页裁剪处理
        int total = studentList.size();
        int start = (page - 1) * size;
        int end = Math.min(start + size, total);
        List<Map<String, Object>> pagedStudents = start < total ? studentList.subList(start, end) : List.of();

        result.put("students", pagedStudents);
        result.put("summary", Map.of(
                "total", enrollments.size(),
                "excellent", excellent,
                "good", good,
                "atRisk", atRisk,
                "inactive", inactive));
        result.put("pagination", Map.of(
                "page", page,
                "size", size,
                "total", total));

        return result;
    }

    /**
     * 学情自动化评估算法（综合评分机制）
     *
     * 评估流程：
     * 1. 强制标记：超过配置的 inactiveDays 天未学习的学生直接标记为 inactive
     * 2. 综合评分：活跃度得分 × activityWeight + 进度得分 × progressWeight + 测验得分 × quizWeight
     * 3. 分类阈值：
     *    - 综合得分 >= excellentThreshold → excellent
     *    - 综合得分 >= goodThreshold → good
     *    - 综合得分 < atRiskThreshold → at-risk
     *    - 其他 → good
     *
     * 活跃度得分计算：当天学习=100分，每增加一天未学习扣除 activityDecayPerDay 分，最低0分
     *
     * @param enrollment         报名记录
     * @param inactiveThreshold  静默期阈值时间点
     * @return 学情状态标识
     */
    private String calculateLearningStatus(Enrollment enrollment, LocalDateTime inactiveThreshold) {
        // 第一优先级：强制标记静默状态（超过阈值天数未学习）
        if (enrollment.getLastStudyAt() == null || enrollment.getLastStudyAt().isBefore(inactiveThreshold)) {
            return "inactive";
        }

        // 计算活跃度得分
        int activityScore = calculateActivityScore(enrollment.getLastStudyAt());

        // 获取学习进度得分（0-100）
        int progressScore = enrollment.getProgress() != null ? enrollment.getProgress() : 0;

        // 测验成绩暂使用默认值（后续可扩展 Feign 调用 progress-service 获取真实成绩）
        int quizScore = learningStatusConfig.getDefaultQuizScore();

        // 计算综合得分
        double compositeScore = activityScore * learningStatusConfig.getActivityWeight()
                + progressScore * learningStatusConfig.getProgressWeight()
                + quizScore * learningStatusConfig.getQuizWeight();

        // 根据阈值分类
        if (compositeScore >= learningStatusConfig.getExcellentThreshold()) {
            return "excellent";
        } else if (compositeScore >= learningStatusConfig.getGoodThreshold()) {
            return "good";
        } else if (compositeScore < learningStatusConfig.getAtRiskThreshold()) {
            return "at-risk";
        }

        return "good";
    }

    /**
     * 计算活跃度得分。
     * 当天学习=100分，每增加一天未学习扣除配置的 activityDecayPerDay 分，最低0分。
     *
     * @param lastStudyAt 最后学习时间
     * @return 活跃度得分（0-100）
     */
    private int calculateActivityScore(LocalDateTime lastStudyAt) {
        if (lastStudyAt == null) {
            return 0;
        }

        long daysSinceLastStudy = ChronoUnit.DAYS.between(lastStudyAt.toLocalDate(), LocalDateTime.now().toLocalDate());

        // 当天学习得满分
        if (daysSinceLastStudy <= 0) {
            return 100;
        }

        // 每天衰减
        int score = 100 - (int) (daysSinceLastStudy * learningStatusConfig.getActivityDecayPerDay());
        return Math.max(0, score);
    }

    /**
     * 精确生成的学生异常预警说明标签
     */
    private List<String> getStudentAlerts(Enrollment enrollment, LocalDateTime inactiveThreshold) {
        List<String> alerts = new java.util.ArrayList<>();

        // 规则 1：静默预警
        if (enrollment.getLastStudyAt() == null || enrollment.getLastStudyAt().isBefore(inactiveThreshold)) {
            alerts.add(learningStatusConfig.getInactiveDays() + "天未登录");
        }

        // 规则 2：低效能预警
        int progress = enrollment.getProgress() != null ? enrollment.getProgress() : 0;
        if (progress < learningStatusConfig.getAtRiskThreshold()) {
            alerts.add("学习进度低于" + learningStatusConfig.getAtRiskThreshold() + "%");
        }

        return alerts;
    }

    /**
     * 跨系统数据导出聚合 (CSV/Excel 适配)
     * 将包含学生真实信息、课程元数据及分析状态的记录压平导出。
     *
     * 优化策略：
     * 1. 第一轮遍历：收集所有不重复的 studentId
     * 2. 批量 Feign 调用 user-service 获取用户信息 → 构建 Map<Long, UserBriefDTO>
     * 3. 第二轮遍历：构建导出数据时从 Map 取真实信息，降级时显示"未知用户"
     *
     * @param teacherId 教师ID
     * @param courseId  课程ID（可选，为空则导出该教师所有课程的学生）
     * @return 导出数据列表
     */
    public List<Map<String, Object>> getTeacherStudentsForExport(Long teacherId, Long courseId) {
        List<Map<String, Object>> result = new java.util.ArrayList<>();

        // 1. 范围界定：获取要导出的课程列表
        List<Course> courses;
        if (courseId != null) {
            Course course = courseMapper.selectById(courseId);
            if (course == null || !course.getTeacherId().equals(teacherId)) {
                return result;
            }
            courses = List.of(course);
        } else {
            courses = courseMapper.selectList(
                    new LambdaQueryWrapper<Course>()
                            .eq(Course::getTeacherId, teacherId));
        }

        if (courses.isEmpty()) {
            return result;
        }

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime inactiveThreshold = now.minusDays(learningStatusConfig.getInactiveDays());

        // 2. 第一轮遍历：收集所有 studentId 并获取报名记录
        Set<Long> studentIdSet = new HashSet<>();
        Map<Long, List<Enrollment>> courseEnrollmentsMap = new HashMap<>();

        for (Course course : courses) {
            List<Enrollment> enrollments = enrollmentMapper.selectList(
                    new LambdaQueryWrapper<Enrollment>()
                            .eq(Enrollment::getCourseId, course.getId())
                            .ne(Enrollment::getStatus, Enrollment.STATUS_DROPPED));

            courseEnrollmentsMap.put(course.getId(), enrollments);

            for (Enrollment enrollment : enrollments) {
                studentIdSet.add(enrollment.getStudentId());
            }
        }

        // 3. 批量 Feign 调用获取用户信息
        Map<Long, UserBriefDTO> userMap = batchFetchUserInfo(new ArrayList<>(studentIdSet));

        // 4. 第二轮遍历：构建导出数据
        for (Course course : courses) {
            List<Enrollment> enrollments = courseEnrollmentsMap.get(course.getId());

            for (Enrollment enrollment : enrollments) {
                Map<String, Object> studentData = new HashMap<>();
                studentData.put("studentId", enrollment.getStudentId());

                // 从 userMap 获取真实用户信息，降级时显示占位信息
                UserBriefDTO userInfo = userMap.get(enrollment.getStudentId());
                if (userInfo != null) {
                    studentData.put("name", userInfo.getName() != null ? userInfo.getName() : userInfo.getUsername());
                    studentData.put("email", userInfo.getEmail() != null ? userInfo.getEmail() : "");
                } else {
                    studentData.put("name", "未知用户");
                    studentData.put("email", "");
                }

                studentData.put("courseName", course.getTitle());
                studentData.put("enrolledAt", enrollment.getEnrolledAt() != null ? enrollment.getEnrolledAt()
                        .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")) : "");
                studentData.put("progress", enrollment.getProgress() != null ? enrollment.getProgress() : 0);
                studentData.put("learningStatus", calculateLearningStatus(enrollment, inactiveThreshold));
                studentData.put("lastStudyTime", enrollment.getLastStudyAt() != null ? enrollment.getLastStudyAt()
                        .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")) : "");

                result.add(studentData);
            }
        }

        return result;
    }

    /**
     * 批量获取用户信息并构建 ID -> 用户信息 的映射。
     * 当 Feign 调用失败时返回空 Map，调用方需处理降级逻辑。
     *
     * @param studentIds 学生ID列表
     * @return 用户ID到用户信息的映射
     */
    private Map<Long, UserBriefDTO> batchFetchUserInfo(List<Long> studentIds) {
        if (studentIds == null || studentIds.isEmpty()) {
            return Collections.emptyMap();
        }

        try {
            Result<List<UserBriefDTO>> feignResult = userServiceClient.getUsersByIds(studentIds);
            if (feignResult != null && feignResult.getData() != null) {
                return feignResult.getData().stream()
                        .filter(user -> user != null && user.getId() != null)
                        .collect(Collectors.toMap(UserBriefDTO::getId, user -> user, (a, b) -> a));
            }
        } catch (Exception e) {
            log.warn("批量获取用户信息失败，将使用降级数据: {}", e.getMessage());
        }

        return Collections.emptyMap();
    }

    /**
     * 发布选课/退课事件到 Redis Stream
     *
     * @param eventType  事件类型（COURSE_ENROLLED / COURSE_DROPPED）
     * @param studentId  学生ID
     * @param courseId   课程ID
     * @param courseName 课程名称
     */
    private void publishEnrollmentEvent(EventType eventType, Long studentId, Long courseId, String courseName) {
        try {
            Map<String, Object> data = new HashMap<>();
            data.put("studentId", studentId);
            data.put("courseId", courseId);
            data.put("courseName", courseName);

            redisStreamPublisher.publish(eventType, RedisStreamConstants.SERVICE_COURSE, data);
        } catch (Exception e) {
            log.error("发布选课事件失败: type={}, studentId={}, courseId={}, error={}",
                    eventType, studentId, courseId, e.getMessage());
        }
    }
}
