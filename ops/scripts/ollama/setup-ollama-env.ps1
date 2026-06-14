Write-Host "正在设置 OLLAMA_KEEP_ALIVE 环境变量..." -ForegroundColor Cyan

try {
    [Environment]::SetEnvironmentVariable("OLLAMA_KEEP_ALIVE", "-1", "User")
    Write-Host "✓ OLLAMA_KEEP_ALIVE = -1 已设置 (用户级)" -ForegroundColor Green

    $currentValue = [Environment]::GetEnvironmentVariable("OLLAMA_KEEP_ALIVE", "User")
    Write-Host "当前值: OLLAMA_KEEP_ALIVE = $currentValue" -ForegroundColor Yellow
} catch {
    Write-Host "✗ 设置失败: $_" -ForegroundColor Red
    exit 1
}

Write-Host ""
Write-Host "⚠ 重要提示: 请重启 Ollama 服务以使环境变量生效" -ForegroundColor Magenta
Write-Host "  - Windows 托盘: 右键 Ollama 图标 → Quit → 重新启动 Ollama" -ForegroundColor Magenta
Write-Host "  - 或执行命令: taskkill /f /im ollama.exe && start ollama app.exe" -ForegroundColor Magenta