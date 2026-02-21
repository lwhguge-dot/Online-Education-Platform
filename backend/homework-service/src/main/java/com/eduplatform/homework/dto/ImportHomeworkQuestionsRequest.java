package com.eduplatform.homework.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

/**
 * 批量导入题目请求体。
 * 说明：仅承载题目列表，避免复用创建作业 DTO 导致契约过大。
 */
@Data
public class ImportHomeworkQuestionsRequest {

    /**
     * 待导入题目列表。
     */
    @NotEmpty(message = "questions不能为空")
    @Valid
    private List<HomeworkCreateDTO.QuestionDTO> questions;
}
