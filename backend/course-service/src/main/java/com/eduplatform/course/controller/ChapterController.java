package com.eduplatform.course.controller;

import com.eduplatform.common.result.Result;
import com.eduplatform.course.dto.ChapterDTO;
import com.eduplatform.course.dto.ChapterQuizDTO;
import com.eduplatform.course.entity.Chapter;
import com.eduplatform.course.entity.ChapterQuiz;
import com.eduplatform.course.service.ChapterService;
import com.eduplatform.course.vo.ChapterQuizVO;
import com.eduplatform.course.vo.ChapterVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 章节控制器。
 * 设计意图：统一管理课程章节与测验题目，控制层仅接收 DTO 并输出 VO。
 */
@RestController
@RequestMapping("/api/chapters")
@RequiredArgsConstructor
@Slf4j
public class ChapterController {

    private final ChapterService chapterService;

    /**
     * 创建章节。
     * 业务原因：新章节创建需要统一填充默认排序与课程关联。
     */
    @PostMapping
    public Result<ChapterVO> createChapter(
            @Valid @RequestBody ChapterDTO dto,
            @RequestHeader(value = "X-User-Role", required = false) String currentUserRole) {
        if (!hasTeacherManageRole(currentUserRole)) {
            return Result.failure(403, "权限不足，仅教师或管理员可创建章节");
        }
        try {
            Chapter chapter = new Chapter();
            BeanUtils.copyProperties(dto, chapter);
            Chapter created = chapterService.createChapter(chapter);
            return Result.success("章节创建成功", chapterService.convertToVO(created));
        } catch (Exception e) {
            log.error("创建章节失败: courseId={}", dto != null ? dto.getCourseId() : null, e);
            return Result.error("创建失败，请稍后重试");
        }
    }

    /**
     * 更新章节。
     * 说明：仅允许更新章节基础信息，明细由服务层校验。
     */
    @PutMapping("/{id}")
    public Result<ChapterVO> updateChapter(
            @PathVariable("id") Long id,
            @Valid @RequestBody ChapterDTO dto,
            @RequestHeader(value = "X-User-Role", required = false) String currentUserRole) {
        if (!hasTeacherManageRole(currentUserRole)) {
            return Result.failure(403, "权限不足，仅教师或管理员可更新章节");
        }
        try {
            Chapter chapter = new Chapter();
            BeanUtils.copyProperties(dto, chapter);
            chapter.setId(id);
            Chapter updated = chapterService.updateChapter(chapter);
            return Result.success("更新成功", chapterService.convertToVO(updated));
        } catch (Exception e) {
            log.error("更新章节失败: chapterId={}", id, e);
            return Result.error("更新失败，请稍后重试");
        }
    }

    /**
     * 删除章节。
     * 业务原因：章节下线需要清理其测验与关联资源。
     */
    @DeleteMapping("/{id}")
    public Result<Void> deleteChapter(
            @PathVariable("id") Long id,
            @RequestHeader(value = "X-User-Role", required = false) String currentUserRole) {
        if (!hasTeacherManageRole(currentUserRole)) {
            return Result.failure(403, "权限不足，仅教师或管理员可删除章节");
        }
        try {
            chapterService.deleteChapter(id);
            return Result.success("删除成功", null);
        } catch (Exception e) {
            log.error("删除章节失败: chapterId={}", id, e);
            return Result.error("删除失败，请稍后重试");
        }
    }

    /**
     * 获取章节详情。
     * 说明：返回章节与测验聚合信息，减少前端二次请求。
     */
    @GetMapping("/{id}")
    public Result<Map<String, Object>> getChapterDetail(@PathVariable("id") Long id) {
        Map<String, Object> detail = chapterService.getChapterDetail(id);
        if (detail != null) {
            // 转换 Map 中的内容
            if (detail.containsKey("chapter")) {
                detail.put("chapter", chapterService.convertToVO((Chapter) detail.get("chapter")));
            }
            if (detail.containsKey("quizzes")) {
                detail.put("quizzes", chapterService.convertQuizToVOList((List<ChapterQuiz>) detail.get("quizzes")));
            }
            return Result.success(detail);
        }
        return Result.error("章节不存在");
    }

    /**
     * 获取课程所有章节。
     * 业务原因：课程详情页需要完整章节目录用于导航。
     */
    @GetMapping("/course/{courseId}")
    public Result<List<ChapterVO>> getChaptersByCourse(@PathVariable("courseId") Long courseId) {
        List<Chapter> chapters = chapterService.getChaptersByCourse(courseId);
        return Result.success(chapterService.convertToVOList(chapters));
    }

    /**
     * 添加章节测验题目。
     * 业务原因：课程测验与章节强关联，需统一由章节入口创建。
     */
    @PostMapping("/{chapterId}/quizzes")
    public Result<ChapterQuizVO> addQuiz(
            @PathVariable("chapterId") Long chapterId,
            @Valid @RequestBody ChapterQuizDTO dto,
            @RequestHeader(value = "X-User-Role", required = false) String currentUserRole) {
        if (!hasTeacherManageRole(currentUserRole)) {
            return Result.failure(403, "权限不足，仅教师或管理员可添加测验");
        }
        try {
            ChapterQuiz quiz = new ChapterQuiz();
            BeanUtils.copyProperties(dto, quiz);
            quiz.setChapterId(chapterId);
            ChapterQuiz created = chapterService.addQuiz(quiz);
            return Result.success("题目添加成功", chapterService.convertQuizToVO(created));
        } catch (Exception e) {
            log.error("添加章节测验失败: chapterId={}", chapterId, e);
            return Result.error("添加失败，请稍后重试");
        }
    }

    /**
     * 批量添加测验题目。
     * 说明：批量导入场景下减少请求次数。
     */
    @PostMapping("/{chapterId}/quizzes/batch")
    public Result<Void> addQuizzesBatch(@PathVariable("chapterId") Long chapterId,
            @Valid @RequestBody List<ChapterQuizDTO> dtos,
            @RequestHeader(value = "X-User-Role", required = false) String currentUserRole) {
        if (!hasTeacherManageRole(currentUserRole)) {
            return Result.failure(403, "权限不足，仅教师或管理员可批量添加测验");
        }
        try {
            List<ChapterQuiz> quizzes = dtos.stream().map(dto -> {
                ChapterQuiz quiz = new ChapterQuiz();
                BeanUtils.copyProperties(dto, quiz);
                return quiz;
            }).collect(Collectors.toList());
            chapterService.addQuizzes(chapterId, quizzes);
            return Result.success("批量添加成功", null);
        } catch (Exception e) {
            log.error("批量添加章节测验失败: chapterId={}", chapterId, e);
            return Result.error("添加失败，请稍后重试");
        }
    }

    /**
     * 获取章节测验。
     * 说明：返回 VO 列表，避免直接暴露题目实体。
     */
    @GetMapping("/{chapterId}/quizzes")
    public Result<List<ChapterQuizVO>> getQuizzes(@PathVariable("chapterId") Long chapterId) {
        List<ChapterQuiz> quizzes = chapterService.getQuizzes(chapterId);
        return Result.success(chapterService.convertQuizToVOList(quizzes));
    }

    /**
     * 删除测验题目。
     * 说明：仅删除指定题目，不影响章节其他内容。
     */
    @DeleteMapping("/quizzes/{quizId}")
    public Result<Void> deleteQuiz(
            @PathVariable("quizId") Long quizId,
            @RequestHeader(value = "X-User-Role", required = false) String currentUserRole) {
        if (!hasTeacherManageRole(currentUserRole)) {
            return Result.failure(403, "权限不足，仅教师或管理员可删除测验");
        }
        try {
            chapterService.deleteQuiz(quizId);
            return Result.success("删除成功", null);
        } catch (Exception e) {
            log.error("删除章节测验失败: quizId={}", quizId, e);
            return Result.error("删除失败，请稍后重试");
        }
    }

    /**
     * 判断是否具备教师管理权限（教师或管理员）。
     */
    private boolean hasTeacherManageRole(String currentUserRole) {
        return currentUserRole != null
                && ("teacher".equalsIgnoreCase(currentUserRole) || "admin".equalsIgnoreCase(currentUserRole));
    }
}
