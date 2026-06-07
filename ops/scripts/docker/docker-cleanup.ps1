param(
  [switch]$DryRun
)

$ErrorActionPreference = "Stop"
$LogFile = "$PSScriptRoot\docker-cleanup.log"

function Write-Log {
    param($Message)
    $timestamp = Get-Date -Format "yyyy-MM-dd HH:mm:ss"
    "$timestamp | $Message" | Tee-Object -FilePath $LogFile -Append
}

function Invoke-PruneStep {
    param(
        [string]$StepLabel,
        [string]$LogKey,
        [scriptblock]$Command
    )

    Write-Log $StepLabel
    if ($DryRun) {
        Write-Log "[DRY-RUN] Skipped: $LogKey"
        return "N/A"
    }

    $result = & $Command 2>&1
    Write-Log "$LogKey result: $($result -join '; ')"
    if ($result -match 'Total reclaimed space: (\S+)') { return $matches[1] }
    return "N/A"
}

Write-Log "=== Docker Cleanup Started $(if ($DryRun) { '(DRY-RUN mode)' }) ==="

$Before = docker system df 2>&1
Write-Log "Before:`n$Before"

$reclaimed1 = Invoke-PruneStep "Step 1: Pruning build cache..." "Build cache" {
    docker builder prune -af
}

$reclaimed2 = Invoke-PruneStep "Step 2: Pruning dangling images..." "Dangling images" {
    docker image prune -f
}

$reclaimed3 = Invoke-PruneStep "Step 3: Pruning unused images (>7 days old)..." "Unused images" {
    docker image prune -a -f --filter "until=168h"
}

Write-Log "Step 4: Listing unused volumes..."
$unusedVolumes = docker volume ls -q --filter "dangling=true" 2>&1
if ($unusedVolumes) {
    Write-Log "Found unused volumes: $($unusedVolumes -join ', ')"
    Write-Log "SKIPPING volume prune for safety. Run 'docker volume prune' manually if needed."
} else {
    Write-Log "No unused volumes found."
}

$After = docker system df 2>&1
Write-Log "After:`n$After"

Write-Log "=== Done. Reclaimed: $reclaimed1 (build), $reclaimed2 (images), $reclaimed3 (old images) ==="
