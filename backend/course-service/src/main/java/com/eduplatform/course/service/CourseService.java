package com.eduplatform.course.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.eduplatform.common.result.Result;
import com.eduplatform.course.dto.UserBriefDTO;
import com.eduplatform.course.entity.Chapter;
import com.eduplatform.course.entity.Course;
import com.eduplatform.course.feign.AuditLogClient;
import com.eduplatform.course.feign.UserServiceClient;
import com.eduplatform.course.mapper.ChapterMapper;
import com.eduplatform.course.mapper.CourseMapper;
import com.eduplatform.course.dto.CourseDTO;
import com.eduplatform.course.vo.CourseVO;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 课程核心服务实现类
 * 负责课程资源的全生命周期管理，涵盖课程创建、元数据更新、多维度检索、提审撤回及复杂的审核流状态机。
 * 
 * 核心功能：
 * 1. 跨服务元数据聚合：通过 Feign 实时同步 User-service 的教师姓名，解决分布式环境下的数据一致性与展示需求。
 * 2. 高性能检索：利用 Spring Cache + Redis 对已发布的课程列表进行按学科维度的二级缓存优化。
 * 3. 审计闭环：深度集成 AuditLogClient，确保每一次审核动作及状态变更均有迹可循。
 *
 * @author Antigravity
 */
@Service
@RequiredArgsConstructor
public class CourseService {

    private final CourseMapper courseMapper;
    private final ChapterMapper chapterMapper;
    private final AuditLogClient auditLogClient;
    private final UserServiceClient userServiceClient;

    /**
     * 将持久层课程实体映射为视图对象 (VO)
     * 用于前端 UI 渲染，隐藏敏感字段并确保属性命名符合协议规范。
     *
     * @param course 课程物理记录实体
     * @return 准备好传输的 CourseVO
     */
    public CourseVO convertToVO(Course course) {
        if (course == null) {
            return null;
        }
        CourseVO vo = new CourseVO();
        BeanUtils.copyProperties(course, vo);
        return vo;
    }

    /**
     * 批量实体对象转换为视图列表
     * 
     * @param courses 实体列表
     * @return 视图对象列表
     */
    public List<CourseVO> convertToVOList(List<Course> courses) {
        return courses.stream()
                .map(this::convertToVO)
                .collect(Collectors.toList());
    }

    /**
     * 异步/同步统计并填充章节总数
     * 业务场景：在列表展现时展示“共 X 章节”，增强用户点击欲望。
     * 
     * @param courses 待填充的课程列表
     */
    private void fillChapterCounts(List<Course> courses) {
        if (courses == null || courses.isEmpty()) {
            return;
        }

        // 批量查询课程章节并按课程ID聚合，避免逐课程 count 的 N+1 查询
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
     * 实时回显教师姓名 (跨服务聚合)
     * 由于课程表仅存储关联的 teacher_id，本方法通过 RPC 调用 User-service 获取最新昵称，
     * 避免了分布式系统中的硬编码冗余，并支持故障降级。
     * 
     * @param courses 待填充的课程列表
     */
    private void fillTeacherNames(List<Course> courses) {
        if (courses == null || courses.isEmpty()) {
            return;
        }

        // 优先走批量查询路径，降低远程调用次数；失败时降级到原有逐条查询逻辑
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
                // 批量查询异常时走降级逻辑
            }
        }

        if (optimizedApplied) {
            // 兜底：仍为空的教师名填充默认值，避免前端出现空白
            for (Course course : courses) {
                if (course.getTeacherId() != null && (course.getTeacherName() == null || course.getTeacherName().isBlank())) {
                    course.setTeacherName("未知教师");
                }
            }
            return;
        }

        for (Course course : courses) {
            if (course.getTeacherId() != null) {
                try {
                    Result<UserBriefDTO> result = userServiceClient.getUserById(course.getTeacherId());
                    if (result != null && result.getData() != null) {
                        UserBriefDTO user = result.getData();
                        String teacherName = user.getName();
                        if (teacherName == null || teacherName.isEmpty()) {
                            teacherName = user.getUsername();
                        }
                        course.setTeacherName(teacherName);
                    }
                } catch (Exception e) {
                    // 容错处理：当 User-service 不可用时，显示预设名称，保证界面不空白
                    if (course.getTeacherName() == null) {
                        course.setTeacherName("未知教师");
                    }
                }
            }
        }
    }

    /**
     * 管理员/内部多维度课程检索
     * 支持学科、状态组合筛选，常用于后台运营管理系统统计与维护。
     *
     * @param subject 学科枚举分类 (如：MATH, CS，传入 "all" 或 null 不限)
     * @param status  过滤状态 (对应 Course 常量，如 STATUS_PUBLISHED)
     * @return 经过元数据填充（章节数、教师名）后的课程列表
     */
    public List<Course> getAllCourses(String subject, String status) {
        LambdaQueryWrapper<Course> wrapper = new LambdaQueryWrapper<>();
        if (subject != null && !subject.isEmpty() && !"all".equals(subject)) {
            wrapper.eq(Course::getSubject, subject);
        }
        if (status != null && !status.isEmpty()) {
            wrapper.eq(Course::getStatus, status);
        }
        wrapper.orderByDesc(Course::getCreatedAt);
        List<Course> courses = courseMapper.selectList(wrapper);
        fillChapterCounts(courses);
        fillTeacherNames(courses);
        return courses;
    }

    /**
     * 面向 C 端学生的精选发布课程检索 (带缓存)
     * 业务逻辑：仅展示已发布的 (PUBLISHED) 课程，并根据学科分类进行 Redis 缓存。
     * 缓存失效场景：当课程被修改或下线时，需手动或自动清理该缓存。
     *
     * @param subject 分类标识
     * @return 缓存命中的或从库加载的课程集合
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
     * 获取单一课程详情
     * 包含完整的教师上下文展示属性，适用于详情页呈现。
     * 
     * @param id 课程唯一标识
     * @return 课程实体（含教师回显），若不存在返回 null
     */
    public Course getById(Long id) {
        Course course = courseMapper.selectById(id);
        if (course != null) {
            fillTeacherNames(java.util.Collections.singletonList(course));
        }
        return course;
    }

    /**
     * 初始化创建新课程 (Draft 模式)
     * 设置默认的基础分值、零学生计数及“草稿”状态，等待教师后续完善章节。
     *
     * @param dto 包含标题、描述、分类及封面图的原始 DTO
     * @return 初始化完成后的持久层实体
     */
    public Course createCourse(CourseDTO dto) {
        if (dto == null) {
            throw new IllegalArgumentException("课程数据载荷缺失");
        }
        Course course = new Course();
        BeanUtils.copyProperties(dto, course);
        course.setCreatedAt(LocalDateTime.now());
        course.setUpdatedAt(LocalDateTime.now());
        course.setRating(0.0);
        course.setStudentCount(0);
        course.setStatus(Course.STATUS_DRAFT);
        courseMapper.insert(course);
        return course;
    }

    /**
     * 修订存量课程关键元数据
     * 注意：本接口仅负责属性同步，不涉及状态扭转或提审。
     *
     * @param id  目标课程唯一标识
     * @param dto 包含待修改属性的容器
     */
    public void updateCourse(Long id, CourseDTO dto) {
        if (dto == null) {
            throw new IllegalArgumentException("更新内容不可为空");
        }
        Course existing = courseMapper.selectById(id);
        if (existing == null) {
            throw new RuntimeException("操作失败：目标课程不存在于系统中");
        }
        // 按需覆盖可变属性
        if (dto.getTitle() != null)
            existing.setTitle(dto.getTitle());
        if (dto.getDescription() != null)
            existing.setDescription(dto.getDescription());
        if (dto.getSubject() != null)
            existing.setSubject(dto.getSubject());
        if (dto.getCoverImage() != null)
            existing.setCoverImage(dto.getCoverImage());
        existing.setUpdatedAt(LocalDateTime.now());
        courseMapper.updateById(existing);
    }

    /**
     * 原子化更新课程物理状态 (基本操作)
     * 自动处理第三方或数字状态码与系统标准状态字符串的归一化映射。
     *
     * @param id     课程 ID
     * @param status 目标状态 (支持数字码 0/1/2 或标准字符串)
     */
    public void updateStatus(Long id, String status) {
        Course course = courseMapper.selectById(id);
        if (course == null) {
            throw new RuntimeException("课程不存在");
        }
        // 执行双层状态归一化：确保状态语义全站一致
        String normalizedStatus = normalizeStatus(status);
        course.setStatus(normalizedStatus);
        course.setUpdatedAt(LocalDateTime.now());
        courseMapper.updateById(course);
    }

    /**
     * 具备审计追踪的状态更新 (运营操作)
     * 在变更课程状态的同时，同步投递一条结构化的审计日志，记录操作人上下文。
     * 
     * @param id           课程 ID
     * @param status       目标状态
     * @param operatorId   执行者 ID
     * @param operatorName 执行者姓名
     * @param ipAddress    操作物理起源 IP
     */
    public void updateStatusWithAudit(Long id, String status, Long operatorId, String operatorName, String ipAddress) {
        Course course = courseMapper.selectById(id);
        if (course == null) {
            throw new RuntimeException("课程不存在");
        }
        String normalizedStatus = normalizeStatus(status);
        course.setStatus(normalizedStatus);
        course.setUpdatedAt(LocalDateTime.now());
        courseMapper.updateById(course);

        // 审计详情构建：区分上架与下架语义
        String actionType = Course.STATUS_OFFLINE.equals(normalizedStatus) ? "COURSE_OFFLINE" : "COURSE_ONLINE";
        String details = Course.STATUS_OFFLINE.equals(normalizedStatus) ? "下架课程" : "上架课程";

        Map<String, Object> auditLog = new HashMap<>();
        auditLog.put("operatorId", operatorId);
        auditLog.put("operatorName", operatorName);
        auditLog.put("actionType", actionType);
        auditLog.put("targetType", "COURSE");
        auditLog.put("targetId", id);
        auditLog.put("targetName", course.getTitle());
        auditLog.put("details", details);
        auditLog.put("ipAddress", ipAddress != null ? ipAddress : "unknown");

        try {
            auditLogClient.createAuditLog(auditLog);
        } catch (Exception e) {
            // 审计策略：异步解耦，记录失败不通过抛出异常阻断业务主进程
        }
    }

    /**
     * 状态归一化转换器
     * 处理遗留系统数字码或非标字符串，确保数据库内部状态的高确定性。
     */
    private String normalizeStatus(String status) {
        if (status == null)
            return Course.STATUS_DRAFT;
        switch (status) {
            case "0":
                return Course.STATUS_REVIEWING;
            case "1":
                return Course.STATUS_PUBLISHED;
            case "2":
                return Course.STATUS_OFFLINE;
            case "DRAFT":
            case "REVIEWING":
            case "PUBLISHED":
            case "OFFLINE":
            case "REJECTED":
            case "BANNED":
                return status;
            default:
                return Course.STATUS_DRAFT;
        }
    }

    /**
     * 教师提审动作
     * 逻辑前置校验：仅允许“草稿”或“被驳回”状态发起提审，强制锁定当前版本内容进入审核流。
     * 
     * @param id 待审课程 ID
     */
    public void submitReview(Long id) {
        Course course = courseMapper.selectById(id);
        if (course == null) {
            throw new RuntimeException("课程不存在");
        }
        if (!Course.STATUS_DRAFT.equals(course.getStatus()) && !Course.STATUS_REJECTED.equals(course.getStatus())) {
            throw new RuntimeException("当前课程状态不允许发起提审");
        }
        course.setStatus(Course.STATUS_REVIEWING);
        course.setSubmitTime(LocalDateTime.now());
        course.setUpdatedAt(LocalDateTime.now());
        courseMapper.updateById(course);
    }

    /**
     * 教师主动撤回申请
     * 将处于“审核中”的课程回退至“草稿”态，释放修改权限。
     */
    public void withdrawReview(Long id) {
        Course course = courseMapper.selectById(id);
        if (course == null) {
            throw new RuntimeException("课程不存在");
        }
        if (!Course.STATUS_REVIEWING.equals(course.getStatus())) {
            throw new RuntimeException("非审核中状态，无法执行撤回操作");
        }
        course.setStatus(Course.STATUS_DRAFT);
        course.setUpdatedAt(LocalDateTime.now());
        courseMapper.updateById(course);
    }

    /**
     * 管理员正式审核决策
     * 核心流程：验证状态 -> 分辨通过/驳回动作 -> 更新字段(审核人/时间/反馈) -> 异步审计日志。
     * 
     * @param id          课程 ID
     * @param action      决策动作 (APPROVE-通过, REJECT-驳回)
     * @param remark      审核评语 (驳回时必填改进建议)
     * @param auditBy     管理员 ID
     * @param auditByName 管理员姓名
     * @param ipAddress   操作 IP
     */
    public void auditCourse(Long id, String action, String remark, Long auditBy, String auditByName, String ipAddress) {
        Course course = courseMapper.selectById(id);
        if (course == null) {
            throw new RuntimeException("课程不存在");
        }
        if (!Course.STATUS_REVIEWING.equals(course.getStatus())) {
            throw new RuntimeException("课程未处于待审核状态");
        }

        String actionType;
        String details;
        if ("APPROVE".equals(action)) {
            course.setStatus(Course.STATUS_PUBLISHED);
            actionType = "COURSE_APPROVE";
            details = "审核通过课程" + (remark != null && !remark.isEmpty() ? "，备注：" + remark : "");
        } else if ("REJECT".equals(action)) {
            course.setStatus(Course.STATUS_REJECTED);
            actionType = "COURSE_REJECT";
            details = "驳回课程" + (remark != null && !remark.isEmpty() ? "，原因：" + remark : "");
        } else {
            throw new RuntimeException("由于无效的操作类型，审核请求被拒绝");
        }

        course.setAuditBy(auditBy);
        course.setAuditTime(LocalDateTime.now());
        course.setAuditRemark(remark);
        course.setUpdatedAt(LocalDateTime.now());
        courseMapper.updateById(course);

        // 分发审计日志
        if (auditBy != null && auditByName != null) {
            try {
                Map<String, Object> logData = new HashMap<>();
                logData.put("operatorId", auditBy);
                logData.put("operatorName", auditByName);
                logData.put("actionType", actionType);
                logData.put("targetType", "COURSE");
                logData.put("targetId", id);
                logData.put("targetName", course.getTitle());
                logData.put("details", details);
                logData.put("ipAddress", ipAddress != null ? ipAddress : "unknown");
                auditLogClient.createAuditLog(logData);
            } catch (Exception e) {
                // 容错：审计链路异常不回滚业务数据库
            }
        }
    }

    /**
     * 内部无感知审核 (用于自动化测试或系统级批量状态同步)
     */
    public void auditCourseInternal(Long id, String action, String remark, Long auditBy) {
        Course course = courseMapper.selectById(id);
        if (course == null) {
            throw new RuntimeException("课程记录缺失");
        }
        if (!Course.STATUS_REVIEWING.equals(course.getStatus())) {
            throw new RuntimeException("状态不符：无法执行内部审核");
        }

        if ("APPROVE".equals(action)) {
            course.setStatus(Course.STATUS_PUBLISHED);
        } else if ("REJECT".equals(action)) {
            course.setStatus(Course.STATUS_REJECTED);
        } else {
            throw new RuntimeException("无效的内部审核动作");
        }

        course.setAuditBy(auditBy);
        course.setAuditTime(LocalDateTime.now());
        course.setAuditRemark(remark);
        course.setUpdatedAt(LocalDateTime.now());
        courseMapper.updateById(course);
    }

    /**
     * 强行下线课程 (紧急运营动作)
     * 将课程状态标记为 OFFLINE，阻断新用户选课，同时记录下架审计证据。
     */
    public void offlineCourse(Long id, Long operatorId, String operatorName, String ipAddress) {
        Course course = courseMapper.selectById(id);
        if (course == null) {
            throw new RuntimeException("课程记录不存在");
        }

        course.setStatus(Course.STATUS_OFFLINE);
        course.setUpdatedAt(LocalDateTime.now());
        courseMapper.updateById(course);

        // 记录审计轨迹
        if (operatorId != null && operatorName != null) {
            try {
                Map<String, Object> logData = new HashMap<>();
                logData.put("operatorId", operatorId);
                logData.put("operatorName", operatorName);
                logData.put("actionType", "COURSE_OFFLINE");
                logData.put("targetType", "COURSE");
                logData.put("targetId", id);
                logData.put("targetName", course.getTitle());
                logData.put("details", "执行强制下线操作");
                logData.put("ipAddress", ipAddress != null ? ipAddress : "unknown");
                auditLogClient.createAuditLog(logData);
            } catch (Exception e) {
                // 容错处理
            }
        }
    }

    /**
     * 获取教师名下的私人课程列表
     * 按创建时间逆序排列，并填充实时章节总数。
     */
    public List<Course> getTeacherCourses(Long teacherId) {
        List<Course> courses = courseMapper.selectList(
                new LambdaQueryWrapper<Course>()
                        .eq(Course::getTeacherId, teacherId)
                        .orderByDesc(Course::getCreatedAt));
        fillChapterCounts(courses);
        return courses;
    }

    /**
     * 获取全站处于“待审核”队列的课程
     * 按照提交提审的时间顺序排列（先提审先处理）。
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
     * 物理删除课程记录 (谨慎使用)
     * 注意：本接口未包含级联删除逻辑，若需深度清理请参阅 CourseCascadeDeleteService。
     */
    public void deleteCourse(Long id) {
        courseMapper.deleteById(id);
    }

    /**
     * 按状态统计全站课程基数
     */
    public long countByStatus(String status) {
        LambdaQueryWrapper<Course> wrapper = new LambdaQueryWrapper<>();
        if (status != null && !status.isEmpty()) {
            wrapper.eq(Course::getStatus, status);
        }
        return courseMapper.selectCount(wrapper);
    }

    /**
     * 按学科统计已发布的活跃课程总数
     */
    public long countBySubject(String subject) {
        return courseMapper.selectCount(
                new LambdaQueryWrapper<Course>().eq(Course::getSubject, subject).eq(Course::getStatus,
                        Course.STATUS_PUBLISHED));
    }

    /**
     * 批量更新课程状态 (集群运营操作)
     * 内置复杂的状态转换合法性校验，支持部分成功结果返回。
     * 
     * @param courseIds    目标课程 ID 集合
     * @param status       期望变更为的状态
     * @param operatorId   操作人 ID
     * @param operatorName 操作人姓名
     * @param ipAddress    操作 IP
     * @return 包含 successCount (成功数)、failCount (失败数) 及详情的 Map 结果集
     */
    public Map<String, Object> batchUpdateStatus(List<Long> courseIds, String status,
            Long operatorId, String operatorName, String ipAddress) {
        int successCount = 0;
        int failCount = 0;
        List<String> failedCourses = new java.util.ArrayList<>();

        for (Long courseId : courseIds) {
            try {
                Course course = courseMapper.selectById(courseId);
                if (course == null) {
                    failCount++;
                    failedCourses.add("课程 ID " + courseId + " 不存在");
                    continue;
                }

                // 执行防腐层校验：防止非法的状态跳变 (如：从草稿直接到下线)
                if (!isValidStatusTransition(course.getStatus(), status)) {
                    failCount++;
                    failedCourses.add(course.getTitle() + ": 无法从 " + course.getStatus() + " 转换到 " + status);
                    continue;
                }

                course.setStatus(status);
                course.setUpdatedAt(LocalDateTime.now());
                courseMapper.updateById(course);

                // 同步审计快照
                if (operatorId != null && operatorName != null) {
                    try {
                        Map<String, Object> logData = new HashMap<>();
                        logData.put("operatorId", operatorId);
                        logData.put("operatorName", operatorName);
                        logData.put("actionType", "COURSE_BATCH_STATUS");
                        logData.put("targetType", "COURSE");
                        logData.put("targetId", courseId);
                        logData.put("targetName", course.getTitle());
                        logData.put("details", "批量更新状态为: " + status);
                        logData.put("ipAddress", ipAddress != null ? ipAddress : "unknown");
                        auditLogClient.createAuditLog(logData);
                    } catch (Exception e) {
                        // 记录失败不扣减业务成功数
                    }
                }

                successCount++;
            } catch (Exception e) {
                failCount++;
                failedCourses.add("课程 ID " + courseId + ": " + e.getMessage());
            }
        }

        Map<String, Object> result = new HashMap<>();
        result.put("successCount", successCount);
        result.put("failCount", failCount);
        result.put("total", courseIds.size());
        result.put("failedCourses", failedCourses);
        return result;
    }

    /**
     * 业务规则拦截：验证状态转换路径是否符合工作流规范
     * 
     * 规则集：
     * 1. PUBLISHED: 仅能从 REVIEWING 跃迁。
     * 2. OFFLINE: 仅能从 PUBLISHED/BANNED(特定条件) 跃迁。
     * 3. DRAFT: 允许从 REVIEWING/REJECTED 撤回或重置。
     */
    private boolean isValidStatusTransition(String currentStatus, String targetStatus) {
        if (Course.STATUS_PUBLISHED.equals(targetStatus)) {
            return Course.STATUS_REVIEWING.equals(currentStatus);
        }
        if (Course.STATUS_OFFLINE.equals(targetStatus)) {
            return Course.STATUS_PUBLISHED.equals(currentStatus);
        }
        if (Course.STATUS_DRAFT.equals(targetStatus)) {
            return Course.STATUS_REVIEWING.equals(currentStatus) ||
                    Course.STATUS_REJECTED.equals(currentStatus);
        }
        return false;
    }

    /**
     * 以存量课程为模板快速创建新课 (克隆模式)
     * 常用于复用教学大纲。注意：克隆后的课程强制回归 DRAFT 状态。
     * 
     * @param courseId  模板课程 ID
     * @param newTitle  新课程标题
     * @param teacherId 新归属教师 ID
     * @return 克隆生成的持久层实体
     */
    public Course duplicateCourse(Long courseId, String newTitle, Long teacherId) {
        Course source = courseMapper.selectById(courseId);
        if (source == null) {
            throw new RuntimeException("操作失败：模板课程记录缺失");
        }

        Course newCourse = new Course();
        newCourse.setTitle(newTitle != null ? newTitle : source.getTitle() + " (副本)");
        newCourse.setDescription(source.getDescription());
        newCourse.setSubject(source.getSubject());
        newCourse.setCoverImage(source.getCoverImage());
        newCourse.setTeacherId(teacherId != null ? teacherId : source.getTeacherId());
        newCourse.setStatus(Course.STATUS_DRAFT);
        newCourse.setRating(0.0);
        newCourse.setStudentCount(0);
        newCourse.setCreatedAt(LocalDateTime.now());
        newCourse.setUpdatedAt(LocalDateTime.now());

        courseMapper.insert(newCourse);
        return newCourse;
    }

    /**
     * 获取全站课程运营看板统计数据 (带缓存)
     * 业务场景：后台管理系统首页仪表盘展示，提供全量、学科、状态的三维分析。
     * 缓存策略：全局 Redis 缓存，默认失效时间 10 分钟 (由 RedisConfig 统一管控)。
     * 
     * @return 包含 total(总数), draft(草稿数), published(上架数), offline(下架数) 及
     *         subjectStats(学科分布) 的 Map
     */
    @org.springframework.cache.annotation.Cacheable(value = "course_stats", key = "'dashboard'")
    public Map<String, Object> getCourseStatistics() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("total", countByStatus(null));
        stats.put("draft", countByStatus(Course.STATUS_DRAFT));
        stats.put("reviewing", countByStatus(Course.STATUS_REVIEWING));
        stats.put("published", countByStatus(Course.STATUS_PUBLISHED));
        stats.put("offline", countByStatus(Course.STATUS_OFFLINE));

        // 按学科维度下钻统计
        Map<String, Long> subjectStats = new HashMap<>();
        String[] subjects = { "语文", "数学", "英语", "物理", "化学", "生物", "政治", "历史", "地理" };
        for (String subject : subjects) {
            subjectStats.put(subject, countBySubject(subject));
        }
        stats.put("subjectStats", subjectStats);

        return stats;
    }

    /**
     * 获取按学科分组的课程分布统计
     * 用于管理员仪表盘的课程分布饼图展示。
     *
     * @return 包含学科列表、课程数及学生数的统计数据
     */
    public Map<String, Object> getCourseStatsBySubject() {
        Map<String, Object> result = new HashMap<>();
        List<String> subjects = new java.util.ArrayList<>();
        List<Long> courseCounts = new java.util.ArrayList<>();
        List<Long> studentCounts = new java.util.ArrayList<>();

        // 定义系统支持的学科列表
        String[] allSubjects = { "语文", "数学", "英语", "物理", "化学", "生物", "政治", "历史", "地理" };

        for (String subject : allSubjects) {
            // 统计该学科的已发布课程数
            long courseCount = courseMapper.selectCount(
                    new LambdaQueryWrapper<Course>()
                            .eq(Course::getSubject, subject)
                            .eq(Course::getStatus, Course.STATUS_PUBLISHED));

            // 只添加有课程的学科
            if (courseCount > 0) {
                subjects.add(subject);
                courseCounts.add(courseCount);

                // 统计该学科的学生总数（所有已发布课程的学生数之和）
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
