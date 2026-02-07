package com.eduplatform.user.config;

import io.minio.MinioClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * MinIO 客户端配置。
 * 说明：类名历史原因保留，实际职责已切换为对象存储配置，项目不再使用本地文件系统。
 */
@Configuration
public class FileUploadConfig {

    /**
     * MinIO 服务端点（可被网关或前端访问的地址）。
     */
    @Value("${minio.endpoint}")
    private String endpoint;

    /**
     * MinIO 访问密钥。
     */
    @Value("${minio.access-key}")
    private String accessKey;

    /**
     * MinIO 安全密钥。
     */
    @Value("${minio.secret-key}")
    private String secretKey;

    /**
     * 创建 MinIO 客户端实例，用于上传与删除对象。
     */
    @Bean
    public MinioClient minioClient() {
        return MinioClient.builder()
                .endpoint(endpoint)
                .credentials(accessKey, secretKey)
                .build();
    }
}
