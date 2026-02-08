package com.eduplatform.user.service;

import com.eduplatform.user.dto.TeacherProfileDTO;
import com.eduplatform.user.entity.TeacherProfile;
import com.eduplatform.user.entity.User;
import com.eduplatform.user.mapper.TeacherProfileMapper;
import com.eduplatform.user.mapper.UserMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.minio.MinioClient;
import io.minio.BucketExistsArgs;
import io.minio.MakeBucketArgs;
import io.minio.PutObjectArgs;
import io.minio.SetBucketPolicyArgs;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

/**
 * 教师档案服务
 * 负责教师个人资料的维护、教学统计展示及头像上传等业务逻辑。
 */
@Service
@RequiredArgsConstructor
public class TeacherProfileService {

    private final TeacherProfileMapper profileMapper;
    private final UserMapper userMapper;
    private final ObjectMapper objectMapper;
    private final MinioClient minioClient;

    /**
     * MinIO 对象存储桶名称。
     */
    @Value("${minio.bucket}")
    private String bucketName;

    /**
     * MinIO 公网访问端点，用于拼接可访问 URL。
     */
    @Value("${minio.endpoint}")
    private String endpoint;

    /**
     * 获取教师的完整资料信息
     * 逻辑：
     * 1. 从 User 表读取账户级信息 (邮箱、手机号等)
     * 2. 从 TeacherProfile 表读取教师扩展信息 (职称、统计数据等)
     * 3. 解析存储在 String 字段中的 JSON 结构化数据 (教学科目、评分标准等)
     *
     * @param userId 用户ID
     * @return 教师资料传输对象 (TeacherProfileDTO)
     */
    public TeacherProfileDTO getProfile(Long userId) {
        User user = userMapper.selectById(userId);
        TeacherProfile profile = profileMapper.findByUserId(userId);

        TeacherProfileDTO dto = new TeacherProfileDTO();
        if (user != null) {
            dto.setUserId(user.getId());
            dto.setUsername(user.getUsername());
            dto.setRealName(user.getName());
            dto.setEmail(user.getEmail());
            dto.setPhone(user.getPhone());
            dto.setAvatar(user.getAvatar());
        }

        if (profile != null) {
            dto.setTitle(profile.getTitle());
            dto.setDepartment(profile.getDepartment());
            dto.setIntroduction(profile.getIntroduction());
            dto.setTotalStudents(profile.getTotalStudents());
            dto.setTotalCourses(profile.getTotalCourses());

            // 解析扩展 JSON 字段
            dto.setTeachingSubjects(parseJson(profile.getTeachingSubjects(), new TypeReference<List<String>>() {
            }));
            dto.setDefaultGradingCriteria(
                    parseJson(profile.getDefaultGradingCriteria(), TeacherProfileDTO.GradingCriteria.class));
            dto.setDashboardLayout(parseJson(profile.getDashboardLayout(), TeacherProfileDTO.DashboardLayout.class));
            dto.setNotificationSettings(
                    parseJson(profile.getNotificationSettings(), TeacherProfileDTO.NotificationSettings.class));
        }

        // 防空处理：确保前端收到的 JSON 对象不为 null
        if (dto.getDefaultGradingCriteria() == null) {
            dto.setDefaultGradingCriteria(new TeacherProfileDTO.GradingCriteria());
        }
        if (dto.getDashboardLayout() == null) {
            dto.setDashboardLayout(new TeacherProfileDTO.DashboardLayout());
        }
        if (dto.getNotificationSettings() == null) {
            dto.setNotificationSettings(new TeacherProfileDTO.NotificationSettings());
        }

        return dto;
    }

    /**
     * 更新教师个人资料
     * 1. 更新 User 表的基础联系方式
     * 2. 更新或新建 TeacherProfile 扩展表记录
     * 3. 将 DTO 中的结构化对象序列化回 JSON 字符串
     *
     * @param userId 用户ID
     * @param dto    包含变更内容的资料 DTO
     */
    @Transactional
    public void updateProfile(Long userId, TeacherProfileDTO dto) {
        // 更新基础用户信息
        User user = userMapper.selectById(userId);
        if (user != null) {
            if (dto.getPhone() != null)
                user.setPhone(dto.getPhone());
            userMapper.updateById(user);
        }

        // 维护教师资料扩展记录
        TeacherProfile profile = profileMapper.findByUserId(userId);
        if (profile == null) {
            profile = new TeacherProfile();
            profile.setUserId(userId);
        }

        profile.setTitle(dto.getTitle());
        profile.setDepartment(dto.getDepartment());
        profile.setIntroduction(dto.getIntroduction());

        // 将对象序列化为 JSON 字符串持久化
        profile.setTeachingSubjects(toJson(dto.getTeachingSubjects()));
        profile.setDefaultGradingCriteria(toJson(dto.getDefaultGradingCriteria()));
        profile.setDashboardLayout(toJson(dto.getDashboardLayout()));
        profile.setNotificationSettings(toJson(dto.getNotificationSettings()));

        if (profile.getId() == null) {
            profileMapper.insertProfile(profile);
        } else {
            profileMapper.updateByUserId(profile);
        }
    }

    /**
     * 上传并更新用户头像。
     * 逻辑路径：
     * 1. 基于日期目录生成对象键，避免单一前缀过多对象影响检索性能。
     * 2. 以 UUID 进行匿名化命名，确保文件名安全与幂等性。
     * 3. 上传至 MinIO 后，生成可访问 URL 并同步写回用户档案。
     *
     * @param userId 用户ID
     * @param file   上传的图片文件
     * @return 返回可访问的 URL 路径
     * @throws IOException 上传失败时抛出，用于控制器统一兜底处理
     */
    @Transactional
    public String uploadAvatar(Long userId, MultipartFile file) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new IOException("头像文件不能为空");
        }

        // 上传前确保桶存在，避免首次部署时因桶缺失导致 500。
        ensureBucketExists();

        String objectName = buildObjectName("avatars", file.getOriginalFilename());
        try (InputStream inputStream = file.getInputStream()) {
            PutObjectArgs args = PutObjectArgs.builder()
                    .bucket(bucketName)
                    .object(objectName)
                    .stream(inputStream, file.getSize(), -1)
                    .contentType(file.getContentType())
                    .build();
            minioClient.putObject(args);
        } catch (Exception exception) {
            throw new IOException("头像上传到对象存储失败", exception);
        }

        String avatarUrl = buildObjectUrl(objectName);
        User user = userMapper.selectById(userId);
        if (user != null) {
            user.setAvatar(avatarUrl);
            userMapper.updateById(user);
        }

        return avatarUrl;
    }

    /**
     * 确保对象存储桶存在。
     * 设计说明：新环境首次启动时 MinIO 可能尚未创建业务桶，
     * 这里做一次幂等检查并按需创建，避免上传接口直接失败。
     */
    private void ensureBucketExists() throws IOException {
        try {
            boolean exists = minioClient.bucketExists(BucketExistsArgs.builder().bucket(bucketName).build());
            if (!exists) {
                minioClient.makeBucket(MakeBucketArgs.builder().bucket(bucketName).build());
            }

            // 头像属于公开静态资源，统一设置桶只读公开策略，便于前端直接渲染。
            String publicReadPolicy = "{\"Version\":\"2012-10-17\",\"Statement\":[{\"Effect\":\"Allow\",\"Principal\":{\"AWS\":[\"*\"]},\"Action\":[\"s3:GetObject\"],\"Resource\":[\"arn:aws:s3:::"
                    + bucketName + "/*\"]}]}";
            minioClient.setBucketPolicy(SetBucketPolicyArgs.builder()
                    .bucket(bucketName)
                    .config(publicReadPolicy)
                    .build());
        } catch (Exception exception) {
            throw new IOException("初始化对象存储桶失败", exception);
        }
    }

    /**
     * 更新教师通知推送设置
     */
    @Transactional
    public void updateNotificationSettings(Long userId, TeacherProfileDTO.NotificationSettings settings) {
        TeacherProfile profile = profileMapper.findByUserId(userId);
        if (profile == null) {
            profile = new TeacherProfile();
            profile.setUserId(userId);
            profile.setNotificationSettings(toJson(settings));
            profileMapper.insertProfile(profile);
        } else {
            profile.setNotificationSettings(toJson(settings));
            profileMapper.updateByUserId(profile);
        }
    }

    /**
     * 更新教师默认评分标准
     */
    @Transactional
    public void updateGradingCriteria(Long userId, TeacherProfileDTO.GradingCriteria criteria) {
        TeacherProfile profile = profileMapper.findByUserId(userId);
        if (profile == null) {
            profile = new TeacherProfile();
            profile.setUserId(userId);
            profile.setDefaultGradingCriteria(toJson(criteria));
            profileMapper.insertProfile(profile);
        } else {
            profile.setDefaultGradingCriteria(toJson(criteria));
            profileMapper.updateByUserId(profile);
        }
    }

    /**
     * 更新教师管理后台仪表盘布局
     */
    @Transactional
    public void updateDashboardLayout(Long userId, TeacherProfileDTO.DashboardLayout layout) {
        TeacherProfile profile = profileMapper.findByUserId(userId);
        if (profile == null) {
            profile = new TeacherProfile();
            profile.setUserId(userId);
            profile.setDashboardLayout(toJson(layout));
            profileMapper.insertProfile(profile);
        } else {
            profile.setDashboardLayout(toJson(layout));
            profileMapper.updateByUserId(profile);
        }
    }

    /**
     * 通用的 JSON 反序列化辅助工具 (单对象)
     */
    private <T> T parseJson(String json, Class<T> clazz) {
        if (json == null || json.isEmpty())
            return null;
        try {
            return objectMapper.readValue(json, clazz);
        } catch (JsonProcessingException e) {
            return null;
        }
    }

    /**
     * 通用的 JSON 反序列化辅助工具 (泛型列表等复杂类型)
     */
    private <T> T parseJson(String json, TypeReference<T> typeRef) {
        if (json == null || json.isEmpty())
            return null;
        try {
            return objectMapper.readValue(json, typeRef);
        } catch (JsonProcessingException e) {
            return null;
        }
    }

    /**
     * 通用的 JSON 序列化辅助工具
     */
    private String toJson(Object obj) {
        if (obj == null)
            return null;
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            return null;
        }
    }

    /**
     * 生成 MinIO 对象键。
     * 设计意图：通过分层目录减少单层对象数量，降低对象列表性能风险。
     */
    private String buildObjectName(String prefix, String originalFilename) {
        String dateDir = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy/MM/dd"));
        String extension = resolveExtension(originalFilename);
        String filename = UUID.randomUUID().toString().replace("-", "") + extension;
        return prefix + "/" + dateDir + "/" + filename;
    }

    /**
     * 提取文件扩展名，缺失时使用默认后缀。
     */
    private String resolveExtension(String originalFilename) {
        if (originalFilename == null || !originalFilename.contains(".")) {
            return ".jpg";
        }
        return originalFilename.substring(originalFilename.lastIndexOf("."));
    }

    /**
     * 构造可访问的对象 URL，统一由配置端点进行拼接。
     */
    private String buildObjectUrl(String objectName) {
        // 返回相对路径，由前端或网关处理域名前缀
        return "/oss/" + bucketName + "/" + objectName;
    }
}
