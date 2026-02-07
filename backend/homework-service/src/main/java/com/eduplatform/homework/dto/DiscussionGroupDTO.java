package com.eduplatform.homework.dto;

import lombok.Data;
import java.util.List;

@Data
public class DiscussionGroupDTO {
    private Long courseId;
    private String courseTitle;
    private Long chapterId;
    private String chapterTitle;
    private Integer totalCount;
    private Integer pendingCount;
    private Integer answeredCount;
    private Integer followUpCount;
    private Integer overdueCount;
    private List<DiscussionDTO> discussions;
}
