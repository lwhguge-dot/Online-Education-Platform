$headers = @{ "Content-Type" = "application/json" }
$url = "http://localhost:8084/api/progress/video/report" # Direct to service

function Report-Progress {
    param([int]$pos)
    $timestamp = [DateTimeOffset]::Now.ToUnixTimeMilliseconds()
    $body = @{
        studentId       = 999
        chapterId       = 888
        courseId        = 777
        currentPosition = $pos
        totalDuration   = 1000
        clientTimestamp = $timestamp
    } | ConvertTo-Json
    
    try {
        $response = Invoke-RestMethod -Uri $url -Method Post -Headers $headers -Body $body
        Write-Host "Position $pos : Success - UnlockTriggered: $($response.data.unlockTriggered)"
    }
    catch {
        Write-Host "Position $pos : Failed - $($_.Exception.Message)"
        Write-Host $_.ErrorDetails.Message
    }
}

Write-Host "--- Test 1: Initial Report (Should Sync) ---"
Report-Progress -pos 10

Write-Host "--- Test 2: Immediate Update (Should Buffer) ---"
Report-Progress -pos 20

Write-Host "--- Test 3: Validating Redis Key ---"
# Needs docker exec to check redis
# docker-compose exec redis redis-cli get progress:999:888
