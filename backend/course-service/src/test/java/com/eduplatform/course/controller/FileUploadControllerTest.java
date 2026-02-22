package com.eduplatform.course.controller;

import com.eduplatform.common.result.Result;
import com.eduplatform.course.service.FileUploadService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * FileUploadController 权限与异常返回测试。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("FileUploadController 单元测试")
class FileUploadControllerTest {

    @InjectMocks
    private FileUploadController fileUploadController;

    @Mock
    private FileUploadService fileUploadService;

    @Mock
    private MultipartFile multipartFile;

    @Test
    @DisplayName("上传视频-学生角色被拒绝")
    void uploadVideoShouldDenyStudentRole() throws Exception {
        Result<Map<String, String>> result = fileUploadController.uploadVideo(multipartFile, "student");

        assertNotNull(result);
        assertEquals(403, result.getCode());
        assertEquals("权限不足，仅教师或管理员可上传课程视频", result.getMessage());
        verify(fileUploadService, never()).uploadVideo(multipartFile);
    }

    @Test
    @DisplayName("上传视频-异常信息不应外泄")
    void uploadVideoShouldHideInternalExceptionMessage() throws Exception {
        // 控制器日志已做脱敏，不再依赖原始文件名。
        when(fileUploadService.uploadVideo(multipartFile)).thenThrow(new RuntimeException("minio timeout"));

        Result<Map<String, String>> result = fileUploadController.uploadVideo(multipartFile, "teacher");

        assertNotNull(result);
        assertEquals(500, result.getCode());
        assertEquals("上传失败，请稍后重试", result.getMessage());
        verify(fileUploadService).uploadVideo(multipartFile);
    }
}
