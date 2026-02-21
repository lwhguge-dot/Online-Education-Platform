package com.eduplatform.user.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.eduplatform.common.result.Result;
import com.eduplatform.user.dto.AnnouncementRequestDTO;
import com.eduplatform.user.dto.AnnouncementStatsDTO;
import com.eduplatform.user.dto.TeacherAnnouncementDTO;
import com.eduplatform.user.entity.Announcement;
import com.eduplatform.user.service.AnnouncementService;
import com.eduplatform.user.vo.AnnouncementVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 公告管理控制器。
 * 处理系统全量公告及教师针对特定课程发布的公告信息。
 * 设计意图：控制层仅接收 DTO 并输出 VO，避免实体直出导致字段泄露。
 */
@RestController
@RequestMapping("/api/announcements")
@RequiredArgsConstructor
public class AnnouncementController {

    private final AnnouncementService announcementService;

    /**
     * 后台全量公告分页检索 (管理员控制台)。
     * 业务原因：管理端需要统一分页与状态过滤能力，避免前端二次筛选。
     *
     * @param page           起始页
     * @param size           页容量
     * @param status         状态过滤 (DRAFT/PUBLISHED)
     * @param targetAudience 目标受众过滤 (ALL/STUDENT/TEACHER)
     * @return 包含记录、总数、页码等标准分页格式的 Result
     */
    @GetMapping
    public Result<Map<String, Object>> getAnnouncements(
            @RequestParam(name = "page", defaultValue = "1") int page,
            @RequestParam(name = "size", defaultValue = "20") int size,
            @RequestParam(name = "status", required = false) String status,
            @RequestParam(name = "targetAudience", required = false) String targetAudience,
            @RequestHeader(value = "X-User-Role", required = false) String currentUserRole) {
        // 管理后台全量检索仅允许管理员访问
        if (!isAdminRole(currentUserRole)) {
            return Result.failure(403, "权限不足，仅管理员可访问全量公告列表");
        }

        IPage<Announcement> pageResult = announcementService.findByPage(page, size, status, targetAudience);
        List<AnnouncementVO> records = announcementService.convertToVOList(pageResult.getRecords());

        Map<String, Object> result = new HashMap<>();
        result.put("records", records);
        result.put("total", pageResult.getTotal());
        result.put("pages", pageResult.getPages());
        result.put("current", pageResult.getCurrent());
        result.put("size", pageResult.getSize());

        return Result.success(result);
    }

    /**
     * 获取面向当前用户的已发布公告。
     * 业务原因：统一在服务端完成受众过滤，减少前端冗余判断。
     *
     * @param audience 角色标识 (如 STUDENT)
     * @return 置顶优先的公告列表
     */
    @GetMapping("/active")
    public Result<List<AnnouncementVO>> getActiveAnnouncements(
            @RequestParam(name = "audience", required = false) String audience) {
        List<Announcement> announcements = announcementService.findActiveByAudience(audience);
        return Result.success(announcementService.convertToVOList(announcements));
    }

    /**
     * 获取公告图文详情。
     * 说明：若公告不存在直接返回错误提示，避免前端空页面。
     */
    @GetMapping("/{id}")
    public Result<AnnouncementVO> getAnnouncementById(@PathVariable("id") Long id) {
        Announcement announcement = announcementService.findById(id);
        if (announcement == null) {
            return Result.error("公告已下架或不存在");
        }
        return Result.success(announcementService.convertToVO(announcement));
    }

    /**
     * 创建系统级公告。
     * 初始状态默认为 DRAFT（草稿），避免误发布。
    */
    @PostMapping
    public Result<AnnouncementVO> createAnnouncement(
            @Valid @RequestBody AnnouncementRequestDTO request,
            @RequestHeader(value = "X-User-Role", required = false) String currentUserRole) {
        // 系统级公告创建仅允许管理员操作
        if (!isAdminRole(currentUserRole)) {
            return Result.failure(403, "权限不足，仅管理员可创建系统公告");
        }
        Announcement created = announcementService.create(buildAnnouncementEntity(request));
        return Result.success("公告已创建，请及时发布", announcementService.convertToVO(created));
    }

    /**
     * 全量更新公告内容。
     * 说明：控制层接收 DTO 并转实体，避免直写实体字段。
     */
    @PutMapping("/{id}")
    public Result<AnnouncementVO> updateAnnouncement(
            @PathVariable("id") Long id,
            @Valid @RequestBody AnnouncementRequestDTO request,
            @RequestHeader(value = "X-User-Role", required = false) String currentUserRole) {
        // 系统级公告更新仅允许管理员操作
        if (!isAdminRole(currentUserRole)) {
            return Result.failure(403, "权限不足，仅管理员可更新系统公告");
        }
        Announcement updated = announcementService.update(id, buildAnnouncementEntity(request));
        return Result.success("内容修正成功", announcementService.convertToVO(updated));
    }

    /**
     * 永久物理删除公告。
     * 说明：此操作不可逆，通常用于后台治理或违规内容清理。
    */
    @DeleteMapping("/{id}")
    public Result<Void> deleteAnnouncement(
            @PathVariable("id") Long id,
            @RequestHeader(value = "X-User-Role", required = false) String currentUserRole) {
        // 系统级公告删除仅允许管理员操作
        if (!isAdminRole(currentUserRole)) {
            return Result.failure(403, "权限不足，仅管理员可删除系统公告");
        }
        announcementService.delete(id);
        return Result.success("公告条目已彻底移除", null);
    }

    /**
     * 发布公告 (使受众可见)。
     * 业务原因：统一在服务端控制发布时间与状态切换。
    */
    @PostMapping("/{id}/publish")
    public Result<AnnouncementVO> publishAnnouncement(
            @PathVariable("id") Long id,
            @RequestHeader(value = "X-User-Role", required = false) String currentUserRole) {
        // 系统级公告发布仅允许管理员操作
        if (!isAdminRole(currentUserRole)) {
            return Result.failure(403, "权限不足，仅管理员可发布系统公告");
        }
        Announcement published = announcementService.publish(id);
        return Result.success("公告发布成功，前端实时生效", announcementService.convertToVO(published));
    }

    // ==================== 课程/教师私有公告接口 ====================

    /**
     * 教师向自有课程/全员发布消息。
     * 业务逻辑：自动填充创建人为当前教师 ID。
     *
     * @param teacherId 发布者 ID
     * @param dto       业务数据 (含标题、内容、可选课程 ID)
     */
    @PostMapping("/teachers/{teacherId}")
    public Result<AnnouncementVO> createTeacherAnnouncement(
            @PathVariable("teacherId") Long teacherId,
            @Valid @RequestBody TeacherAnnouncementDTO dto,
            @RequestHeader(value = "X-User-Id", required = false) String currentUserIdHeader,
            @RequestHeader(value = "X-User-Role", required = false) String currentUserRole) {
        // 教师公告仅允许教师本人或管理员操作
        Long currentUserId = parseUserId(currentUserIdHeader);
        if (!canManageTeacherAnnouncement(teacherId, currentUserId, currentUserRole)) {
            return Result.failure(403, "权限不足，仅教师本人或管理员可发布教师公告");
        }
        Announcement created = announcementService.createByTeacher(teacherId, dto);
        return Result.success("教学公告已送达", announcementService.convertToVO(created));
    }

    /**
     * 教师更新历史发布的课程公告。
     * 说明：仅允许修改本人发布的公告，具体校验由服务层处理。
     */
    @PutMapping("/teachers/{teacherId}/{announcementId}")
    public Result<AnnouncementVO> updateTeacherAnnouncement(
            @PathVariable("teacherId") Long teacherId,
            @PathVariable("announcementId") Long announcementId,
            @Valid @RequestBody TeacherAnnouncementDTO dto,
            @RequestHeader(value = "X-User-Id", required = false) String currentUserIdHeader,
            @RequestHeader(value = "X-User-Role", required = false) String currentUserRole) {
        // 教师公告更新仅允许教师本人或管理员操作
        Long currentUserId = parseUserId(currentUserIdHeader);
        if (!canManageTeacherAnnouncement(teacherId, currentUserId, currentUserRole)) {
            return Result.failure(403, "权限不足，仅教师本人或管理员可更新教师公告");
        }
        Announcement updated = announcementService.updateByTeacher(teacherId, announcementId, dto);
        return Result.success("公告修订完成", announcementService.convertToVO(updated));
    }

    /**
     * 教师撤回/物理删除公告。
     * 说明：删除权限由服务层校验，避免越权。
     */
    @DeleteMapping("/teachers/{teacherId}/{announcementId}")
    public Result<Void> deleteTeacherAnnouncement(
            @PathVariable("teacherId") Long teacherId,
            @PathVariable("announcementId") Long announcementId,
            @RequestHeader(value = "X-User-Id", required = false) String currentUserIdHeader,
            @RequestHeader(value = "X-User-Role", required = false) String currentUserRole) {
        // 教师公告删除仅允许教师本人或管理员操作
        Long currentUserId = parseUserId(currentUserIdHeader);
        if (!canManageTeacherAnnouncement(teacherId, currentUserId, currentUserRole)) {
            return Result.failure(403, "权限不足，仅教师本人或管理员可删除教师公告");
        }
        announcementService.deleteByTeacher(teacherId, announcementId);
        return Result.success("该内容已从授课范围消失", null);
    }

    /**
     * 分页检索特定教师的所有发布历史。
     * 支持结合课程 ID 进行精准追溯。
     */
    @GetMapping("/teachers/{teacherId}")
    public Result<Map<String, Object>> getTeacherAnnouncements(
            @PathVariable("teacherId") Long teacherId,
            @RequestParam(name = "courseId", required = false) Long courseId,
            @RequestParam(name = "status", required = false) String status,
            @RequestParam(name = "page", defaultValue = "1") int page,
            @RequestParam(name = "size", defaultValue = "20") int size,
            @RequestHeader(value = "X-User-Id", required = false) String currentUserIdHeader,
            @RequestHeader(value = "X-User-Role", required = false) String currentUserRole) {
        // 教师公告列表仅允许教师本人或管理员访问
        Long currentUserId = parseUserId(currentUserIdHeader);
        if (!canManageTeacherAnnouncement(teacherId, currentUserId, currentUserRole)) {
            return Result.failure(403, "权限不足，仅教师本人或管理员可查看教师公告");
        }
        Map<String, Object> result = announcementService.findByTeacher(teacherId, courseId, status, page, size);
        return Result.success(result);
    }

    /**
     * 获取公告阅读审计统计。
     * 返回总阅读人数（UV）及各受众群体的渗透率。
    */
    @GetMapping("/{id}/stats")
    public Result<AnnouncementStatsDTO> getAnnouncementStats(
            @PathVariable("id") Long id,
            @RequestHeader(value = "X-User-Role", required = false) String currentUserRole) {
        // 阅读统计属于审计类信息，仅允许管理员访问
        if (!isAdminRole(currentUserRole)) {
            return Result.failure(403, "权限不足，仅管理员可查看公告统计");
        }
        AnnouncementStatsDTO stats = announcementService.getAnnouncementStats(id);
        return Result.success(stats);
    }

    /**
     * 记录阅读回执。
     * 用户点击公告后触发，用于防重复统计及状态标识。
     */
    @PostMapping("/{id}/read")
    public Result<Void> recordRead(
            @PathVariable("id") Long id,
            @RequestParam(value = "userId", required = false) Long userId,
            @RequestHeader(value = "X-User-Id", required = false) String currentUserIdHeader,
            @RequestHeader(value = "X-User-Role", required = false) String currentUserRole) {
        // 公告阅读回执默认以网关注入身份为准，避免前端伪造 userId
        Long currentUserId = parseUserId(currentUserIdHeader);
        if (currentUserId == null && !isAdminRole(currentUserRole)) {
            return Result.failure(401, "身份无效，请重新登录后再试");
        }

        Long effectiveUserId;
        if (isAdminRole(currentUserRole) && userId != null) {
            effectiveUserId = userId;
        } else {
            if (userId != null && currentUserId != null && !userId.equals(currentUserId)) {
                return Result.failure(403, "权限不足，不能为其他用户记录阅读状态");
            }
            effectiveUserId = currentUserId;
        }

        if (effectiveUserId == null) {
            return Result.failure(400, "缺少有效的用户标识");
        }

        announcementService.recordRead(id, effectiveUserId);
        return Result.success("阅读回执已确认", null);
    }

    /**
     * 弹性切换公告置顶状态。
     * 仅置顶状态的变更，不修改公告正文。
     */
    @PostMapping("/teachers/{teacherId}/{announcementId}/toggle-pin")
    public Result<AnnouncementVO> togglePin(
            @PathVariable("teacherId") Long teacherId,
            @PathVariable("announcementId") Long announcementId,
            @RequestHeader(value = "X-User-Id", required = false) String currentUserIdHeader,
            @RequestHeader(value = "X-User-Role", required = false) String currentUserRole) {
        // 置顶操作仅允许教师本人或管理员执行
        Long currentUserId = parseUserId(currentUserIdHeader);
        if (!canManageTeacherAnnouncement(teacherId, currentUserId, currentUserRole)) {
            return Result.failure(403, "权限不足，仅教师本人或管理员可置顶公告");
        }
        Announcement updated = announcementService.togglePin(teacherId, announcementId);
        String message = updated.getIsPinned() ? "公告已锁定至主页置顶" : "已从首屏推荐移除";
        return Result.success(message, announcementService.convertToVO(updated));
    }

    /**
     * 构造公告实体
     * 说明：仅用于系统级公告的创建与更新，避免控制层直接接收实体。
     *
     * @param request 公告请求数据
     * @return 公告实体
     */
    private Announcement buildAnnouncementEntity(AnnouncementRequestDTO request) {
        Announcement announcement = new Announcement();
        if (request == null) {
            return announcement;
        }
        BeanUtils.copyProperties(request, announcement);
        return announcement;
    }

    /**
     * 解析网关注入的用户ID，非法值返回 null。
     */
    private Long parseUserId(String currentUserIdHeader) {
        if (currentUserIdHeader == null || currentUserIdHeader.isBlank()) {
            return null;
        }
        try {
            return Long.parseLong(currentUserIdHeader);
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    /**
     * 判断是否为管理员角色。
     */
    private boolean isAdminRole(String role) {
        return role != null && "admin".equalsIgnoreCase(role);
    }

    /**
     * 判断是否允许管理教师公告：管理员可跨教师操作，教师仅允许操作本人数据。
     */
    private boolean canManageTeacherAnnouncement(Long targetTeacherId, Long currentUserId, String currentUserRole) {
        if (isAdminRole(currentUserRole)) {
            return true;
        }
        return currentUserRole != null
                && "teacher".equalsIgnoreCase(currentUserRole)
                && currentUserId != null
                && currentUserId.equals(targetTeacherId);
    }
}
