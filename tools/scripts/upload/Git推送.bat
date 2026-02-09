@echo off
setlocal
chcp 65001 >nul

rem ???????????????
set "SCRIPT_DIR=%~dp0"
set "PS1_SCRIPT="

rem ??????????????????????????? Git*.ps1
for %%I in ("%SCRIPT_DIR%Git*.ps1") do (
  set "PS1_SCRIPT=%%~fI"
  goto :found_ps1
)

:found_ps1
if "%PS1_SCRIPT%"=="" (
  echo [error] cannot find powershell script: Git*.ps1
  pause
  exit /b 2
)

powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%PS1_SCRIPT%" %*

set "ERR=%ERRORLEVEL%"
if not "%ERR%"=="0" (
  echo.
  echo [error] upload script failed, code: %ERR%
  pause
)
exit /b %ERR%
