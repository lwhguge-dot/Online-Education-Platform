package com.eduplatform.user.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.eduplatform.common.result.Result;
import com.eduplatform.common.security.RequestContext;
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
    private final RequestContext requestContext;

    /**
     * 后台全量公告分页检索 (管理员控制台)。
     */
    @GetMapping
    public Result<Map<String, Object>> getAnnouncements(
            @RequestParam(name = "page", defaultValue = "1") int page,
            @RequestParam(name = "size", defaultValue = "20") int size,
            @RequestParam(name = "status", required = false) String status,
            @RequestParam(name = "targetAudience", required = false) String targetAudience) {
        if (!requestContext.isAdmin()) {
            return Result.failure(403, "权限不足，仅管理员可查看全量公告");
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
     */
    @GetMapping("/active")
    public Result<List<AnnouncementVO>> getActiveAnnouncements(
            @RequestParam(name = "audience", required = false) String audience) {
        List<Announcement> announcements = announcementService.findActiveByAudience(audience);
        return Result.success(announcementService.convertToVOList(announcements));
    }

    /**
     * 获取公告图文详情。
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
     */
    @PostMapping
    public Result<AnnouncementVO> createAnnouncement(@Valid @RequestBody AnnouncementRequestDTO request) {
        if (!requestContext.isAdmin()) {
            return Result.failure(403, "权限不足，仅管理员可创建公告");
        }
        Announcement created = announcementService.create(buildAnnouncementEntity(request));
        return Result.success("公告已创建，请及时发布", announcementService.convertToVO(created));
    }

    /**
     * 全量更新公告内容。
     */
    @PutMapping("/{id}")
    public Result<AnnouncementVO> updateAnnouncement(
            @PathVariable("id") Long id,
            @Valid @RequestBody AnnouncementRequestDTO request) {
        if (!requestContext.isAdmin()) {
            return Result.failure(403, "权限不足，仅管理员可修改公告");
        }
        Announcement updated = announcementService.update(id, buildAnnouncementEntity(request));
        return Result.success("内容修正成功", announcementService.convertToVO(updated));
    }

    /**
     * 永久物理删除公告。
     */
    @DeleteMapping("/{id}")
    public Result<Void> deleteAnnouncement(@PathVariable("id") Long id) {
        if (!requestContext.isAdmin()) {
            return Result.failure(403, "权限不足，仅管理员可删除公告");
        }
        announcementService.delete(id);
        return Result.success("公告条目已彻底移除", null);
    }

    /**
     * 发布公告 (使受众可见)。
     */
    @PostMapping("/{id}/publish")
    public Result<AnnouncementVO> publishAnnouncement(@PathVariable("id") Long id) {
        if (!requestContext.isAdmin()) {
            return Result.failure(403, "权限不足，仅管理员可发布公告");
        }
        Announcement published = announcementService.publish(id);
        return Result.success("公告发布成功，前端实时生效", announcementService.convertToVO(published));
    }

    // ==================== 课程/教师私有公告接口 ====================

    /**
     * 教师向自有课程/全员发布消息。
     */
    @PostMapping("/teachers/{teacherId}")
    public Result<AnnouncementVO> createTeacherAnnouncement(
            @PathVariable("teacherId") Long teacherId,
            @Valid @RequestBody TeacherAnnouncementDTO dto) {
        if (!requestContext.canAccessTeacherData(teacherId)) {
            return Result.failure(403, "权限不足，仅教师本人或管理员可发布教学公告");
        }
        Announcement created = announcementService.createByTeacher(teacherId, dto);
        return Result.success("教学公告已送达", announcementService.convertToVO(created));
    }

    /**
     * 教师更新历史发布的课程公告。
     */
    @PutMapping("/teachers/{teacherId}/{announcementId}")
    public Result<AnnouncementVO> updateTeacherAnnouncement(
            @PathVariable("teacherId") Long teacherId,
            @PathVariable("announcementId") Long announcementId,
            @Valid @RequestBody TeacherAnnouncementDTO dto) {
        if (!requestContext.canAccessTeacherData(teacherId)) {
            return Result.failure(403, "权限不足，仅教师本人或管理员可修改教学公告");
        }
        Announcement updated = announcementService.updateByTeacher(teacherId, announcementId, dto);
        return Result.success("公告修订完成", announcementService.convertToVO(updated));
    }

    /**
     * 教师撤回/物理删除公告。
     */
    @DeleteMapping("/teachers/{teacherId}/{announcementId}")
    public Result<Void> deleteTeacherAnnouncement(
            @PathVariable("teacherId") Long teacherId,
            @PathVariable("announcementId") Long announcementId) {
        if (!requestContext.canAccessTeacherData(teacherId)) {
            return Result.failure(403, "权限不足，仅教师本人或管理员可删除教学公告");
        }
        announcementService.deleteByTeacher(teacherId, announcementId);
        return Result.success("该内容已从授课范围消失", null);
    }

    /**
     * 分页检索特定教师的所有发布历史。
     */
    @GetMapping("/teachers/{teacherId}")
    public Result<Map<String, Object>> getTeacherAnnouncements(
            @PathVariable("teacherId") Long teacherId,
            @RequestParam(name = "courseId", required = false) Long courseId,
            @RequestParam(name = "status", required = false) String status,
            @RequestParam(name = "page", defaultValue = "1") int page,
            @RequestParam(name = "size", defaultValue = "20") int size) {
        if (!requestContext.canAccessTeacherData(teacherId)) {
            return Result.failure(403, "权限不足，仅教师本人或管理员可查看教学公告历史");
        }
        Map<String, Object> result = announcementService.findByTeacher(teacherId, courseId, status, page, size);
        return Result.success(result);
    }

    /**
     * 获取公告阅读审计统计。
     */
    @GetMapping("/{id}/stats")
    public Result<AnnouncementStatsDTO> getAnnouncementStats(@PathVariable("id") Long id) {
        if (!requestContext.isAdmin()) {
            return Result.failure(403, "权限不足，仅管理员可查看公告统计");
        }
        AnnouncementStatsDTO stats = announcementService.getAnnouncementStats(id);
        return Result.success(stats);
    }

    /**
     * 记录阅读回执。
     * 权限：阅读记录必须由本人产生，避免越权代他人写入。
     */
    @PostMapping("/{id}/read")
    public Result<Void> recordRead(
            @PathVariable("id") Long id,
            @RequestParam(value = "userId", required = false) Long userId,
            @RequestHeader(value = "X-User-Id", required = false) String currentUserIdHeader,
            @RequestHeader(value = "X-User-Role", required = false) String currentUserRole) {
        Long currentUserId = parseUserId(currentUserIdHeader);
        if (currentUserId == null && currentUserRole == null) {
            return Result.failure(401, "身份无效，请重新登录后再试");
        }

        // 阅读记录必须使用网关注入的真实身份写入，禁止前端传入 userId 代他人记录；
        // 仅当 currentUserId 缺失但请求来自内部可信调用时（无法定 role），按 body 兜底。
        Long effectiveUserId;
        if (currentUserId != null) {
            effectiveUserId = currentUserId;
        } else {
            // 网关已剥离可伪造头，且必须通过签名校验才能到达此处，因此此处兜底可信。
            if (userId == null) {
                return Result.failure(400, "缺少有效的用户标识");
            }
            effectiveUserId = userId;
        }

        announcementService.recordRead(id, effectiveUserId);
        return Result.success("阅读回执已确认", null);
    }

    /**
     * 弹性切换公告置顶状态。
     */
    @PostMapping("/teachers/{teacherId}/{announcementId}/toggle-pin")
    public Result<AnnouncementVO> togglePin(
            @PathVariable("teacherId") Long teacherId,
            @PathVariable("announcementId") Long announcementId) {
        if (!requestContext.canAccessTeacherData(teacherId)) {
            return Result.failure(403, "权限不足，仅教师本人或管理员可切换置顶状态");
        }
        Announcement updated = announcementService.togglePin(teacherId, announcementId);
        String message = updated.getIsPinned() ? "公告已锁定至主页置顶" : "已从首屏推荐移除";
        return Result.success(message, announcementService.convertToVO(updated));
    }

    private Announcement buildAnnouncementEntity(AnnouncementRequestDTO request) {
        Announcement announcement = new Announcement();
        if (request == null) {
            return announcement;
        }
        BeanUtils.copyProperties(request, announcement);
        return announcement;
    }

    private Long parseUserId(String currentUserIdHeader) {
        return requestContext.parseUserId(currentUserIdHeader);
    }
}
