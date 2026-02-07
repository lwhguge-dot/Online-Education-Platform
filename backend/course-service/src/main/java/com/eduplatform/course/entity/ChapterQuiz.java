package com.eduplatform.course.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 章节随堂小测实体类
 * 对应数据库表 `chapter_quizzes`，用于存储章节末尾的即时测试题。
 */
@Data
@TableName("chapter_quizzes")
public class ChapterQuiz {
    /**
     * 测试题唯一标识
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 关联的章节 ID
     */
    private Long chapterId;

    /**
     * 题目正文
     */
    private String question;

    /**
     * 题目类型 (例如：SINGLE_CHOICE-单选, MULTIPLE_CHOICE-多选)
     */
    private String questionType;

    /**
     * 备选项 JSON 列表 (例如：["A: xx", "B: xx"])
     */
    private String options;

    /**
     * 标准答案 (单选为选项索引或文本，多选为英文逗号分隔符)
     */
    private String correctAnswer;

    /**
     * 该题目的分值权重
     */
    private Integer score;

    /**
     * 题目在章节测试中的显示顺序
     */
    private Integer sortOrder;

    /**
     * 题目录入时间
     */
    @TableField("created_at")
    private LocalDateTime createdAt;
}
