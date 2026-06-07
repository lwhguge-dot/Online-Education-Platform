# verify-retain.ps1
# Check if Hindsight retain was called in the last N minutes
# Usage: .\verify-retain.ps1 [-MinutesAgo 10]

param([int]$MinutesAgo = 10)

$ErrorActionPreference = "Stop"
$HindsightUrl = "http://localhost:8888"
$BankId = "deepseek-v2"

Write-Host "=== Hindsight Retain Check ===" -ForegroundColor Cyan
Write-Host "Window: last $MinutesAgo minutes" -ForegroundColor Cyan
Write-Host ""

Write-Host "Checking Hindsight health..." -ForegroundColor Cyan
$health = & curl.exe -s "$HindsightUrl/health" 2>&1
if ($LASTEXITCODE -ne 0 -or $health -notmatch "healthy") {
    Write-Host "FAIL: Hindsight not reachable at $HindsightUrl" -ForegroundColor Red
    exit 1
}
Write-Host "OK: Hindsight healthy" -ForegroundColor Green

Write-Host ""
Write-Host "Searching recent memories..." -ForegroundColor Cyan
$body = @{ query = "recent memory"; budget = "low"; max_tokens = 512 } | ConvertTo-Json -Compress
try {
    $data = Invoke-RestMethod -Uri "$HindsightUrl/v1/default/banks/$BankId/memories/recall" `
        -Method Post -ContentType "application/json" -Body $body
} catch {
    Write-Host "FAIL: Cannot query memories: $_" -ForegroundColor Red
    exit 1
}

$results = $data.results
if (-not $results -or $results.Count -eq 0) {
    Write-Host "WARN: No memories found. AI may have never called retain." -ForegroundColor Yellow
    exit 2
}

Write-Host "Recent $($results.Count) memory results:" -ForegroundColor Cyan
$foundRecent = $false
foreach ($r in $results) {
    $text = if ($r.text.Length -gt 100) { $r.text.Substring(0, 100) + "..." } else { $r.text }
    $time = if ($r.mentioned_at) { $r.mentioned_at } else { "N/A" }
    Write-Host "  [$time] $text" -ForegroundColor DarkGray

    if ($r.mentioned_at) {
        try {
            $memTime = [datetime]::Parse($r.mentioned_at, [System.Globalization.CultureInfo]::InvariantCulture,
                [System.Globalization.DateTimeStyles]::AssumeUniversal -bor [System.Globalization.DateTimeStyles]::AdjustToUniversal)
            $elapsed = [datetime]::UtcNow - $memTime
            if ($elapsed.TotalMinutes -le $MinutesAgo) {
                $foundRecent = $true
            }
        } catch {}
    }
}

Write-Host ""
if ($foundRecent) {
    Write-Host "PASS: retain OK - memories found within ${MinutesAgo}min window" -ForegroundColor Green
    exit 0
} else {
    Write-Host "FAIL: No memory within ${MinutesAgo}min. AI may have forgotten retain." -ForegroundColor Red
    exit 2
}