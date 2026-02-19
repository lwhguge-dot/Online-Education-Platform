@echo off
setlocal EnableExtensions

REM Keep this launcher ASCII-only for cmd.exe compatibility.
REM Resolve the PowerShell script by using the same basename.
set "PS1_PATH=%~dpn0.ps1"
if not exist "%PS1_PATH%" (
  set "PS1_FALLBACK="
  for %%I in ("%~dp0*.ps1") do (
    if /I not "%%~nxI"=="rebuild.ps1" if not defined PS1_FALLBACK set "PS1_FALLBACK=%%~fI"
  )
  if defined PS1_FALLBACK set "PS1_PATH=%PS1_FALLBACK%"
)

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
  echo [ERROR] Docker startup script failed, exit code: %ERR%
  pause
  exit /b %ERR%
)

echo [DONE] Docker startup script finished successfully.
exit /b 0
