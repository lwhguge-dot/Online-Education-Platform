package com.eduplatform.course.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 课程核心实体类
 * 对应数据库表 `courses`，承载课程的元数据、教学画像及全生命周期状态。
 * 该实体是教务系统的核心，关联了章节(Chapter)、选课(Enrollment)及教师画像。
 * 
 * 生命周期流程：DRAFT (新建) -> REVIEWING (提审) -> PUBLISHED (发布) / REJECTED (驳回)。
 *
 * @author Antigravity
 */
@Data
@TableName("courses")
public class Course {

    /** 初始草稿状态：仅教师本人可见，可随意修改内容 */
    public static final String STATUS_DRAFT = "DRAFT";
    /** 审核中状态：内容已锁定，等待教导主任或管理员合规性审查 */
    public static final String STATUS_REVIEWING = "REVIEWING";
    /** 驳回状态：审核未通过，教师需根据 audit_remark 调整后重新提审 */
    public static final String STATUS_REJECTED = "REJECTED";
    /** 发布状态：全站学生可见，允许进行选课与学习 */
    public static final String STATUS_PUBLISHED = "PUBLISHED";
    /** 下架状态：课程暂时隐藏，已选课学生可继续学习，新学生不可选课 */
    public static final String STATUS_OFFLINE = "OFFLINE";
    /** 封禁状态：因违规被强制关停，所有用户不可见 */
    public static final String STATUS_BANNED = "BANNED";

    /**
     * 课程唯一标识
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 课程标题 (展示在列表及搜索页的主名称)
     */
    private String title;

    /**
     * 课程营销描述或教学大纲 (支持富文本存储)
     */
    private String description;

    /**
     * 业务分类/学科标签 (例如：人工智能、高等数学)
     */
    private String subject;

    /**
     * 课程封面图访问链接 (CDN 绝对路径或相对路径)
     */
    @TableField("cover_image")
    private String coverImage;

    /**
     * 外键：归属教师 ID
     */
    @TableField("teacher_id")
    private Long teacherId;

    /**
     * 教师名称冗余字段 (减少分页查询时的多表关联开销)
     */
    @TableField("teacher_name")
    private String teacherName;

    /**
     * 课程综合评分 (来源于学生评价的加权平均值，范围 0.0-5.0)
     */
    private Double rating;

    /**
     * 累计报名学生总数 (计数器字段，随 Enrollment 自动增减)
     */
    @TableField("student_count")
    private Integer studentCount;

    /**
     * 课程当前生命周期状态
     * 
     * @see Course#STATUS_DRAFT
     * @see Course#STATUS_PUBLISHED
     */
    private String status;

    /**
     * 发起审核动作的物理时间
     */
    @TableField("submit_time")
    private LocalDateTime submitTime;

    /**
     * 执行最后一次审核动作的管理员 ID
     */
    @TableField("audit_by")
    private Long auditBy;

    /**
     * 审核完成时间
     */
    @TableField("audit_time")
    private LocalDateTime auditTime;

    /**
     * 审核不通过时的改进意见或内部备注
     */
    @TableField("audit_remark")
    private String auditRemark;

    /**
     * 数据行创建时间 (自动填充)
     */
    @TableField("created_at")
    private LocalDateTime createdAt;

    /**
     * 数据行最后修改时间 (自动填充)
     */
    @TableField("updated_at")
    private LocalDateTime updatedAt;

    /**
     * 逻辑字段：统计该课程下的有效章节总数 (不映射数据库)
     */
    @TableField(exist = false)
    private Integer totalChapters;
}
