@echo off
chcp 65001 >nul
title Git 一键推送到 GitHub

echo ==============================
echo   Git 一键推送到 GitHub
echo ==============================
echo.

cd /d "%~dp0..\.."

:: 1. 检查 Git 状态
echo [1/5] 检查文件变动...
echo.
git status --short
echo.

:: 检查是否有变动
git diff --quiet --exit-code >nul 2>&1
set UNSTAGED=%ERRORLEVEL%
git diff --quiet --cached --exit-code >nul 2>&1
set STAGED=%ERRORLEVEL%
git ls-files --others --exclude-standard >nul 2>&1
for /f %%i in ('git ls-files --others --exclude-standard ^| find /c /v ""') do set UNTRACKED=%%i

if %UNSTAGED%==0 if %STAGED%==0 if %UNTRACKED%==0 (
    echo 没有任何文件变动，无需推送。
    echo.
    pause
    exit /b 0
)

:: 2. 让用户输入提交信息
echo [2/5] 请输入本次改动说明:
echo   示例: fix: 修复登录接口报错
echo   示例: feat: 新增课程搜索功能
echo   示例: docs: 更新README文档
echo.
set /p COMMIT_MSG=请输入:

:: 检查输入是否为空
if "%COMMIT_MSG%"=="" (
    echo.
    echo 提交信息不能为空，已取消。
    pause
    exit /b 1
)

:: 3. 添加所有变动文件
echo.
echo [3/5] 添加变动文件...
git add .

:: 4. 提交
echo [4/5] 提交中...
git commit -m "%COMMIT_MSG%"
if %ERRORLEVEL% neq 0 (
    echo.
    echo 提交失败，请检查错误信息。
    pause
    exit /b 1
)

:: 5. 推送
echo.
echo [5/5] 推送到 GitHub...
git push
if %ERRORLEVEL% neq 0 (
    echo.
    echo 推送失败，可能是网络问题，请稍后重试。
    pause
    exit /b 1
)

echo.
echo ==============================
echo   推送成功!
echo ==============================
echo.
echo 仓库地址: https://github.com/lwhguge-dot/Online-Education-Platform
echo.
pause
