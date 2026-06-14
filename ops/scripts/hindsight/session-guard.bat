@echo off
REM Hindsight Session Guard - auto-saves session memory on IDE close

setlocal enabledelayedexpansion

set SCRIPT_DIR=%~dp0
set PROJECT_DIR=%SCRIPT_DIR%..\..\..
set LOG_DIR=%PROJECT_DIR%\logs

if not exist "%LOG_DIR%" mkdir "%LOG_DIR%"

echo [%date% %time%] Session guard triggered >> "%LOG_DIR%\session-close.log"

cd /d "%PROJECT_DIR%"
uv run ops\scripts\hindsight\save-session-memory.py --final --bank-id deepseek-v2 >> "%LOG_DIR%\session-close.log" 2>&1


if %ERRORLEVEL% EQU 0 (
    echo [%date% %time%] Session memory saved successfully >> "%LOG_DIR%\session-close.log"
) else (
    echo [%date% %time%] Session memory save FAILED (exit code: %ERRORLEVEL%) >> "%LOG_DIR%\session-close.log"
)

endlocal