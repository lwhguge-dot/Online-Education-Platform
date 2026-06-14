$ErrorActionPreference = "Stop"
$OLLAMA_HOST = if ($env:OLLAMA_HOST) { $env:OLLAMA_HOST } else { "localhost:11434" }
if ($OLLAMA_HOST -like "0.0.0.0*") {
    $OLLAMA_HOST = $OLLAMA_HOST -replace "0.0.0.0", "127.0.0.1"
}
$env:OLLAMA_HOST = $OLLAMA_HOST
$MAX_RETRIES = 30
$RETRY_DELAY_SEC = 2
$MODEL = "qwen2.5:3b"


Write-Host "========================================" -ForegroundColor Cyan
Write-Host "  Ollama Model Preload" -ForegroundColor Cyan
Write-Host "  Model: $MODEL" -ForegroundColor Cyan
Write-Host "  Target: $OLLAMA_HOST" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""

Write-Host "[1/3] Checking Ollama service..." -ForegroundColor Yellow
$ready = $false
for ($i = 1; $i -le $MAX_RETRIES; $i++) {
    $result = ollama list 2>&1 | Out-String
    if ($LASTEXITCODE -eq 0) {
        Write-Host "  OK Ollama ready (attempt $i/$MAX_RETRIES)" -ForegroundColor Green
        $ready = $true
        break
    }
    if ($i -lt $MAX_RETRIES) {
        Write-Host "  ... waiting ($i/$MAX_RETRIES), retry in ${RETRY_DELAY_SEC}s" -ForegroundColor Gray
        Start-Sleep -Seconds $RETRY_DELAY_SEC
    }
}

if (-not $ready) {
    Write-Host "  FAIL Ollama not ready after $MAX_RETRIES attempts" -ForegroundColor Red
    exit 1
}

Write-Host ""
Write-Host "[2/3] Preloading model $MODEL ..." -ForegroundColor Yellow
$body = @{ model = $MODEL; prompt = ""; stream = $false } | ConvertTo-Json -Compress
try {
    $null = Invoke-RestMethod -Uri "http://${OLLAMA_HOST}/api/generate" `
        -Method Post -Body $body -ContentType "application/json" -TimeoutSec 120
    Write-Host "  OK Model $MODEL preloaded" -ForegroundColor Green
} catch {
    Write-Host "  FAIL Model preload failed: $_" -ForegroundColor Red
    exit 1
}

Write-Host ""
Write-Host "[3/3] Verifying model status..." -ForegroundColor Yellow
$psResult = ollama ps 2>&1 | Out-String
if ($LASTEXITCODE -eq 0) {
    Write-Host "  OK Model running:" -ForegroundColor Green
    Write-Host $psResult
} else {
    Write-Host "  WARN ollama ps returned non-zero, but preload completed" -ForegroundColor Yellow
}

Write-Host ""
Write-Host "========================================" -ForegroundColor Cyan
Write-Host "  Done - Model $MODEL is ready" -ForegroundColor Green
Write-Host "========================================" -ForegroundColor Cyan