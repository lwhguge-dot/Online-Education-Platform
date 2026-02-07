package com.eduplatform.homework.service;

import com.eduplatform.homework.dto.TeachingEventDTO;
import com.eduplatform.homework.entity.TeachingEvent;
import com.eduplatform.homework.mapper.TeachingEventMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class TeachingEventService {
    
    private final TeachingEventMapper eventMapper;
    
    public List<TeachingEventDTO> getEventsByMonth(Long teacherId, int year, int month) {
        try {
            YearMonth ym = YearMonth.of(year, month);
            LocalDateTime start = ym.atDay(1).atStartOfDay();
            LocalDateTime end = ym.plusMonths(1).atDay(1).atStartOfDay();
            
            return eventMapper.findByTeacherAndDateRange(teacherId, start, end)
                .stream().map(this::toDTO).collect(Collectors.toList());
        } catch (Exception e) {
            log.warn("获取教学日历事件失败（表可能不存在）: {}", e.getMessage());
            return new ArrayList<>();
        }
    }
    
    public List<TeachingEventDTO> getEventsByWeek(Long teacherId, LocalDateTime weekStart) {
        try {
            LocalDateTime end = weekStart.plusDays(7);
            return eventMapper.findByTeacherAndDateRange(teacherId, weekStart, end)
                .stream().map(this::toDTO).collect(Collectors.toList());
        } catch (Exception e) {
            log.warn("获取教学日历事件失败（表可能不存在）: {}", e.getMessage());
            return new ArrayList<>();
        }
    }
    
    public List<TeachingEventDTO> getEventsByDay(Long teacherId, LocalDateTime dayStart) {
        try {
            LocalDateTime end = dayStart.plusDays(1);
            return eventMapper.findByTeacherAndDateRange(teacherId, dayStart, end)
                .stream().map(this::toDTO).collect(Collectors.toList());
        } catch (Exception e) {
            log.warn("获取教学日历事件失败（表可能不存在）: {}", e.getMessage());
            return new ArrayList<>();
        }
    }
    
    @Transactional
    public TeachingEventDTO createEvent(TeachingEventDTO dto) {
        TeachingEvent event = toEntity(dto);
        event.setStatus("active");
        event.setCreatedAt(LocalDateTime.now());
        eventMapper.insert(event);
        dto.setId(event.getId());
        return dto;
    }
    
    @Transactional
    public TeachingEventDTO updateEvent(Long id, TeachingEventDTO dto) {
        TeachingEvent event = eventMapper.selectById(id);
        if (event == null) return null;
        
        event.setTitle(dto.getTitle());
        event.setDescription(dto.getDescription());
        event.setEventType(dto.getEventType());
        event.setStartTime(dto.getStartTime());
        event.setEndTime(dto.getEndTime());
        event.setAllDay(dto.getAllDay() != null && dto.getAllDay() ? 1 : 0);
        event.setCourseId(dto.getCourseId());
        event.setColor(dto.getColor());
        event.setReminderMinutes(dto.getReminderMinutes());
        eventMapper.updateById(event);
        return toDTO(event);
    }
    
    @Transactional
    public void deleteEvent(Long id) {
        TeachingEvent event = eventMapper.selectById(id);
        if (event != null) {
            event.setStatus("cancelled");
            eventMapper.updateById(event);
        }
    }
    
    public String exportToICal(Long teacherId, int year, int month) {
        List<TeachingEventDTO> events = getEventsByMonth(teacherId, year, month);
        StringBuilder sb = new StringBuilder();
        sb.append("BEGIN:VCALENDAR\r\n");
        sb.append("VERSION:2.0\r\n");
        sb.append("PRODID:-//EduPlatform//Teaching Calendar//CN\r\n");
        
        for (TeachingEventDTO event : events) {
            sb.append("BEGIN:VEVENT\r\n");
            sb.append("UID:").append(event.getId()).append("@eduplatform\r\n");
            sb.append("DTSTART:").append(formatDateTime(event.getStartTime())).append("\r\n");
            if (event.getEndTime() != null) {
                sb.append("DTEND:").append(formatDateTime(event.getEndTime())).append("\r\n");
            }
            sb.append("SUMMARY:").append(event.getTitle()).append("\r\n");
            if (event.getDescription() != null) {
                sb.append("DESCRIPTION:").append(event.getDescription()).append("\r\n");
            }
            sb.append("END:VEVENT\r\n");
        }
        
        sb.append("END:VCALENDAR\r\n");
        return sb.toString();
    }
    
    private String formatDateTime(LocalDateTime dt) {
        return String.format("%04d%02d%02dT%02d%02d%02d",
            dt.getYear(), dt.getMonthValue(), dt.getDayOfMonth(),
            dt.getHour(), dt.getMinute(), dt.getSecond());
    }
    
    private TeachingEventDTO toDTO(TeachingEvent e) {
        TeachingEventDTO dto = new TeachingEventDTO();
        dto.setId(e.getId());
        dto.setTeacherId(e.getTeacherId());
        dto.setTitle(e.getTitle());
        dto.setDescription(e.getDescription());
        dto.setEventType(e.getEventType());
        dto.setStartTime(e.getStartTime());
        dto.setEndTime(e.getEndTime());
        dto.setAllDay(e.getAllDay() != null && e.getAllDay() == 1);
        dto.setCourseId(e.getCourseId());
        dto.setHomeworkId(e.getHomeworkId());
        dto.setColor(e.getColor());
        dto.setReminderMinutes(e.getReminderMinutes());
        dto.setIsRecurring(e.getIsRecurring() != null && e.getIsRecurring() == 1);
        dto.setRecurrenceRule(e.getRecurrenceRule());
        dto.setStatus(e.getStatus());
        return dto;
    }
    
    private TeachingEvent toEntity(TeachingEventDTO dto) {
        TeachingEvent e = new TeachingEvent();
        e.setTeacherId(dto.getTeacherId());
        e.setTitle(dto.getTitle());
        e.setDescription(dto.getDescription());
        e.setEventType(dto.getEventType());
        e.setStartTime(dto.getStartTime());
        e.setEndTime(dto.getEndTime());
        e.setAllDay(dto.getAllDay() != null && dto.getAllDay() ? 1 : 0);
        e.setCourseId(dto.getCourseId());
        e.setHomeworkId(dto.getHomeworkId());
        e.setColor(dto.getColor());
        e.setReminderMinutes(dto.getReminderMinutes());
        e.setIsRecurring(dto.getIsRecurring() != null && dto.getIsRecurring() ? 1 : 0);
        e.setRecurrenceRule(dto.getRecurrenceRule());
        return e;
    }
}
