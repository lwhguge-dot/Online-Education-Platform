package com.eduplatform.homework.controller;

import com.eduplatform.common.result.Result;
import com.eduplatform.homework.dto.TeachingEventDTO;
import com.eduplatform.homework.service.TeachingEventService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 教学日历控制器。
 * 设计意图：为教师提供课程事件管理与日历导出能力。
 */
@RestController
@RequestMapping("/api/calendar")
@RequiredArgsConstructor
public class TeachingEventController {

    private final TeachingEventService eventService;

    /**
     * 按月获取教师日历事件。
     */
    @GetMapping("/teacher/{teacherId}/month")
    public Result<List<TeachingEventDTO>> getByMonth(
            @PathVariable Long teacherId,
            @RequestParam int year,
            @RequestParam int month,
            @RequestHeader(value = "X-User-Id", required = false) String currentUserIdHeader,
            @RequestHeader(value = "X-User-Role", required = false) String currentUserRole) {
        // 教学日历查询仅允许教师本人或管理员
        Long currentUserId = parseUserId(currentUserIdHeader);
        if (!canAccessTeacherData(teacherId, currentUserId, currentUserRole)) {
            return Result.failure(403, "权限不足，仅教师本人或管理员可查看教学日历");
        }

        List<TeachingEventDTO> events = eventService.getEventsByMonth(teacherId, year, month);
        return Result.success(events);
    }

    /**
     * 按周获取教师日历事件。
     * 说明：若缺少时间部分则补齐到当天 00:00:00。
     */
    @GetMapping("/teacher/{teacherId}/week")
    public Result<List<TeachingEventDTO>> getByWeek(
            @PathVariable Long teacherId,
            @RequestParam(name = "weekStart", required = false) String weekStart,
            @RequestParam(name = "startDate", required = false) String startDate,
            @RequestHeader(value = "X-User-Id", required = false) String currentUserIdHeader,
            @RequestHeader(value = "X-User-Role", required = false) String currentUserRole) {
        // 教学日历查询仅允许教师本人或管理员
        Long currentUserId = parseUserId(currentUserIdHeader);
        if (!canAccessTeacherData(teacherId, currentUserId, currentUserRole)) {
            return Result.failure(403, "权限不足，仅教师本人或管理员可查看教学日历");
        }

        // 兼容旧参数 startDate 和新参数 weekStart，优先使用 weekStart
        String effectiveWeekStart = weekStart != null ? weekStart : startDate;
        if (effectiveWeekStart == null || effectiveWeekStart.isBlank()) {
            return Result.failure(400, "缺少 weekStart 或 startDate 参数");
        }

        // 处理日期格式：如果只有日期部分，添加时间部分
        LocalDateTime start;
        if (effectiveWeekStart.contains("T")) {
            start = LocalDateTime.parse(effectiveWeekStart);
        } else {
            start = LocalDateTime.parse(effectiveWeekStart + "T00:00:00");
        }
        List<TeachingEventDTO> events = eventService.getEventsByWeek(teacherId, start);
        return Result.success(events);
    }

    /**
     * 按日获取教师日历事件。
     */
    @GetMapping("/teacher/{teacherId}/day")
    public Result<List<TeachingEventDTO>> getByDay(
            @PathVariable Long teacherId,
            @RequestParam String date,
            @RequestHeader(value = "X-User-Id", required = false) String currentUserIdHeader,
            @RequestHeader(value = "X-User-Role", required = false) String currentUserRole) {
        // 教学日历查询仅允许教师本人或管理员
        Long currentUserId = parseUserId(currentUserIdHeader);
        if (!canAccessTeacherData(teacherId, currentUserId, currentUserRole)) {
            return Result.failure(403, "权限不足，仅教师本人或管理员可查看教学日历");
        }

        LocalDateTime start = LocalDateTime.parse(date + "T00:00:00");
        List<TeachingEventDTO> events = eventService.getEventsByDay(teacherId, start);
        return Result.success(events);
    }

    /**
     * 创建教学事件。
     */
    @PostMapping("/events")
    public Result<TeachingEventDTO> createEvent(
            @Valid @RequestBody TeachingEventDTO dto,
            @RequestHeader(value = "X-User-Id", required = false) String currentUserIdHeader,
            @RequestHeader(value = "X-User-Role", required = false) String currentUserRole) {
        // 创建教学事件时以网关身份为准，禁止伪造 teacherId
        Long currentUserId = parseUserId(currentUserIdHeader);
        Long targetTeacherId = dto.getTeacherId() != null ? dto.getTeacherId() : currentUserId;
        if (!canAccessTeacherData(targetTeacherId, currentUserId, currentUserRole)) {
            return Result.failure(403, "权限不足，仅教师本人或管理员可创建教学事件");
        }

        if (!isAdminRole(currentUserRole)) {
            dto.setTeacherId(currentUserId);
        }

        TeachingEventDTO created = eventService.createEvent(dto);
        return Result.success("事件创建成功", created);
    }

    /**
     * 更新教学事件。
     */
    @PutMapping("/events/{id}")
    public Result<TeachingEventDTO> updateEvent(
            @PathVariable Long id,
            @Valid @RequestBody TeachingEventDTO dto,
            @RequestHeader(value = "X-User-Id", required = false) String currentUserIdHeader,
            @RequestHeader(value = "X-User-Role", required = false) String currentUserRole) {
        // 更新教学事件时同样校验教师身份并覆盖 teacherId
        Long currentUserId = parseUserId(currentUserIdHeader);
        Long targetTeacherId = dto.getTeacherId() != null ? dto.getTeacherId() : currentUserId;
        if (!canAccessTeacherData(targetTeacherId, currentUserId, currentUserRole)) {
            return Result.failure(403, "权限不足，仅教师本人或管理员可更新教学事件");
        }

        if (!isAdminRole(currentUserRole)) {
            dto.setTeacherId(currentUserId);
        }

        TeachingEventDTO updated = eventService.updateEvent(id, dto, currentUserId, isAdminRole(currentUserRole));
        return Result.success("事件更新成功", updated);
    }

    /**
     * 删除教学事件。
     */
    @DeleteMapping("/events/{id}")
    public Result<Void> deleteEvent(
            @PathVariable Long id,
            @RequestParam(name = "teacherId", required = false) Long teacherId,
            @RequestHeader(value = "X-User-Id", required = false) String currentUserIdHeader,
            @RequestHeader(value = "X-User-Role", required = false) String currentUserRole) {
        // 删除事件要求提供 teacherId 做权限校验，防止按ID越权删除
        Long currentUserId = parseUserId(currentUserIdHeader);
        if (teacherId == null) {
            return Result.failure(400, "缺少 teacherId 参数");
        }
        if (!canAccessTeacherData(teacherId, currentUserId, currentUserRole)) {
            return Result.failure(403, "权限不足，仅教师本人或管理员可删除教学事件");
        }

        eventService.deleteEvent(id, currentUserId, isAdminRole(currentUserRole));
        return Result.success("事件已删除", null);
    }

    /**
     * 导出 iCal 日历文件。
     * 说明：用于同步到第三方日历工具。
     */
    @GetMapping("/teacher/{teacherId}/export")
    public ResponseEntity<byte[]> exportICal(
            @PathVariable Long teacherId,
            @RequestParam int year,
            @RequestParam int month,
            @RequestHeader(value = "X-User-Id", required = false) String currentUserIdHeader,
            @RequestHeader(value = "X-User-Role", required = false) String currentUserRole) {
        // 导出日历文件仅允许教师本人或管理员
        Long currentUserId = parseUserId(currentUserIdHeader);
        if (!canAccessTeacherData(teacherId, currentUserId, currentUserRole)) {
            return ResponseEntity.status(403).build();
        }

        String ical = eventService.exportToICal(teacherId, year, month);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType("text/calendar"));
        headers.setContentDispositionFormData("attachment", "calendar.ics");
        return ResponseEntity.ok().headers(headers).body(ical.getBytes());
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
     * 教师数据访问控制：管理员可跨账号访问，教师仅可访问本人数据。
     */
    private boolean canAccessTeacherData(Long targetTeacherId, Long currentUserId, String currentUserRole) {
        if (isAdminRole(currentUserRole)) {
            return true;
        }
        return targetTeacherId != null
                && currentUserRole != null
                && "teacher".equalsIgnoreCase(currentUserRole)
                && currentUserId != null
                && currentUserId.equals(targetTeacherId);
    }
}
