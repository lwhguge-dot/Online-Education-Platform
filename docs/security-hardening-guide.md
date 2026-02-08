# 安全加固说明（2026-02）

本文档记录本轮“全量严格模式”已落地的接口安全收口策略，便于联调、验收与后续维护。

## 1. 网关统一鉴权

- 网关新增全局过滤器：`backend/gateway/src/main/java/com/eduplatform/gateway/filter/JwtAuthFilter.java`
- 所有 `/api/**` 默认要求 `Authorization: Bearer <token>`
- 网关统一注入可信身份头（并移除客户端同名头，防止伪造）：
  - `X-User-Id`
  - `X-User-Name`
  - `X-User-Role`
- WebSocket 握手改为 `?token=...`，并在用户服务握手拦截器中二次校验

## 2. 内部高危接口保护

- `/cascade/*` 与跨服务联动接口统一要求 `X-Internal-Token`
- 内部令牌来源：环境变量 `INTERNAL_API_TOKEN`
- 以下服务已配置 Feign 自动注入内部令牌：
  - `course-service`
  - `user-service`
  - `homework-service`
  - `progress-service`

## 3. 严格权限模型

本轮在多个控制器统一采用“本人 / 教师 / 管理员 / 内部服务”分层策略：

- 学生私有数据：学生仅可访问本人；教师/管理员可用于教学管理查询
- 教师私有数据：教师仅可访问本人；管理员可跨账号管理
- 管理能力（审核、下线、批量变更、导出）：仅管理员
- 内部联动接口：仅允许合法 `X-Internal-Token`

## 4. 关键控制器收口清单

- `AnnouncementController`
- `NotificationController`
- `TeacherProfileController`
- `BadgeController`
- `ProgressController`
- `EnrollmentController`
- `CourseController`
- `HomeworkController`
- `TeachingEventController`
- `TeacherStatsController`

## 5. 本轮重点行为变化（接口语义）

### 5.1 CourseController

- `createCourse/updateCourse`：仅教师或管理员；教师侧强制使用网关注入身份，不再信任请求体中的 `teacherId`
- `submit-review/withdraw-review`：教师仅可操作本人课程，管理员可全量操作
- `audit`：仅管理员或内部服务；优先使用网关身份，不再信任 `auditBy`
- `offline`：仅管理员
- `reviewing`：仅管理员可查看
- `delete`：仅课程所属教师或管理员
- `batch-status`：仅管理员
- `export`：仅管理员
- `duplicate`：教师仅可复制本人课程，且复制目标教师固定为当前教师；管理员可指定 `teacherId`

### 5.2 EnrollmentController

- 学生维度接口（`enroll/drop/check/student/progress/check-new-chapters`）统一本人/教师/管理员校验
- 教师维度接口（`course/*`、`teacher/*`）统一教师/管理员校验
- `course/{courseId}/count` 与 `course/{courseId}/today` 支持“教师/管理员或内部令牌”
- `course/{courseId}/students` 已收口为“教师/管理员或内部令牌”

### 5.3 ProgressController

- 视频上报、测验提交默认信任网关注入身份；非管理员会覆盖 DTO 内 `studentId`
- 学生学习轨迹、掌握度、测验趋势、分析等接口统一本人/教师/管理员校验
- 课程分析接口收口为教师/管理员

### 5.4 HomeworkController

- `createHomework` 与 `chapter/{chapterId}` 统计视图收口为教师/管理员
- 学生作业查询、提交、报告、问答等接口统一本人/教师/管理员校验
- 批改、教师待办、教师活动、导入题目、复制作业等教师端能力统一教师/管理员校验
- `unlock` 与 `cascade` 接口仅内部令牌可调用

## 6. 前端联调注意事项

- 日历删除接口改为携带教师参数：
  - `DELETE /calendar/events/{id}?teacherId={teacherId}`
- 公告阅读回执默认走网关身份，前端可不再强依赖 `userId`
- 仍保留部分 `studentId/teacherId` 参数以兼容旧接口，但后端将优先采用网关注入身份

## 7. 环境变量要求

以下变量应在部署环境中正确配置：

- `JWT_SECRET`
- `INTERNAL_API_TOKEN`
- `SPRING_DATASOURCE_PASSWORD`
- `SPRING_REDIS_PASSWORD`
- `MINIO_SECRET_KEY`

建议密钥轮换后执行强制重建：

```powershell
powershell -ExecutionPolicy Bypass -File .\tools\scripts\docker\Docker启动.ps1 -ForceRecreate
```
