package com.eduplatform.course.service;

import com.eduplatform.common.exception.BusinessException;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.io.ByteArrayInputStream;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 资源持久化服务 (文件上传与管理)
 * 处理教学资源（视频、课件、图片）的对象存储与元数据转换，统一落地到 MinIO。
 *
 * 存储设计：
 * 1. 分级目录：采用 `type/yyyy/MM/dd` 的日期分级结构，规避单前缀对象数量过大。
 * 2. 匿名化命名：使用 UUID+后缀 对原始文件进行重命名，确保对象键唯一性。
 * 3. 严格校验：集成 MIME 类型指纹校验与文件大小阈值熔断机制。
 *
 * @author Antigravity
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FileUploadService {

    private final MinioClient minioClient;

    /**
     * MinIO 存储桶名称。
     */
    @Value("${minio.bucket}")
    private String bucketName;

    /**
     * MinIO 访问端点，用于拼接对象 URL。
     */
    @Value("${minio.endpoint}")
    private String endpoint;

    @Value("${file.max-size:104857600}")
    private long maxFileSize;

    /**
     * 视频资源白名单 (支持主流流媒体/交互式视频格式)
     */
    private static final List<String> ALLOWED_VIDEO_TYPES = Arrays.asList(
            "video/mp4", "video/avi", "video/mkv", "video/mov", "video/wmv", "video/flv");

    /**
     * 图片资源白名单 (含 WebP 现代格式支持)
     */
    private static final List<String> ALLOWED_IMAGE_TYPES = Arrays.asList(
            "image/jpeg", "image/png", "image/gif", "image/webp");

    /**
     * 教学文档白名单 (涵盖 Office 全家桶及 PDF)
     */
    private static final List<String> ALLOWED_DOC_TYPES = Arrays.asList(
            "application/pdf", "application/msword",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "application/vnd.ms-powerpoint",
            "application/vnd.openxmlformats-officedocument.presentationml.presentation");

    /**
     * 上传流媒体视频
     * 逻辑方案：验证视频特征 -> 构造 videos 子目录 -> 执行物理写入。
     */
    public String uploadVideo(MultipartFile file) throws IOException {
        validateFile(file, ALLOWED_VIDEO_TYPES, "视频");
        return saveFile(file, "videos");
    }

    /**
     * 上传课程封面或静态资源图
     */
    public String uploadImage(MultipartFile file) throws IOException {
        validateFile(file, ALLOWED_IMAGE_TYPES, "图片");
        return saveFile(file, "images");
    }

    /**
     * 上传教学大纲或讲义文档
     */
    public String uploadDocument(MultipartFile file) throws IOException {
        validateFile(file, ALLOWED_DOC_TYPES, "文档");
        return saveFile(file, "docs");
    }

    /**
     * 常见文件格式的 Magic Bytes 特征。
     * 覆盖所有允许上传的文件类型。
     */
    private static final Map<String, byte[]> MAGIC_BYTES;
    static {
        MAGIC_BYTES = new HashMap<>();
        // 视频格式
        MAGIC_BYTES.put("video/mp4", new byte[]{0x00, 0x00, 0x00, (byte) 0x1C, 0x66, 0x74, 0x79, 0x70});
        MAGIC_BYTES.put("video/avi", new byte[]{0x52, 0x49, 0x46, 0x46});  // RIFF
        MAGIC_BYTES.put("video/mkv", new byte[]{0x1A, 0x45, (byte) 0xDF, (byte) 0xA3});  // EBML
        MAGIC_BYTES.put("video/mov", new byte[]{0x00, 0x00, 0x00, 0x14, 0x66, 0x74, 0x79, 0x70});  // ftyp
        MAGIC_BYTES.put("video/wmv", new byte[]{0x30, 0x26, (byte) 0xB2, 0x75});  // ASF
        MAGIC_BYTES.put("video/flv", new byte[]{0x46, 0x4C, 0x56, 0x01});  // FLV
        // 图片格式
        MAGIC_BYTES.put("image/jpeg", new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF});
        MAGIC_BYTES.put("image/png", new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A});
        MAGIC_BYTES.put("image/gif", new byte[]{0x47, 0x49, 0x46, 0x38});
        MAGIC_BYTES.put("image/webp", new byte[]{0x52, 0x49, 0x46, 0x46});  // RIFF
        // 文档格式
        MAGIC_BYTES.put("application/pdf", new byte[]{0x25, 0x50, 0x44, 0x46});  // %PDF
        MAGIC_BYTES.put("application/msword", new byte[]{(byte) 0xD0, (byte) 0xCF, 0x11, (byte) 0xE0});  // OLE
        MAGIC_BYTES.put("application/vnd.openxmlformats-officedocument.wordprocessingml.document", new byte[]{0x50, 0x4B, 0x03, 0x04});  // ZIP
        MAGIC_BYTES.put("application/vnd.ms-powerpoint", new byte[]{(byte) 0xD0, (byte) 0xCF, 0x11, (byte) 0xE0});  // OLE
        MAGIC_BYTES.put("application/vnd.openxmlformats-officedocument.presentationml.presentation", new byte[]{0x50, 0x4B, 0x03, 0x04});  // ZIP
    }

    /**
     * 资源合法性前置校验
     * 核心阈值：基于配置的 maxFileSize 执行强约束拦截。
     * 除检查 MIME 类型外，还通过 Magic Bytes 进行服务端文件类型验证。
     */
    private void validateFile(MultipartFile file, List<String> allowedTypes, String typeName) {
        if (file.isEmpty()) {
            throw new BusinessException("操作失败：上传文件内容不能为空");
        }
        if (file.getSize() > maxFileSize) {
            throw new BusinessException("由于安全策略限制，文件大小不能超过 " + (maxFileSize / 1024 / 1024) + "MB");
        }
        String contentType = file.getContentType();
        if (contentType == null || !allowedTypes.contains(contentType)) {
            throw new BusinessException("格式不受支持，该频道仅限上传: " + typeName);
        }
        if (!validateMagicBytes(file, contentType)) {
            throw new BusinessException("文件内容与声明的格式不符，拒绝上传");
        }
    }

    /**
     * 通过 Magic Bytes 验证文件内容是否与声明类型一致。
     */
    private boolean validateMagicBytes(MultipartFile file, String contentType) {
        byte[] expected = MAGIC_BYTES.get(contentType);
        if (expected == null) {
            return true;
        }
        try (InputStream in = file.getInputStream()) {
            byte[] header = new byte[expected.length];
            int bytesRead = in.readNBytes(header, 0, expected.length);
            if (bytesRead < expected.length) {
                return false;
            }
            for (int i = 0; i < expected.length; i++) {
                if (header[i] != expected[i]) {
                    return false;
                }
            }
            return true;
        } catch (IOException e) {
            log.warn("Magic Bytes 验证失败", e);
            return false;
        }
    }

    /**
     * 对象存储引擎
     * 实现细节：
     * 1. 分级对象键：按日期分层以降低前缀膨胀。
     * 2. 哈希碰撞规避：UUID + 扩展名重组。
     * 3. 返回对象访问 URL，供前端直接渲染。
     * 
     * @return 用于数据库存储的 Web 路径
     */
    private String saveFile(MultipartFile file, String subDir) throws IOException {
        String objectName = buildObjectName(subDir, file.getOriginalFilename());
        try (InputStream inputStream = file.getInputStream()) {
            PutObjectArgs args = PutObjectArgs.builder()
                    .bucket(bucketName)
                    .object(objectName)
                    .stream(inputStream, file.getSize(), -1)
                    .contentType(file.getContentType())
                    .build();
            minioClient.putObject(args);
        } catch (Exception exception) {
            throw new IOException("课程资源上传失败", exception);
        }

        return buildObjectUrl(objectName);
    }

    /**
     * 对象资源回收 (资源下线)
     * 安全策略：仅允许在固定前缀内删除，防止越权删除其他业务对象。
     *
     * @param filePath 资源访问 URL
     * @return true 表示对象已成功移除
     */
    public boolean deleteFile(String filePath) {
        if (filePath == null || !filePath.contains("/" + bucketName + "/")) {
            return false;
        }
        try {
            String objectName = resolveObjectName(filePath);
            minioClient.removeObject(io.minio.RemoveObjectArgs.builder()
                    .bucket(bucketName)
                    .object(objectName)
                    .build());
            return true;
        } catch (Exception e) {
            // 安全要求：避免直接记录用户可控路径，防止日志注入。
            log.error("对象存储回收失败", e);
            return false;
        }
    }

    /**
     * 构建对象名称，使用日期分层避免单前缀过大。
     */
    private String buildObjectName(String prefix, String originalFilename) {
        String dateDir = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy/MM/dd"));
        String extension = resolveExtension(originalFilename);
        String filename = UUID.randomUUID().toString().replace("-", "") + extension;
        return prefix + "/" + dateDir + "/" + filename;
    }

    /**
     * 解析文件扩展名，缺失时使用空扩展名。
     */
    private String resolveExtension(String originalFilename) {
        if (originalFilename == null || !originalFilename.contains(".")) {
            return "";
        }
        return originalFilename.substring(originalFilename.lastIndexOf("."));
    }

    /**
     * 构造对象访问 URL。
     */
    private String buildObjectUrl(String objectName) {
        // 返回相对路径，由前端或网关处理域名前缀
        return "/oss/" + bucketName + "/" + objectName;
    }

    /**
     * 从完整 URL 解析对象键，过滤路径遍历攻击。
     */
    private String resolveObjectName(String filePath) {
        String marker = "/" + bucketName + "/";
        int index = filePath.indexOf(marker);
        if (index < 0) {
            throw new BusinessException("文件路径格式不合法");
        }
        String objectName = filePath.substring(index + marker.length());
        if (objectName.contains("..") || objectName.contains("\\")) {
            throw new BusinessException("文件路径包含非法字符");
        }
        return objectName;
    }
}
