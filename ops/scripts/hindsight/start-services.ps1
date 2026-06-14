param(
    [switch]$SkipMaintenance
)

Write-Host "=== Starting Services ===" -ForegroundColor Cyan
Write-Host "Time: $(Get-Date -Format 'yyyy-MM-dd HH:mm:ss')"
Write-Host ""

# 1. Start Docker
Write-Host "[1/3] Starting Docker Desktop..." -ForegroundColor Green
$dockerRunning = Get-Process "Docker Desktop" -ErrorAction SilentlyContinue
if ($dockerRunning) {
    Write-Host "  Docker Desktop is already running"
} else {
    Start-Process "C:\Program Files\Docker\Docker\Docker Desktop.exe" -WindowStyle Minimized
    Write-Host "  Docker Desktop started (waiting 30s for init)..."
    Start-Sleep -Seconds 30
}

# 2. Start Ollama
Write-Host "[2/3] Starting Ollama..." -ForegroundColor Green
$ollamaRunning = Get-Process "ollama" -ErrorAction SilentlyContinue
if ($ollamaRunning) {
    Write-Host "  Ollama is already running"
} else {
    Start-Process "ollama" -ArgumentList "serve" -WindowStyle Minimized
    Write-Host "  Ollama started (waiting 5s for init)..."
    Start-Sleep -Seconds 5
}

# 3. Run maintenance
if (-not $SkipMaintenance) {
    Write-Host "[3/3] Running Hindsight maintenance..." -ForegroundColor Green
    & "$PSScriptRoot\hindsight-maintenance.ps1"
} else {
    Write-Host "[3/3] Skipping maintenance (use -SkipMaintenance:`$false to enable)" -ForegroundColor Yellow
}

Write-Host ""
Write-Host "=== All Services Started ===" -ForegroundColor Cyan
