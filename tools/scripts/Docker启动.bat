@echo off
setlocal

title Smart Classroom - Docker Start

set "COMPOSE_CMD=docker compose"
set "SCRIPT_DIR=%~dp0"
for %%I in ("%SCRIPT_DIR%..\..") do set "PROJECT_ROOT=%%~fI"

cd /d "%PROJECT_ROOT%"

echo ========================================
echo Smart Classroom - Docker Start Script
echo ========================================
echo.

echo [1/8] Check Docker daemon...
docker info >nul 2>&1
if errorlevel 1 (
  echo [ERROR] Docker is not running. Start Docker Desktop first.
  pause
  exit /b 1
)
echo [OK] Docker daemon ready.
echo.

echo [2/8] Check docker compose...
%COMPOSE_CMD% version >nul 2>&1
if errorlevel 1 (
  echo [ERROR] docker compose v2 is not available.
  pause
  exit /b 1
)
echo [OK] docker compose is available.
echo.

echo [3/8] Validate compose file...
%COMPOSE_CMD% -f docker-compose.yml config >nul
if errorlevel 1 (
  echo [ERROR] docker-compose.yml validation failed.
  pause
  exit /b 1
)
echo [OK] docker-compose.yml is valid.
echo.

echo [4/8] Stop old app containers if any...
%COMPOSE_CMD% stop gateway user-service course-service homework-service progress-service frontend >nul 2>&1
echo [OK] Previous app containers handled.
echo.

echo [5/8] Start infrastructure services...
%COMPOSE_CMD% up -d postgres redis nacos minio sentinel
if errorlevel 1 (
  echo [ERROR] Failed to start infrastructure services.
  pause
  exit /b 1
)
echo [OK] Infrastructure started.
echo.

echo [6/8] Start observability services...
%COMPOSE_CMD% up -d prometheus grafana jaeger
if errorlevel 1 (
  echo [ERROR] Failed to start observability services.
  pause
  exit /b 1
)
echo [OK] Observability started.
echo.

echo [7/8] Wait for health checks (30s)...
timeout /t 30 /nobreak >nul
echo [OK] Wait finished.
echo.

echo [8/8] Start application services...
%COMPOSE_CMD% up -d --build gateway user-service course-service homework-service progress-service frontend
if errorlevel 1 (
  echo [ERROR] Failed to start application services.
  echo        Check logs: %COMPOSE_CMD% logs -f [service]
  pause
  exit /b 1
)

echo.
echo ========================================
echo [DONE] All services started.
echo ========================================
echo Frontend:   http://localhost
echo API:        http://localhost:8090
echo Nacos:      http://localhost:8848/nacos
echo Sentinel:   http://localhost:8858
echo MinIO:      http://localhost:9001
echo Prometheus: http://localhost:9090
echo Grafana:    http://localhost:3000
echo Jaeger:     http://localhost:16686
echo.
echo Useful commands:
echo   %COMPOSE_CMD% ps
echo   %COMPOSE_CMD% logs -f [service]
echo   %COMPOSE_CMD% restart [service]
echo   %COMPOSE_CMD% down
echo.
pause

