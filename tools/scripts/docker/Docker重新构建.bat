@echo off
setlocal EnableExtensions

REM Keep launcher ASCII-only for cmd compatibility.
set "PS1_PATH=%~dpn0.ps1"
if not exist "%PS1_PATH%" set "PS1_PATH=%~dp0rebuild.ps1"

if not exist "%PS1_PATH%" (
  echo.
  echo [ERROR] Script not found: %PS1_PATH%
  pause
  exit /b 1
)

powershell.exe -NoLogo -NoProfile -ExecutionPolicy Bypass -File "%PS1_PATH%" %*
set "ERR=%ERRORLEVEL%"

if not "%ERR%"=="0" (
  echo.
  echo [ERROR] Script failed with exit code: %ERR%
  pause
  exit /b %ERR%
)

echo [DONE] Execution finished successfully.
pause
exit /b 0
