# 2026-02-19 后端全量代码审查报告（Skill 驱动）

## 审查总览
- 审查时间：2026-02-19
- 审查范围：`backend/gateway`、`backend/common`、`backend/user-service`、`backend/course-service`、`backend/homework-service`、`backend/progress-service`、`backend/schema.sql`、`backend/pom.xml`、根目录 `docker-compose.yml`
- 审查方法：静态审查 + 全量验证（`mvn -T 1C clean test`、`mvn -T 1C test`）
- 规模基线：Java 文件 228、Controller 20、Service 27、测试文件 6

## 总体风险热力图
| 级别 | 数量 | 结论 |
|---|---:|---|
| P0 | 4 | 存在可直接导致生产阻断/账号安全风险的问题，需 72 小时内止血 |
| P1 | 4 | 存在高概率安全与数据可信性风险，需 1 周内完成修复 |
| P2 | 4 | 存在稳定性/可维护性/工程质量问题，需纳入 2~4 周治理 |
| P3 | 0 | 无 |

## 修复进展（更新于 2026-02-21）
- 本节用于记录修复后的实时状态；上文风险热力图为 2026-02-19 首次审查基线。

### 问题状态跟踪
| 编号 | 当前状态 | 修复说明 | 关键证据 |
|---|---|---|---|
| BKR-P2-009 | 已修复 | 网关限流键新增“受信代理 IP”约束。仅当来源地址命中 `gateway.rate-limit.trusted-proxies` 时才采信 `X-Forwarded-For/X-Real-IP`，默认回退 `remoteAddress`，阻断伪造转发头绕过。 | `backend/gateway/src/main/java/com/eduplatform/gateway/filter/RateLimitFilter.java:165`、`backend/gateway/src/main/java/com/eduplatform/gateway/filter/RateLimitFilter.java:191`、`backend/gateway/src/main/java/com/eduplatform/gateway/config/RateLimiterConfig.java:37`、`backend/gateway/src/main/resources/application.yml:175` |
| BKR-P2-010 | 已修复 | 学情分析等级文案统一为 UTF-8 可读中文，并增加回归测试防止乱码文本回退。 | `backend/progress-service/src/main/java/com/eduplatform/progress/service/ProgressAnalyticsService.java:109`、`backend/progress-service/src/test/java/com/eduplatform/progress/service/ProgressAnalyticsServiceTest.java:45` |
| BKR-P2-011 | 已修复 | 已完成三大超大类按职责拆分：`HomeworkService` 写流程下沉到 `HomeworkAuthoringService/HomeworkSubmissionService`，`CourseService` 审核状态机下沉到 `CourseWorkflowService`，`ProgressService` 写流程下沉到 `ProgressTrackingService`；主服务仅保留聚合与兼容入口，复杂度显著下降（`HomeworkService` 349 行、`CourseService` 327 行、`ProgressService` 179 行）。 | `backend/homework-service/src/main/java/com/eduplatform/homework/service/HomeworkService.java:134`、`backend/homework-service/src/main/java/com/eduplatform/homework/service/HomeworkService.java:225`、`backend/course-service/src/main/java/com/eduplatform/course/service/CourseService.java:201`、`backend/course-service/src/main/java/com/eduplatform/course/service/CourseWorkflowService.java:21`、`backend/progress-service/src/main/java/com/eduplatform/progress/service/ProgressService.java:62`、`backend/progress-service/src/main/java/com/eduplatform/progress/service/ProgressTrackingService.java:42` |
| BKR-P2-012 | 已修复 | 已建立“控制器权限 + DTO 校验 + 服务委托 + 工作流行为”回归网：新增 `CourseWorkflowServiceTest` 与 `ProgressService` 写流程委托测试，完善 `CourseService/HomeworkService` 委托验证，叠加既有网关会话与用户权限回归；关键模块及全仓 `test/clean test` 均稳定通过。 | `backend/course-service/src/test/java/com/eduplatform/course/service/CourseServiceTest.java:107`、`backend/course-service/src/test/java/com/eduplatform/course/service/CourseWorkflowServiceTest.java:26`、`backend/homework-service/src/test/java/com/eduplatform/homework/service/HomeworkServiceTest.java:190`、`backend/progress-service/src/test/java/com/eduplatform/progress/service/ProgressServiceTest.java:226`、`backend/user-service/src/test/java/com/eduplatform/user/controller/AuthControllerTest.java:29`、`backend/gateway/src/test/java/com/eduplatform/gateway/filter/JwtAuthFilterTest.java:1` |

### 最新验证证据（2026-02-21）
- `mvn -T 1C -pl gateway -Dtest=RateLimitFilterTest test`：通过（6 tests, 0 failures）。
- `mvn -T 1C -pl gateway -Dtest=JwtAuthFilterTest test`：通过（4 tests, 0 failures）。
- `mvn -T 1C -pl user-service -Dtest=AuthControllerTest test`：通过（9 tests, 0 failures）。
- `mvn -T 1C -pl homework-service -am test "-Dtest=HomeworkServiceTest" "-Dsurefire.failIfNoSpecifiedTests=false"`：通过（20 tests, 0 failures）。
- `mvn -T 1C -pl homework-service -am test`：通过（`homework-service` 模块 39 tests, 0 failures）。
- `mvn -T 1C -pl course-service -am test "-Dtest=CourseServiceTest,CourseWorkflowServiceTest" "-Dsurefire.failIfNoSpecifiedTests=false"`：通过（9 tests, 0 failures）。
- `mvn -T 1C -pl progress-service -am test "-Dtest=ProgressServiceTest" "-Dsurefire.failIfNoSpecifiedTests=false"`：通过（10 tests, 0 failures）。
- `mvn -T 1C -pl course-service,progress-service -am test`：通过（`course/progress` 定向回归 `BUILD SUCCESS`）。
- `mvn -T 1C test`（目录：`backend/`）：通过（Reactor 全模块 `BUILD SUCCESS`）。
- `mvn -T 1C clean test`（目录：`backend/`）：通过（冷启动全模块 `BUILD SUCCESS`）。

## 验证证据（关键命令）
- 命令：`mvn -T 1C clean test`（目录：`backend/`）
- 结果：失败；`common` 模块冷启动编译失败
- 关键错误：`backend/common/src/main/java/com/eduplatform/common/ai/AiService.java:5`、`backend/common/src/main/java/com/eduplatform/common/ai/AiService.java:28` 找不到 `dev.langchain4j.service.Vague`

- 命令：`mvn -T 1C test`（目录：`backend/`）
- 结果：成功；说明存在“增量编译通过、冷启动失败”的可重复构建一致性问题

- 命令：`rg --files backend -g "*Controller.java"`
- 结果：20 个 Controller；其中 6 个无显式鉴权标记（见问题 `BKR-P1-005`）

- 命令：输入校验统计
- 结果：`@RequestBody` 52 处、`@RequestBody Map<...>` 18 处、`@Valid/@Validated` 0 处

- 命令：错误直出统计
- 结果：`Result.(error|fail|failure)(...e.getMessage())` 63 处，`catch (Exception e)` 134 处

## 问题清单（按严重级别排序）

| 编号 | 级别 | 类别 | 证据定位（文件:行） | 风险与影响 | 优化建议 | 回归验证建议 |
|---|---|---|---|---|---|---|
| BKR-P0-001 | P0 | 构建稳定性 | `backend/common/src/main/java/com/eduplatform/common/ai/AiService.java:5`；`backend/common/src/main/java/com/eduplatform/common/ai/AiService.java:28` | 冷启动构建失败，CI/CD 与新环境部署会被阻断 | 移除或替换不存在的 `Vague` 注解为当前 LangChain4j 版本可用写法；将 `clean test` 设为发布前硬门禁 | 连续执行 2 次 `mvn -T 1C clean test` 均成功 |
| BKR-P0-002 | P0 | 认证/会话安全 | `backend/gateway/src/main/java/com/eduplatform/gateway/filter/JwtAuthFilter.java:107`；`backend/gateway/src/main/java/com/eduplatform/gateway/filter/JwtAuthFilter.java:147`；`backend/user-service/src/main/java/com/eduplatform/user/controller/AuthController.java:122`；`backend/user-service/src/main/java/com/eduplatform/user/controller/AuthController.java:139` | 网关仅验 JWT，不验会话 `jti`；登出/强制下线后旧 Token 仍可能在过期前可用 | 在网关鉴权链接入会话有效性校验（调用 user-service 验证或共享会话存储）；失败即拒绝转发 | 登录 -> 调用受保护接口成功；登出 -> 立刻调用同接口应返回 401/403 |
| BKR-P0-003 | P0 | 账号安全 | `backend/gateway/src/main/java/com/eduplatform/gateway/filter/JwtAuthFilter.java:55`；`backend/user-service/src/main/java/com/eduplatform/user/controller/AuthController.java:217`；`backend/user-service/src/main/java/com/eduplatform/user/service/UserService.java:182`；`backend/user-service/src/main/java/com/eduplatform/user/service/UserService.java:192` | 重置密码接口被白名单放行，且仅用邮箱+姓名校验，存在撞库/枚举后接管风险 | 下线白名单直通；改为验证码/一次性令牌/时效校验/频控；统一“已受理”响应防枚举 | 对同邮箱高频请求触发限流；错误身份信息返回统一文案且不暴露存在性 |
| BKR-P0-004 | P0 | 配置安全 | `backend/user-service/src/main/resources/application.yml:96`；`backend/user-service/src/main/java/com/eduplatform/user/UserServiceApplication.java:53`；`backend/user-service/src/main/java/com/eduplatform/user/UserServiceApplication.java:55` | 默认启用管理员引导且固定口令，环境误配即形成已知凭据后门 | 默认关闭引导（生产必须 false）；管理员初始密码改为启动时一次性随机并强制首登改密 | 启动生产配置后不应自动创建固定口令管理员 |
| BKR-P1-005 | P1 | 鉴权/授权 | `backend/course-service/src/main/java/com/eduplatform/course/controller/ChapterController.java:34`；`backend/course-service/src/main/java/com/eduplatform/course/controller/FileUploadController.java:27`；`backend/homework-service/src/main/java/com/eduplatform/homework/controller/DiscussionController.java:30`；`backend/user-service/src/main/java/com/eduplatform/user/controller/AuditLogController.java:40`；`backend/user-service/src/main/java/com/eduplatform/user/controller/StatsController.java:31`；`backend/progress-service/src/main/java/com/eduplatform/progress/controller/StatsController.java:23` | 6 个控制器无显式角色/身份校验，部分接口包含写操作与管理视图，存在越权面 | 对敏感接口补齐角色守卫（管理员/教师/本人）；统一封装授权校验器，避免散落判断遗漏 | 低权限账号访问管理接口应稳定返回 403；本人/管理员路径正确通过 |
| BKR-P1-006 | P1 | 审计可信性 | `backend/user-service/src/main/java/com/eduplatform/user/controller/AuditLogController.java:82`；`backend/user-service/src/main/java/com/eduplatform/user/controller/AuditLogController.java:83`；`backend/user-service/src/main/java/com/eduplatform/user/controller/AuditLogController.java:84` | 审计写入接口直接信任请求体中的 `operatorId/operatorName`，可被伪造污染审计链路 | 操作人信息仅取网关注入头；请求体禁止覆盖操作者身份；对外仅开放内部调用路径 | 构造伪造 operatorId 请求应被拒绝或被服务端覆盖为真实身份 |
| BKR-P1-007 | P1 | 输入校验 | `backend/user-service/src/main/java/com/eduplatform/user/dto/ResetPasswordRequest.java:7`；`backend/homework-service/src/main/java/com/eduplatform/homework/controller/DiscussionController.java:87`；`backend/user-service/src/main/java/com/eduplatform/user/controller/NotificationController.java:36` | 全仓 `@Valid/@Validated` 为 0，`Map` 动态入参 18 处，字段必填/边界/类型约束不足，易引发脏数据和异常路径 | 将动态 Map 收敛为 DTO；统一 Bean Validation；在全局异常处理返回标准化 4xx 校验错误 | 非法字段、缺失字段、越界字段均返回稳定 400，且错误码可机器识别 |
| BKR-P1-008 | P1 | 错误信息泄露 | `backend/common/src/main/java/com/eduplatform/common/exception/GlobalExceptionHandler.java:51`；`backend/course-service/src/main/java/com/eduplatform/course/controller/CourseController.java:244`；`backend/homework-service/src/main/java/com/eduplatform/homework/controller/HomeworkController.java:524` | 多处将 `e.getMessage()` 直接返回前端，泄露内部实现细节并放大探测面 | 对外统一通用错误文案 + 错误码；详细异常仅记录日志并挂 traceId | 触发业务异常时响应不应包含内部堆栈/SQL/实现细节 |
| BKR-P2-009 | P2 | 限流策略 | `backend/gateway/src/main/java/com/eduplatform/gateway/filter/RateLimitFilter.java:47`；`backend/gateway/src/main/java/com/eduplatform/gateway/filter/RateLimitFilter.java:24`；`backend/gateway/src/main/resources/application.yml:156` | 限流 key 直接信任 `X-Forwarded-For`，存在伪造绕过风险；且本地内存限流在多实例下不一致 | 仅信任受控代理链头；采用 Redis/Bucket4j 等集中式限流；对高危接口单独收紧阈值 | 伪造 XFF 不应绕过限流；多实例网关下限流计数保持一致 |
| BKR-P2-010 | P2 | 编码一致性 | `backend/progress-service/src/main/java/com/eduplatform/progress/service/ProgressService.java:35`；`backend/progress-service/src/main/java/com/eduplatform/progress/service/ProgressService.java:448`；`backend/progress-service/src/main/java/com/eduplatform/progress/service/ProgressService.java:457` | 存在明显乱码注释与日志，影响可读性和排障效率，违反 UTF-8 规范约束 | 统一文件编码 UTF-8，清理乱码文本并建立提交前编码检查 | 随机抽查日志关键路径文本应可读、无乱码 |
| BKR-P2-011 | P2 | 可维护性 | `backend/homework-service/src/main/java/com/eduplatform/homework/service/HomeworkService.java:1`；`backend/progress-service/src/main/java/com/eduplatform/progress/service/ProgressService.java:1`；`backend/course-service/src/main/java/com/eduplatform/course/service/CourseService.java:1` | 超大类（1296/913/789 行）职责过载，变更冲突率高、回归风险高 | 按领域拆分服务（查询/命令/事件/聚合）；提炼共享校验与装配逻辑 | 拆分后单类复杂度下降，核心路径单测可独立覆盖 |
| BKR-P2-012 | P2 | 测试充分性 | `backend/user-service/src/test/java/com/eduplatform/user/service/UserServiceTest.java:1`；`backend/course-service/src/test/java/com/eduplatform/course/service/CourseServiceTest.java:1` | 测试文件仅 6 个，且均为服务层；Controller/Security/集成回归为 0，权限与异常路径缺乏保护网 | 增补控制器鉴权测试、会话失效链路测试、重置密码安全测试、统一异常响应测试 | 新增测试后应覆盖关键 P0/P1 场景并进入 CI 强制门禁 |

## 公共 API / 接口 / 类型变更评估
| 变更项 | 影响接口 | 兼容影响 | 建议发布策略 |
|---|---|---|---|
| 补齐角色与身份校验 | `api/chapters`、`api/files`、`api/discussions`、`api/audit-logs`、`api/stats/**` | 低权限调用将新增 401/403 | 先灰度后全量，向前端同步权限矩阵 |
| 密码重置安全升级 | `/api/auth/reset-password` | 请求参数与交互流程将变化（引入验证码/一次性令牌） | 增加 V2 接口并保留短期兼容窗 |
| Map 入参收敛 DTO + Validation | 多个 `@RequestBody Map<...>` 接口 | 400 校验错误会更严格 | 提供字段迁移文档与错误码说明 |
| 错误响应标准化 | 全部错误返回 | 文案会变，错误码更稳定 | 发布统一错误码字典，保留 traceId |

## 先修清单（72 小时）
1. 修复 `AiService` 编译阻断，恢复 `clean test` 稳定通过。
2. 下线重置密码白名单直通，临时加网关限流与验证码校验。
3. 在网关鉴权链补齐 `jti` 会话有效性校验。
4. 关闭默认管理员引导，移除固定初始口令。
5. 为 6 个无显式鉴权控制器补齐最小权限守卫。

## 中期治理清单（2~4 周）
1. 全面把 `Map` 请求体迁移为 DTO + Bean Validation。
2. 统一异常处理：去除 `e.getMessage()` 外露，建立错误码体系。
3. 网关限流改为集中式（Redis/Bucket4j）并支持高危接口专属策略。
4. 拆分超大服务类，降低耦合并补齐回归单测。
5. 建立安全回归测试集：鉴权、会话失效、密码重置、审计防伪造。

## 覆盖说明与剩余风险
- 本次已覆盖 20/20 Controller、核心 Filter/Config、关键 Service 与部署配置。
- 由于当前测试以服务层为主，仍存在“接口层与安全链路”回归盲区；建议在修复 P0/P1 同步补齐接口级自动化测试。
