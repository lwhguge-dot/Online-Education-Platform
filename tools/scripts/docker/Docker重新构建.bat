@echo off
setlocal
chcp 65001 >nul

set "SCRIPT_DIR=%~dp0"
set "PS1_PATH=%SCRIPT_DIR%rebuild.ps1"

if not exist "%PS1_PATH%" (
  echo [Error] Script not found: %PS1_PATH%
  pause
  exit /b 1
)

powershell.exe -NoLogo -NoProfile -ExecutionPolicy Bypass -File "%PS1_PATH%" %*
set "ERR=%ERRORLEVEL%"

if not "%ERR%"=="0" (
  echo.
  echo [Error] Script failed with exit code: %ERR%
  pause
  exit /b %ERR%
)

echo [Done] Execution finished successfully.
pause
exit /b 0