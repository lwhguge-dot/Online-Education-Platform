package com.eduplatform.course.controller;

import com.eduplatform.common.result.Result;
import com.eduplatform.course.service.FileUploadService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.HashMap;
import java.util.Map;

/**
 * 课程资源上传控制器。
 * 设计意图：统一课程相关文件上传入口，返回对象存储可访问 URL。
 */
@RestController
@RequestMapping("/api/files")
@RequiredArgsConstructor
public class FileUploadController {

    private final FileUploadService fileUploadService;

    /**
     * 上传课程视频。
     * 说明：文件写入对象存储后返回可直接访问的 URL。
     */
    @PostMapping("/upload/video")
    public Result<Map<String, String>> uploadVideo(@RequestParam("file") MultipartFile file) {
        try {
            String url = fileUploadService.uploadVideo(file);
            Map<String, String> result = new HashMap<>();
            result.put("url", url);
            result.put("originalName", file.getOriginalFilename());
            return Result.success("视频上传成功", result);
        } catch (Exception e) {
            return Result.error("上传失败: " + e.getMessage());
        }
    }

    /**
     * 上传课程图片资源。
     */
    @PostMapping("/upload/image")
    public Result<Map<String, String>> uploadImage(@RequestParam("file") MultipartFile file) {
        try {
            String url = fileUploadService.uploadImage(file);
            Map<String, String> result = new HashMap<>();
            result.put("url", url);
            result.put("originalName", file.getOriginalFilename());
            return Result.success("图片上传成功", result);
        } catch (Exception e) {
            return Result.error("上传失败: " + e.getMessage());
        }
    }

    /**
     * 上传课程文档资源。
     */
    @PostMapping("/upload/document")
    public Result<Map<String, String>> uploadDocument(@RequestParam("file") MultipartFile file) {
        try {
            String url = fileUploadService.uploadDocument(file);
            Map<String, String> result = new HashMap<>();
            result.put("url", url);
            result.put("originalName", file.getOriginalFilename());
            return Result.success("文档上传成功", result);
        } catch (Exception e) {
            return Result.error("上传失败: " + e.getMessage());
        }
    }

    /**
     * 删除已上传资源。
     */
    @DeleteMapping("/delete")
    public Result<Void> deleteFile(@RequestParam("path") String path) {
        try {
            boolean deleted = fileUploadService.deleteFile(path);
            if (deleted) {
                return Result.success("文件已删除", null);
            }
            return Result.error("文件删除失败");
        } catch (Exception e) {
            return Result.error("删除失败: " + e.getMessage());
        }
    }
}
