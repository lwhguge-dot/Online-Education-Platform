param(
    [string]$ApiUrl = "http://localhost:8888",
    [string]$BankId = "deepseek-v2",
    [double]$CleanupThreshold = 0.3
)

$ErrorActionPreference = "SilentlyContinue"

function Invoke-HindsightApi {
    param(
        [string]$Endpoint,
        [string]$Method = "GET",
        [object]$Body = $null
    )
    $url = "$ApiUrl/v1/default/banks/$BankId/$Endpoint"
    if ($Method -eq "GET") {
        $response = curl.exe -s "$url" 2>&1
    } else {
        $bodyJson = $Body | ConvertTo-Json -Depth 10
        $response = curl.exe -s -X POST -H "Content-Type: application/json" -d "$bodyJson" "$url" 2>&1
    }
    try {
        return $response | ConvertFrom-Json
    } catch {
        Write-Host "  [WARN] $Endpoint response: $response" -ForegroundColor Yellow
        return $null
    }
}

Write-Host "=== Hindsight Memory Maintenance ===" -ForegroundColor Cyan
Write-Host "Time: $(Get-Date -Format 'yyyy-MM-dd HH:mm:ss')"
Write-Host "API: $ApiUrl | Bank: $BankId"
Write-Host ""

# 1. Health check
Write-Host "[1/4] Health check..." -ForegroundColor Green
$healthResponse = curl.exe -s "$ApiUrl/health" 2>&1
if ($healthResponse -match '"status"\s*:\s*"healthy"') {
    Write-Host "  Status: OK"
} else {
    Write-Host "  API is not healthy, aborting." -ForegroundColor Red
    Write-Host "  Response: $healthResponse" -ForegroundColor Yellow
    exit 1
}

# 2. Stats before
Write-Host "[2/4] Stats before cleanup..." -ForegroundColor Green
$statsBefore = Invoke-HindsightApi -Endpoint "memories/stats"
if ($statsBefore) {
    Write-Host "  Nodes: $($statsBefore.total_nodes) | Links: $($statsBefore.total_links) | Quality: $($statsBefore.quality_score)"
}

# 3. Cleanup low quality
Write-Host "[3/4] Cleanup (threshold=$CleanupThreshold)..." -ForegroundColor Green
$cleanupResult = Invoke-HindsightApi -Endpoint "memories/cleanup" -Method "POST" -Body @{ threshold = $CleanupThreshold }
if ($cleanupResult) {
    $deleted = if ($cleanupResult.deleted_count) { $cleanupResult.deleted_count } else { "unknown" }
    Write-Host "  Cleaned: $deleted memories"
} else {
    Write-Host "  Cleanup completed (async)" -ForegroundColor Yellow
}

# 4. Compress similar
Write-Host "[4/4] Compress similar memories..." -ForegroundColor Green
$compressResult = Invoke-HindsightApi -Endpoint "memories/compress" -Method "POST"
if ($compressResult) {
    $merged = if ($compressResult.merged_count) { $compressResult.merged_count } else { "unknown" }
    Write-Host "  Compressed: $merged pairs"
} else {
    Write-Host "  Compress completed (async)" -ForegroundColor Yellow
}

# 5. Stats after
Write-Host ""
Write-Host "=== Results ===" -ForegroundColor Cyan
$statsAfter = Invoke-HindsightApi -Endpoint "memories/stats"
if ($statsAfter) {
    Write-Host "  Nodes: $($statsAfter.total_nodes) | Links: $($statsAfter.total_links) | Quality: $($statsAfter.quality_score)"
    if ($statsBefore) {
        $nodeDiff = $statsAfter.total_nodes - $statsBefore.total_nodes
        Write-Host "  Change: $nodeDiff nodes" -ForegroundColor $(if ($nodeDiff -lt 0) { "Green" } else { "Yellow" })
    }
}
Write-Host ""
Write-Host "Done." -ForegroundColor Cyan
