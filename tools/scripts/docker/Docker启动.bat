@echo off
setlocal
chcp 65001 >nul

REM 统一定位到当前 bat 所在目录，避免从任意工作目录调用时找不到 ps1
set "SCRIPT_DIR=%~dp0"
set "PS1_PATH=%SCRIPT_DIR%Docker启动.ps1"

REM 启动前先校验 ps1 是否存在，避免报错信息不明确
if not exist "%PS1_PATH%" (
  echo.
  echo [错误] 未找到脚本：%PS1_PATH%
  pause
  exit /b 1
)

REM 将 bat 接收到的参数原样透传给 PowerShell 启动脚本
powershell.exe -NoLogo -NoProfile -ExecutionPolicy Bypass -File "%PS1_PATH%" %*
set "ERR=%ERRORLEVEL%"
if not "%ERR%"=="0" (
  echo.
  echo [错误] Docker 启动脚本执行失败，错误码: %ERR%
  pause
  exit /b %ERR%
)

echo [完成] Docker 启动脚本执行成功。
exit /b 0
