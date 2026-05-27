$ErrorActionPreference = "Stop"
$LogFile = "$PSScriptRoot\docker-cleanup.log"

function Write-Log {
    param($Message)
    $timestamp = Get-Date -Format "yyyy-MM-dd HH:mm:ss"
    "$timestamp | $Message" | Tee-Object -FilePath $LogFile -Append
}

Write-Log "=== Docker Cleanup Started ==="

$Before = docker system df 2>&1
Write-Log "Before:`n$Before"

Write-Log "Step 1: Pruning build cache..."
$result1 = docker builder prune -af 2>&1
Write-Log "Build cache result: $($result1 -join '; ')"

Write-Log "Step 2: Pruning dangling images..."
$result2 = docker image prune -f 2>&1
Write-Log "Dangling images result: $($result2 -join '; ')"

Write-Log "Step 3: Pruning unused images (>7 days old)..."
$result3 = docker image prune -a -f --filter "until=168h" 2>&1
Write-Log "Unused images result: $($result3 -join '; ')"

Write-Log "Step 4: Pruning unused volumes..."
$result4 = docker volume prune -f 2>&1
Write-Log "Volume prune result: $($result4 -join '; ')"

$After = docker system df 2>&1
Write-Log "After:`n$After"

$reclaimed = if ($result1 -match 'Total reclaimed space: (\S+)') { $matches[1] } else { "N/A" }
Write-Log "=== Done. Reclaimed: $reclaimed ==="