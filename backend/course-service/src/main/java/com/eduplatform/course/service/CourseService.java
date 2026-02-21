package com.eduplatform.course.service;

import com.eduplatform.course.entity.Course;
import com.eduplatform.course.mapper.CourseMapper;
import com.eduplatform.course.dto.CourseDTO;
import com.eduplatform.course.vo.CourseVO;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
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
    private final CourseReadService courseReadService;
    private final CourseWorkflowService courseWorkflowService;

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
     * 管理员/内部多维度课程检索
     * 支持学科、状态组合筛选，常用于后台运营管理系统统计与维护。
     *
     * @param subject 学科枚举分类 (如：MATH, CS，传入 "all" 或 null 不限)
     * @param status  过滤状态 (对应 Course 常量，如 STATUS_PUBLISHED)
     * @return 经过元数据填充（章节数、教师名）后的课程列表
     */
    public List<Course> getAllCourses(String subject, String status) {
        return courseReadService.getAllCourses(subject, status);
    }

    /**
     * 管理端课程列表（默认不展示草稿）。
     * 业务规则：草稿仅课程所属教师可见，管理员默认看不到草稿课程。
     *
     * @param subject 学科分类（可选）
     * @param status  课程状态（可选）
     * @return 管理端可见课程列表
     */
    public List<Course> getAdminVisibleCourses(String subject, String status) {
        return courseReadService.getAdminVisibleCourses(subject, status);
    }

    /**
     * 教师端课程列表（仅本人课程，支持按学科/状态筛选）。
     *
     * @param teacherId 教师ID
     * @param subject   学科分类（可选）
     * @param status    课程状态（可选）
     * @return 教师可见课程列表
     */
    public List<Course> getTeacherCourses(Long teacherId, String subject, String status) {
        return courseReadService.getTeacherCourses(teacherId, subject, status);
    }

    /**
     * 面向 C 端学生的精选发布课程检索 (带缓存)
     * 业务逻辑：仅展示已发布的 (PUBLISHED) 课程，并根据学科分类进行 Redis 缓存。
     * 缓存失效场景：当课程被修改或下线时，需手动或自动清理该缓存。
     *
     * @param subject 分类标识
     * @return 缓存命中的或从库加载的课程集合
     */
    public List<Course> getPublishedCourses(String subject) {
        return courseReadService.getPublishedCourses(subject);
    }

    /**
     * 获取单一课程详情
     * 包含完整的教师上下文展示属性，适用于详情页呈现。
     * 
     * @param id 课程唯一标识
     * @return 课程实体（含教师回显），若不存在返回 null
     */
    public Course getById(Long id) {
        return courseReadService.getById(id);
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
     * 注意：教师每次保存后课程会自动进入待审核状态，需管理员审核通过后再次发布。
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

        // 教师每次保存课程后都进入待审核状态，等待管理员审核通过后再次发布
        existing.setStatus(Course.STATUS_REVIEWING);
        existing.setSubmitTime(LocalDateTime.now());
        // 清空上一轮审核结果，避免历史审核信息误导当前版本
        existing.setAuditBy(null);
        existing.setAuditTime(null);
        existing.setAuditRemark(null);
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
        courseWorkflowService.updateStatus(id, status);
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
        courseWorkflowService.updateStatusWithAudit(id, status, operatorId, operatorName, ipAddress);
    }

    /**
     * 教师提审动作
     * 逻辑前置校验：仅允许“草稿”或“被驳回”状态发起提审，强制锁定当前版本内容进入审核流。
     * 
     * @param id 待审课程 ID
     */
    public void submitReview(Long id) {
        courseWorkflowService.submitReview(id);
    }

    /**
     * 教师主动撤回申请
     * 将处于“审核中”的课程回退至“草稿”态，释放修改权限。
     */
    public void withdrawReview(Long id) {
        courseWorkflowService.withdrawReview(id);
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
        courseWorkflowService.auditCourse(id, action, remark, auditBy, auditByName, ipAddress);
    }

    /**
     * 内部无感知审核 (用于自动化测试或系统级批量状态同步)
     */
    public void auditCourseInternal(Long id, String action, String remark, Long auditBy) {
        courseWorkflowService.auditCourseInternal(id, action, remark, auditBy);
    }

    /**
     * 强行下线课程 (紧急运营动作)
     * 将课程状态标记为 OFFLINE，阻断新用户选课，同时记录下架审计证据。
     */
    public void offlineCourse(Long id, Long operatorId, String operatorName, String ipAddress) {
        courseWorkflowService.offlineCourse(id, operatorId, operatorName, ipAddress);
    }

    /**
     * 获取教师名下的私人课程列表
     * 按创建时间逆序排列，并填充实时章节总数。
     */
    public List<Course> getTeacherCourses(Long teacherId) {
        return courseReadService.getTeacherCourses(teacherId);
    }

    /**
     * 获取全站处于“待审核”队列的课程
     * 按照提交提审的时间顺序排列（先提审先处理）。
     */
    public List<Course> getReviewingCourses() {
        return courseReadService.getReviewingCourses();
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
        return courseReadService.countByStatus(status);
    }

    /**
     * 按学科统计已发布的活跃课程总数
     */
    public long countBySubject(String subject) {
        return courseReadService.countBySubject(subject);
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
        return courseWorkflowService.batchUpdateStatus(courseIds, status, operatorId, operatorName, ipAddress);
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
    public Map<String, Object> getCourseStatistics() {
        return courseReadService.getCourseStatistics();
    }

    /**
     * 获取按学科分组的课程分布统计
     * 用于管理员仪表盘的课程分布饼图展示。
     *
     * @return 包含学科列表、课程数及学生数的统计数据
     */
    public Map<String, Object> getCourseStatsBySubject() {
        return courseReadService.getCourseStatsBySubject();
    }
}
