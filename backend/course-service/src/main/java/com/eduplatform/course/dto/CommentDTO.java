package com.eduplatform.course.dto;

import lombok.Data;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 评论DTO
 */
@Data
public class CommentDTO {
    private Long id;
    private Long chapterId;
    private Long courseId;
    private Long userId;
    private String userName;
    private String userAvatar;
    private Long parentId;
    private String content;
    private Integer likeCount;
    private Integer replyCount;
    private Boolean isPinned;
    private Boolean isLiked;
    private LocalDateTime createdAt;
    private List<CommentDTO> replies;
}
