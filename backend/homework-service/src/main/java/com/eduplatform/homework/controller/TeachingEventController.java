package com.eduplatform.homework.controller;

import com.eduplatform.common.result.Result;
import com.eduplatform.homework.dto.TeachingEventDTO;
import com.eduplatform.homework.service.TeachingEventService;
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
            @RequestParam int month) {
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
            @RequestParam String weekStart) {
        // 处理日期格式：如果只有日期部分，添加时间部分
        LocalDateTime start;
        if (weekStart.contains("T")) {
            start = LocalDateTime.parse(weekStart);
        } else {
            start = LocalDateTime.parse(weekStart + "T00:00:00");
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
            @RequestParam String date) {
        LocalDateTime start = LocalDateTime.parse(date + "T00:00:00");
        List<TeachingEventDTO> events = eventService.getEventsByDay(teacherId, start);
        return Result.success(events);
    }

    /**
     * 创建教学事件。
     */
    @PostMapping("/events")
    public Result<TeachingEventDTO> createEvent(@RequestBody TeachingEventDTO dto) {
        TeachingEventDTO created = eventService.createEvent(dto);
        return Result.success("事件创建成功", created);
    }

    /**
     * 更新教学事件。
     */
    @PutMapping("/events/{id}")
    public Result<TeachingEventDTO> updateEvent(
            @PathVariable Long id,
            @RequestBody TeachingEventDTO dto) {
        TeachingEventDTO updated = eventService.updateEvent(id, dto);
        return Result.success("事件更新成功", updated);
    }

    /**
     * 删除教学事件。
     */
    @DeleteMapping("/events/{id}")
    public Result<Void> deleteEvent(@PathVariable Long id) {
        eventService.deleteEvent(id);
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
            @RequestParam int month) {
        String ical = eventService.exportToICal(teacherId, year, month);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType("text/calendar"));
        headers.setContentDispositionFormData("attachment", "calendar.ics");
        return ResponseEntity.ok().headers(headers).body(ical.getBytes());
    }
}
