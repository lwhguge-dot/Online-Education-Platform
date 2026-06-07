# API 文档

本文档介绍智慧课堂在线教育平台的 API 接口。

## 目录

- [概述](#概述)
- [认证](#认证)
- [用户服务](#用户服务)
- [课程服务](#课程服务)
- [作业服务](#作业服务)
- [进度服务](#进度服务)
- [错误码](#错误码)

## 概述

### 基础信息

- **Base URL**: `http://localhost:8090`
- **认证方式**: JWT Bearer Token
- **数据格式**: JSON
- **字符编码**: UTF-8

### 统一响应格式

```json
{
  "code": 200,
  "message": "success",
  "data": {},
  "traceId": "trace-id"
}
```

### 错误响应格式

```json
{
  "code": 400,
  "message": "错误信息",
  "data": null,
  "traceId": "trace-id"
}
```

## 认证

### 获取 JWT Token

**请求**

```http
POST /api/user/login
Content-Type: application/json

{
  "email": "student@edu.cn",
  "password": "Student123!"
}
```

**响应**

```json
{
  "code": 200,
  "message": "登录成功",
  "data": {
    "token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
    "user": {
      "id": 1,
      "email": "student@edu.cn",
      "username": "student",
      "name": "张三",
      "role": "student"
    }
  },
  "traceId": "trace-id"
}
```

### 使用 Token

在请求头中添加：

```http
Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...
```

### 测试账号

| 角色 | 邮箱 | 密码 |
|------|------|------|
| 学生 | student@edu.cn | Student123! |
| 教师 | teacher@edu.cn | Teacher123! |
| 管理员 | admin@edu.cn | Admin123! |

## 用户服务

### 用户注册

**请求**

```http
POST /api/user/register
Content-Type: application/json

{
  "email": "user@example.com",
  "username": "username",
  "password": "Password123!",
  "name": "姓名",
  "role": "student"
}
```

**响应**

```json
{
  "code": 200,
  "message": "注册成功",
  "data": {
    "id": 1,
    "email": "user@example.com",
    "username": "username",
    "name": "姓名",
    "role": "student"
  },
  "traceId": "trace-id"
}
```

### 用户登录

**请求**

```http
POST /api/user/login
Content-Type: application/json

{
  "email": "student@edu.cn",
  "password": "Student123!"
}
```

**响应**

```json
{
  "code": 200,
  "message": "登录成功",
  "data": {
    "token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
    "user": {
      "id": 1,
      "email": "student@edu.cn",
      "username": "student",
      "name": "张三",
      "role": "student"
    }
  },
  "traceId": "trace-id"
}
```

### 获取用户信息

**请求**

```http
GET /api/user/profile
Authorization: Bearer <token>
```

**响应**

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "id": 1,
    "email": "student@edu.cn",
    "username": "student",
    "name": "张三",
    "role": "student",
    "avatar": null,
    "phone": null,
    "birthday": null,
    "gender": null,
    "status": 1,
    "lastLoginAt": "2024-01-01T00:00:00",
    "createdAt": "2024-01-01T00:00:00",
    "updatedAt": "2024-01-01T00:00:00"
  },
  "traceId": "trace-id"
}
```

### 更新用户信息

**请求**

```http
PUT /api/user/profile
Authorization: Bearer <token>
Content-Type: application/json

{
  "name": "新姓名",
  "phone": "13800138000",
  "birthday": "2000-01-01",
  "gender": "male"
}
```

**响应**

```json
{
  "code": 200,
  "message": "更新成功",
  "data": {
    "id": 1,
    "email": "student@edu.cn",
    "username": "student",
    "name": "新姓名",
    "phone": "13800138000",
    "birthday": "2000-01-01",
    "gender": "male"
  },
  "traceId": "trace-id"
}
```

### 修改密码

**请求**

```http
PUT /api/user/password
Authorization: Bearer <token>
Content-Type: application/json

{
  "oldPassword": "OldPassword123!",
  "newPassword": "NewPassword123!"
}
```

**响应**

```json
{
  "code": 200,
  "message": "密码修改成功",
  "data": null,
  "traceId": "trace-id"
}
```

## 课程服务

### 获取课程列表

**请求**

```http
GET /api/courses?page=1&size=10&subject=math
Authorization: Bearer <token>
```

**响应**

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "records": [
      {
        "id": 1,
        "title": "高中数学基础",
        "description": "高中数学基础知识讲解",
        "subject": "数学",
        "coverImage": "https://example.com/cover.jpg",
        "teacherId": 1,
        "teacherName": "李老师",
        "rating": 4.5,
        "studentCount": 100,
        "status": "PUBLISHED",
        "createdAt": "2024-01-01T00:00:00",
        "updatedAt": "2024-01-01T00:00:00"
      }
    ],
    "total": 100,
    "size": 10,
    "current": 1,
    "pages": 10
  },
  "traceId": "trace-id"
}
```

### 获取课程详情

**请求**

```http
GET /api/courses/1
Authorization: Bearer <token>
```

**响应**

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "id": 1,
    "title": "高中数学基础",
    "description": "高中数学基础知识讲解",
    "subject": "数学",
    "coverImage": "https://example.com/cover.jpg",
    "teacherId": 1,
    "teacherName": "李老师",
    "rating": 4.5,
    "studentCount": 100,
    "status": "PUBLISHED",
    "chapters": [
      {
        "id": 1,
        "title": "第一章 集合与函数",
        "description": "集合与函数的基础知识",
        "sortOrder": 1,
        "videoUrl": "https://example.com/video1.mp4",
        "videoDuration": 3600,
        "status": 1
      }
    ],
    "createdAt": "2024-01-01T00:00:00",
    "updatedAt": "2024-01-01T00:00:00"
  },
  "traceId": "trace-id"
}
```

### 创建课程（教师）

**请求**

```http
POST /api/courses
Authorization: Bearer <token>
Content-Type: application/json

{
  "title": "新课程",
  "description": "课程描述",
  "subject": "数学",
  "coverImage": "https://example.com/cover.jpg"
}
```

**响应**

```json
{
  "code": 200,
  "message": "创建成功",
  "data": {
    "id": 1,
    "title": "新课程",
    "description": "课程描述",
    "subject": "数学",
    "coverImage": "https://example.com/cover.jpg",
    "teacherId": 1,
    "teacherName": "李老师",
    "rating": 0,
    "studentCount": 0,
    "status": "DRAFT",
    "createdAt": "2024-01-01T00:00:00",
    "updatedAt": "2024-01-01T00:00:00"
  },
  "traceId": "trace-id"
}
```

### 更新课程（教师）

**请求**

```http
PUT /api/courses/1
Authorization: Bearer <token>
Content-Type: application/json

{
  "title": "更新后的课程",
  "description": "更新后的描述"
}
```

**响应**

```json
{
  "code": 200,
  "message": "更新成功",
  "data": {
    "id": 1,
    "title": "更新后的课程",
    "description": "更新后的描述",
    "subject": "数学",
    "coverImage": "https://example.com/cover.jpg",
    "teacherId": 1,
    "teacherName": "李老师",
    "rating": 4.5,
    "studentCount": 100,
    "status": "PUBLISHED",
    "createdAt": "2024-01-01T00:00:00",
    "updatedAt": "2024-01-01T00:00:00"
  },
  "traceId": "trace-id"
}
```

### 删除课程（教师）

**请求**

```http
DELETE /api/courses/1
Authorization: Bearer <token>
```

**响应**

```json
{
  "code": 200,
  "message": "删除成功",
  "data": null,
  "traceId": "trace-id"
}
```

### 选课（学生）

**请求**

```http
POST /api/courses/1/enroll
Authorization: Bearer <token>
```

**响应**

```json
{
  "code": 200,
  "message": "选课成功",
  "data": {
    "id": 1,
    "studentId": 1,
    "courseId": 1,
    "enrolledAt": "2024-01-01T00:00:00",
    "progress": 0,
    "status": "active"
  },
  "traceId": "trace-id"
}
```

## 作业服务

### 获取作业列表

**请求**

```http
GET /api/homeworks?courseId=1&page=1&size=10
Authorization: Bearer <token>
```

**响应**

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "records": [
      {
        "id": 1,
        "courseId": 1,
        "chapterId": 1,
        "teacherId": 1,
        "title": "第一章作业",
        "description": "完成第一章练习题",
        "homeworkType": "objective",
        "totalScore": 100,
        "deadline": "2024-01-15T23:59:59",
        "testType": "chapter",
        "createdAt": "2024-01-01T00:00:00",
        "updatedAt": "2024-01-01T00:00:00"
      }
    ],
    "total": 10,
    "size": 10,
    "current": 1,
    "pages": 1
  },
  "traceId": "trace-id"
}
```

### 获取作业详情

**请求**

```http
GET /api/homeworks/1
Authorization: Bearer <token>
```

**响应**

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "id": 1,
    "courseId": 1,
    "chapterId": 1,
    "teacherId": 1,
    "title": "第一章作业",
    "description": "完成第一章练习题",
    "homeworkType": "objective",
    "totalScore": 100,
    "deadline": "2024-01-15T23:59:59",
    "testType": "chapter",
    "questions": [
      {
        "id": 1,
        "questionType": "single",
        "content": "1+1=?",
        "options": "[\"1\", \"2\", \"3\", \"4\"]",
        "correctAnswer": "2",
        "answerAnalysis": "1+1=2",
        "score": 10,
        "sortOrder": 1
      }
    ],
    "createdAt": "2024-01-01T00:00:00",
    "updatedAt": "2024-01-01T00:00:00"
  },
  "traceId": "trace-id"
}
```

### 提交作业（学生）

**请求**

```http
POST /api/homeworks/1/submit
Authorization: Bearer <token>
Content-Type: application/json

{
  "answers": [
    {
      "questionId": 1,
      "studentAnswer": "2"
    }
  ]
}
```

**响应**

```json
{
  "code": 200,
  "message": "提交成功",
  "data": {
    "id": 1,
    "studentId": 1,
    "homeworkId": 1,
    "submitStatus": "submitted",
    "objectiveScore": 10,
    "subjectiveScore": null,
    "totalScore": 10,
    "submittedAt": "2024-01-01T00:00:00",
    "gradedAt": null,
    "gradedBy": null,
    "feedback": null,
    "createdAt": "2024-01-01T00:00:00",
    "updatedAt": "2024-01-01T00:00:00"
  },
  "traceId": "trace-id"
}
```

## 进度服务

### 获取学习进度

**请求**

```http
GET /api/progress?courseId=1
Authorization: Bearer <token>
```

**响应**

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "courseId": 1,
    "studentId": 1,
    "progress": 50,
    "chapters": [
      {
        "chapterId": 1,
        "videoRate": 100,
        "videoWatchTime": 3600,
        "quizScore": 90,
        "isCompleted": 1,
        "completedAt": "2024-01-01T00:00:00"
      },
      {
        "chapterId": 2,
        "videoRate": 50,
        "videoWatchTime": 1800,
        "quizScore": null,
        "isCompleted": 0,
        "completedAt": null
      }
    ]
  },
  "traceId": "trace-id"
}
```

### 更新视频进度

**请求**

```http
POST /api/progress/video
Authorization: Bearer <token>
Content-Type: application/json

{
  "chapterId": 1,
  "videoRate": 75,
  "videoWatchTime": 2700,
  "lastPosition": 2700
}
```

**响应**

```json
{
  "code": 200,
  "message": "更新成功",
  "data": {
    "chapterId": 1,
    "videoRate": 75,
    "videoWatchTime": 2700,
    "lastPosition": 2700,
    "isCompleted": 0,
    "updatedAt": "2024-01-01T00:00:00"
  },
  "traceId": "trace-id"
}
```

### 提交测验

**请求**

```http
POST /api/progress/quiz
Authorization: Bearer <token>
Content-Type: application/json

{
  "chapterId": 1,
  "answers": [
    {
      "quizId": 1,
      "answer": "2"
    }
  ]
}
```

**响应**

```json
{
  "code": 200,
  "message": "提交成功",
  "data": {
    "chapterId": 1,
    "quizScore": 90,
    "quizSubmittedAt": "2024-01-01T00:00:00",
    "isCompleted": 1,
    "completedAt": "2024-01-01T00:00:00"
  },
  "traceId": "trace-id"
}
```

## 错误码

| 错误码 | 说明 |
|--------|------|
| 200 | 成功 |
| 400 | 请求参数错误 |
| 401 | 未认证 |
| 403 | 无权限 |
| 404 | 资源不存在 |
| 500 | 服务器内部错误 |

### 常见错误响应

**参数错误**

```json
{
  "code": 400,
  "message": "参数错误：邮箱格式不正确",
  "data": null,
  "traceId": "trace-id"
}
```

**未认证**

```json
{
  "code": 401,
  "message": "未认证：请先登录",
  "data": null,
  "traceId": "trace-id"
}
```

**无权限**

```json
{
  "code": 403,
  "message": "无权限：仅教师可执行此操作",
  "data": null,
  "traceId": "trace-id"
}
```

**资源不存在**

```json
{
  "code": 404,
  "message": "资源不存在：课程不存在",
  "data": null,
  "traceId": "trace-id"
}
```

**服务器错误**

```json
{
  "code": 500,
  "message": "服务器内部错误",
  "data": null,
  "traceId": "trace-id"
}
```

## 联系方式

- 项目主页: https://github.com/your-username/edu-platform
- 问题反馈: https://github.com/your-username/edu-platform/issues
- 邮箱: your-email@example.com
