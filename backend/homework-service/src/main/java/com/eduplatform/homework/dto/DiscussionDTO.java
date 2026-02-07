package com.eduplatform.homework.dto;

import lombok.Data;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class DiscussionDTO {
    private Long id;
    private Long questionId;
    private Long userId;
    private String userName;
    private String userAvatar;
    private Long parentId;
    private String content;
    private Integer isAnswer;
    private Integer isTop;
    private Integer likeCount;
    private String answerStatus;
    private LocalDateTime answeredAt;
    private Long answeredBy;
    private String answeredByName;
    private Long courseId;
    private String courseTitle;
    private Long chapterId;
    private String chapterTitle;
    private LocalDateTime createdAt;
    private Integer replyCount;
    private List<DiscussionDTO> replies;
    
    // 是否超过48小时未回复
    private Boolean isOverdue;
    
    // 回复时间统计（小时）
    private Long responseTimeHours;
}
