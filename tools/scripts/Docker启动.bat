@echo off
chcp 65001 >nul
echo ========================================
echo    智慧课堂 - Docker 一键部署 (增强版)
echo ========================================
echo.
echo    技术栈更新:
echo    - 数据库: MySQL 8.0 → PostgreSQL 16
echo    - 监控: Prometheus + Grafana + Jaeger
echo    - 错误追踪: Sentry
echo    - 前端: TypeScript
echo    - 对象存储: MinIO
echo ========================================
echo.

echo [1/7] 正在检查 Docker 运行状态...
docker info >nul 2>&1
if %errorlevel% neq 0 (
    echo [错误] Docker 未启动，请先启动 Docker Desktop 再运行本脚本。
    pause
    exit /b
)
echo [✓] Docker 运行正常

echo.
echo [2/7] 正在清理无用镜像以释放空间...
docker image prune -f >nul 2>&1
echo [✓] 镜像清理完成

echo.
echo [3/7] 正在停止旧的后端服务容器...
docker-compose stop gateway user-service course-service homework-service progress-service frontend >nul 2>&1
echo [✓] 后端服务已停止

echo.
echo [4/7] 正在启动基础设施 (PostgreSQL + Redis + Nacos + MinIO + Sentinel)...
docker-compose up -d postgres redis nacos minio sentinel
if %errorlevel% neq 0 (
    echo [错误] 基础设施启动失败，请检查端口占用或配置。
    pause
    exit /b %errorlevel%
)
echo [✓] 基础设施启动成功

echo.
echo [5/7] 正在启动监控服务 (Prometheus + Grafana + Jaeger)...
docker-compose up -d prometheus grafana jaeger
if %errorlevel% neq 0 (
    echo [错误] 监控服务启动失败，请检查配置文件。
    pause
    exit /b %errorlevel%
)
echo [✓] 监控服务启动成功

echo.
echo [6/7] 等待基础设施健康检查 (约30秒)...
timeout /t 30 /nobreak >nul
echo [✓] 基础设施就绪

echo.
echo [7/7] 正在启动业务服务 (Gateway + 4个微服务 + Frontend)...
echo       注意: 第一次构建需要下载依赖，可能需要5-10分钟，请耐心等待。
docker-compose up -d --build gateway user-service course-service homework-service progress-service frontend
if %errorlevel% neq 0 (
    echo [错误] 业务服务启动失败，请检查日志: docker-compose logs
    pause
    exit /b %errorlevel%
)

echo.
echo ========================================
echo [✓] 所有服务已部署成功！
echo ========================================
echo.
echo 📊 基础设施服务:
echo    ├─ PostgreSQL:     localhost:5432 (用户: postgres, 密码: 123456)
echo    ├─ Redis:          localhost:16379 (密码: 123456)
echo    ├─ Nacos控制台:    http://localhost:8848/nacos (用户: nacos, 密码: nacos)
echo    ├─ Sentinel控制台: http://localhost:8858 (用户: sentinel, 密码: sentinel)
echo    ├─ MinIO控制台:    http://localhost:9001 (用户: minioadmin, 密码: minioadmin)
echo    └─ MinIO API:      http://localhost:9000
echo.
echo 🔍 监控与追踪服务:
echo    ├─ Prometheus:     http://localhost:9090
echo    ├─ Grafana:        http://localhost:3000 (用户: admin, 密码: admin)
echo    ├─ Jaeger UI:      http://localhost:16686
echo    └─ Sentry:         https://sentry.io (已集成前端错误监控)
echo.
echo 🚀 业务服务:
echo    ├─ API网关:        http://localhost:8090
echo    ├─ 前端访问地址:   http://localhost
echo    └─ 进度服务:       http://localhost:8084
echo.
echo 📝 常用命令:
echo    ├─ 查看所有服务:   docker-compose ps
echo    ├─ 查看日志:       docker-compose logs -f [服务名]
echo    ├─ 重启服务:       docker-compose restart [服务名]
echo    ├─ 停止所有:       docker-compose down
echo    └─ 查看资源占用:   docker stats
echo.
echo 💡 提示:
echo    - 前端开发服务器支持热更新
echo    - 所有微服务已注册到 Nacos
echo    - 监控指标已配置到 Prometheus
echo    - 分布式追踪已集成 Jaeger
echo.
echo ========================================
pause
