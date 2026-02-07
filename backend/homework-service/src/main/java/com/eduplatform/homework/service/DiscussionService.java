package com.eduplatform.homework.service;

import com.eduplatform.homework.dto.DiscussionDTO;
import com.eduplatform.homework.dto.DiscussionGroupDTO;
import com.eduplatform.homework.dto.DiscussionStatsDTO;
import com.eduplatform.homework.entity.SubjectiveComment;
import com.eduplatform.homework.mapper.SubjectiveCommentMapper;
import com.eduplatform.homework.vo.SubjectiveCommentVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class DiscussionService {
    
    private final SubjectiveCommentMapper commentMapper;
    
    /**
     * 获取教师的所有讨论（按课程/章节分组）
     */
    public List<DiscussionGroupDTO> getGroupedDiscussions(Long teacherId) {
        try {
            List<Map<String, Object>> rawData = commentMapper.findByTeacher(teacherId);
            
            // 转换为DTO
            List<DiscussionDTO> discussions = rawData.stream()
                .map(this::mapToDiscussionDTO)
                .collect(Collectors.toList());
            
            // 按课程和章节分组
            Map<String, List<DiscussionDTO>> grouped = discussions.stream()
                .collect(Collectors.groupingBy(d -> d.getCourseId() + "_" + (d.getChapterId() != null ? d.getChapterId() : 0)));
            
            List<DiscussionGroupDTO> result = new ArrayList<>();
            for (Map.Entry<String, List<DiscussionDTO>> entry : grouped.entrySet()) {
                List<DiscussionDTO> groupDiscussions = entry.getValue();
                if (groupDiscussions.isEmpty()) continue;
                
                DiscussionDTO first = groupDiscussions.get(0);
                DiscussionGroupDTO group = new DiscussionGroupDTO();
                group.setCourseId(first.getCourseId());
                group.setCourseTitle(first.getCourseTitle());
                group.setChapterId(first.getChapterId());
                group.setChapterTitle(first.getChapterTitle());
                group.setTotalCount(groupDiscussions.size());
                group.setPendingCount((int) groupDiscussions.stream().filter(d -> "pending".equals(d.getAnswerStatus())).count());
                group.setAnsweredCount((int) groupDiscussions.stream().filter(d -> "answered".equals(d.getAnswerStatus())).count());
                group.setFollowUpCount((int) groupDiscussions.stream().filter(d -> "follow_up".equals(d.getAnswerStatus())).count());
                group.setOverdueCount((int) groupDiscussions.stream().filter(d -> Boolean.TRUE.equals(d.getIsOverdue())).count());
                group.setDiscussions(groupDiscussions);
                result.add(group);
            }
            
            // 按待处理数量排序
            result.sort((a, b) -> b.getPendingCount() - a.getPendingCount());
            return result;
        } catch (Exception e) {
            log.warn("获取讨论列表失败（表可能不存在）: {}", e.getMessage());
            return new ArrayList<>();
        }
    }
    
    /**
     * 获取讨论统计
     */
    public DiscussionStatsDTO getStats(Long teacherId) {
        try {
            Map<String, Object> stats = commentMapper.getStats(teacherId);
            DiscussionStatsDTO dto = new DiscussionStatsDTO();
            if (stats != null) {
                // MyBatis 使用驼峰命名转换
                dto.setTotalQuestions(getIntValue(stats, "totalQuestions"));
                dto.setPendingCount(getIntValue(stats, "pendingCount"));
                dto.setAnsweredCount(getIntValue(stats, "answeredCount"));
                dto.setFollowUpCount(getIntValue(stats, "followUpCount"));
                dto.setOverdueCount(getIntValue(stats, "overdueCount"));
            }
            return dto;
        } catch (Exception e) {
            log.warn("获取讨论统计失败（表可能不存在）: {}", e.getMessage());
            return new DiscussionStatsDTO();
        }
    }
    
    /**
     * 按课程获取讨论
     */
    public List<DiscussionDTO> getByCourse(Long courseId) {
        try {
            List<Map<String, Object>> rawData = commentMapper.findByCourse(courseId);
            return rawData.stream()
                .map(this::mapToDiscussionDTO)
                .collect(Collectors.toList());
        } catch (Exception e) {
            log.warn("获取课程讨论失败（表可能不存在）: {}", e.getMessage());
            return new ArrayList<>();
        }
    }
    
    /**
     * 更新回答状态
     */
    @Transactional
    public void updateAnswerStatus(Long id, String status, Long answeredBy) {
        commentMapper.updateAnswerStatus(id, status, answeredBy);
    }
    
    /**
     * 切换置顶状态
     */
    @Transactional
    public void toggleTop(Long id) {
        commentMapper.toggleTop(id);
    }
    
    /**
     * 回复讨论
     */
    @Transactional
    public SubjectiveCommentVO reply(Long parentId, Long userId, String content, Long courseId, Long chapterId) {
        // 获取父评论的 question_id
        SubjectiveComment parent = commentMapper.selectById(parentId);
        Long questionId = parent != null ? parent.getQuestionId() : 1L; // 默认值为1
        
        SubjectiveComment comment = new SubjectiveComment();
        comment.setQuestionId(questionId);
        comment.setParentId(parentId);
        comment.setUserId(userId);
        comment.setContent(content);
        comment.setCourseId(courseId);
        comment.setChapterId(chapterId);
        comment.setIsAnswer(0);
        comment.setIsTop(0);
        comment.setLikeCount(0);
        comment.setStatus(1);
        comment.setAnswerStatus("answered");
        comment.setCreatedAt(LocalDateTime.now());
        commentMapper.insert(comment);
        
        // 更新父评论状态为已回答
        commentMapper.updateAnswerStatus(parentId, "answered", userId);
        
        return convertToCommentVO(comment);
    }
    
    /**
     * 获取回复列表
     */
    public List<DiscussionDTO> getReplies(Long parentId) {
        List<Map<String, Object>> rawData = commentMapper.findReplies(parentId);
        return rawData.stream()
            .map(this::mapToReplyDTO)
            .collect(Collectors.toList());
    }
    
    private DiscussionDTO mapToDiscussionDTO(Map<String, Object> data) {
        DiscussionDTO dto = new DiscussionDTO();
        // MyBatis 使用驼峰命名转换，所以键是驼峰格式
        dto.setId(getLongValue(data, "id"));
        dto.setQuestionId(getLongValue(data, "questionId"));
        dto.setUserId(getLongValue(data, "userId"));
        dto.setUserName(getStringValue(data, "userName"));
        dto.setUserAvatar(getStringValue(data, "userAvatar"));
        dto.setParentId(getLongValue(data, "parentId"));
        dto.setContent(getStringValue(data, "content"));
        dto.setIsAnswer(getIntValue(data, "isAnswer"));
        dto.setIsTop(getIntValue(data, "isTop"));
        dto.setLikeCount(getIntValue(data, "likeCount"));
        dto.setAnswerStatus(getStringValue(data, "answerStatus"));
        dto.setAnsweredAt(getDateTimeValue(data, "answeredAt"));
        dto.setAnsweredBy(getLongValue(data, "answeredBy"));
        dto.setCourseId(getLongValue(data, "courseId"));
        dto.setCourseTitle(getStringValue(data, "courseTitle"));
        dto.setChapterId(getLongValue(data, "chapterId"));
        dto.setChapterTitle(getStringValue(data, "chapterTitle"));
        dto.setCreatedAt(getDateTimeValue(data, "createdAt"));
        dto.setReplyCount(getIntValue(data, "replyCount"));
        
        // 计算是否超过48小时未回复
        if ("pending".equals(dto.getAnswerStatus()) && dto.getCreatedAt() != null) {
            long hours = ChronoUnit.HOURS.between(dto.getCreatedAt(), LocalDateTime.now());
            dto.setIsOverdue(hours > 48);
            dto.setResponseTimeHours(hours);
        } else {
            dto.setIsOverdue(false);
        }
        
        return dto;
    }

    /**
     * 将评论实体转换为视图对象
     * 用于对外输出，避免直接暴露持久层数据。
     *
     * @param comment 评论实体
     * @return 评论视图对象
     */
    private SubjectiveCommentVO convertToCommentVO(SubjectiveComment comment) {
        if (comment == null) {
            return null;
        }
        SubjectiveCommentVO vo = new SubjectiveCommentVO();
        BeanUtils.copyProperties(comment, vo);
        return vo;
    }
    
    private DiscussionDTO mapToReplyDTO(Map<String, Object> data) {
        DiscussionDTO dto = new DiscussionDTO();
        dto.setId(getLongValue(data, "id"));
        dto.setUserId(getLongValue(data, "userId"));
        dto.setUserName(getStringValue(data, "userName"));
        dto.setUserAvatar(getStringValue(data, "userAvatar"));
        dto.setParentId(getLongValue(data, "parentId"));
        dto.setContent(getStringValue(data, "content"));
        dto.setCreatedAt(getDateTimeValue(data, "createdAt"));
        return dto;
    }
    
    private Long getLongValue(Map<String, Object> data, String key) {
        Object value = data.get(key);
        if (value == null) return null;
        if (value instanceof Long) return (Long) value;
        if (value instanceof Integer) return ((Integer) value).longValue();
        return null;
    }
    
    private Integer getIntValue(Map<String, Object> data, String key) {
        Object value = data.get(key);
        if (value == null) return 0;
        if (value instanceof Integer) return (Integer) value;
        if (value instanceof Long) return ((Long) value).intValue();
        if (value instanceof java.math.BigDecimal) return ((java.math.BigDecimal) value).intValue();
        if (value instanceof java.math.BigInteger) return ((java.math.BigInteger) value).intValue();
        if (value instanceof Number) return ((Number) value).intValue();
        return 0;
    }
    
    private String getStringValue(Map<String, Object> data, String key) {
        Object value = data.get(key);
        if (value == null) return null;
        return value.toString();
    }
    
    private LocalDateTime getDateTimeValue(Map<String, Object> data, String key) {
        Object value = data.get(key);
        if (value == null) return null;
        if (value instanceof LocalDateTime) return (LocalDateTime) value;
        if (value instanceof java.sql.Timestamp) return ((java.sql.Timestamp) value).toLocalDateTime();
        return null;
    }
}
