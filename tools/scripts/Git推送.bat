@echo off
setlocal EnableDelayedExpansion

title Git Push Helper

echo ==============================
echo   Git Push Helper
echo ==============================
echo.

cd /d "%~dp0..\.."

if exist "%ProgramFiles%\Git\cmd\git.exe" set "PATH=%ProgramFiles%\Git\cmd;%PATH%"
if exist "%ProgramFiles(x86)%\Git\cmd\git.exe" set "PATH=%ProgramFiles(x86)%\Git\cmd;%PATH%"
where git >nul 2>&1
if errorlevel 1 (
    echo [ERROR] Git not found in PATH.
    pause
    exit /b 1
)

echo [1/7] Check file changes...
echo.
git status --short
echo.

git diff --quiet --exit-code >nul 2>&1
set UNSTAGED=%ERRORLEVEL%
git diff --quiet --cached --exit-code >nul 2>&1
set STAGED=%ERRORLEVEL%
for /f %%i in ('git ls-files --others --exclude-standard ^| find /c /v ""') do set UNTRACKED=%%i

if %UNSTAGED%==0 if %STAGED%==0 if %UNTRACKED%==0 (
    echo No file changes. Nothing to push.
    echo.
    pause
    exit /b 0
)

echo [2/7] Auto-generate update summary...
set "HAS_README=0"
set "HAS_DOCKER=0"
set "HAS_SCRIPTS=0"
set "HAS_FRONTEND=0"
set "HAS_BACKEND=0"
set "HAS_OTHER=0"

for /f "usebackq delims=" %%L in (`git status --short`) do (
    set "line=%%L"
    set "file_path=!line:~3!"
    if not "!file_path!"=="" (
        if /I "!file_path:~0,6!"=="README" set HAS_README=1
        if /I "!file_path:~0,14!"=="docker-compose" set HAS_DOCKER=1
        if /I "!file_path:~0,13!"=="tools/scripts" set HAS_SCRIPTS=1
        if /I "!file_path:~0,9!"=="frontend/" set HAS_FRONTEND=1
        if /I "!file_path:~0,8!"=="backend/" set HAS_BACKEND=1
    )
)

if %HAS_README%==0 if %HAS_DOCKER%==0 if %HAS_SCRIPTS%==0 if %HAS_FRONTEND%==0 if %HAS_BACKEND%==0 set HAS_OTHER=1

set "AUTO_SUMMARY="
if %HAS_README%==1 call :append_summary "update docs"
if %HAS_DOCKER%==1 call :append_summary "adjust docker compose config"
if %HAS_SCRIPTS%==1 call :append_summary "update ops scripts"
if %HAS_FRONTEND%==1 call :append_summary "update frontend code/config"
if %HAS_BACKEND%==1 call :append_summary "update backend logic/config"
if %HAS_OTHER%==1 call :append_summary "update project files"
if "%AUTO_SUMMARY%"=="" set "AUTO_SUMMARY=maintenance and fixes"

echo.
echo ===== Auto Summary =====
echo %AUTO_SUMMARY%
echo ========================
echo.

echo [3/7] Choose commit message source:
echo   1. Use auto summary (recommended)
echo   2. Edit auto summary
echo   3. Fully manual input
if not "%~1"=="" (
    set "MSG_MODE=%~1"
    echo Input [1/2/3]: %MSG_MODE%
) else (
    set /p MSG_MODE=Input [1/2/3]: 
)
set "MSG_MODE=%MSG_MODE: =%"
set "MSG_MODE=%MSG_MODE:~0,1%"

set "COMMIT_MSG="
if "%MSG_MODE%"=="1" goto MODE_AUTO
if "%MSG_MODE%"=="2" goto MODE_EDIT
if "%MSG_MODE%"=="3" goto MODE_MANUAL
echo Invalid choice. Use manual input.
goto MODE_MANUAL

:MODE_AUTO
set "COMMIT_MSG=%AUTO_SUMMARY%"
goto MODE_DONE

:MODE_EDIT
if not "%~2"=="" (
    set "COMMIT_MSG=%~2"
    echo Edit and input commit message: %COMMIT_MSG%
) else (
    set /p COMMIT_MSG=Edit and input commit message: 
)
goto MODE_DONE

:MODE_MANUAL
if not "%~2"=="" (
    set "COMMIT_MSG=%~2"
    echo Input commit message: %COMMIT_MSG%
) else (
    set /p COMMIT_MSG=Input commit message: 
)
goto MODE_DONE

:MODE_DONE

if "%COMMIT_MSG%"=="" (
    echo.
    echo Commit message cannot be empty. Cancelled.
    pause
    exit /b 1
)

echo.
echo Commit message to use:
echo %COMMIT_MSG%
echo.
set "CONFIRM_ARG=%~3"
if "%MSG_MODE%"=="1" if "%CONFIRM_ARG%"=="" set "CONFIRM_ARG=%~2"
if not "%CONFIRM_ARG%"=="" (
    set "CONFIRM_MSG=%CONFIRM_ARG%"
    echo Confirm commit and push? [y/N]: %CONFIRM_MSG%
) else (
    set /p CONFIRM_MSG=Confirm commit and push? [y/N]: 
)
if /I not "%CONFIRM_MSG%"=="Y" (
    echo Cancelled.
    pause
    exit /b 0
)

echo.
echo [4/7] Add changed files...
git add .

echo [5/7] Commit...
git commit -m "%COMMIT_MSG%"
if %ERRORLEVEL% neq 0 (
    echo.
    echo Commit failed. Please check error details.
    pause
    exit /b 1
)

echo.
echo [6/7] Push to GitHub...
git push
if %ERRORLEVEL% neq 0 (
    echo.
    echo Push failed. Check network or remote conflicts.
    pause
    exit /b 1
)

echo.
echo [7/7] Done.
echo ==============================
echo   Push Success
echo ==============================
echo Repo: https://github.com/lwhguge-dot/Online-Education-Platform
echo.
pause
exit /b 0

:append_summary
set "item=%~1"
if "%AUTO_SUMMARY%"=="" (
    set "AUTO_SUMMARY=%item%"
) else (
    set "AUTO_SUMMARY=%AUTO_SUMMARY%; %item%"
)
exit /b 0
