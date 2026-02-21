package com.eduplatform.course.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 课程审核请求。
 */
@Data
public class CourseAuditRequest {

    /**
     * 审核动作（如 approve/reject）。
     */
    @NotBlank(message = "action不能为空")
    @Size(max = 20, message = "action长度不能超过20")
    private String action;

    /**
     * 审核备注。
     */
    @Size(max = 1000, message = "remark长度不能超过1000")
    private String remark;

    /**
     * 审核人ID（内部调用可使用）。
     */
    private Long auditBy;
}
