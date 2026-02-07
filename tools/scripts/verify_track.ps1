$url = "http://localhost:8084/api/progress/student/999/learning-track"
$reportUrl = "http://localhost:8084/api/progress/video/report"
$redisCmd = "docker exec demo-redis redis-cli -a 123456"

function Check-Key {
    param($key)
    $exists = Invoke-Expression "$redisCmd exists $key"
    if ($exists -eq 1) { Write-Host "Key '$key' EXISTS" -ForegroundColor Green }
    else { Write-Host "Key '$key' NOT FOUND" -ForegroundColor Yellow }
}

Write-Host "--- 1. Get Track (Should Cache) ---"
try {
    Invoke-RestMethod -Uri $url -Method Get | Out-Null
    Write-Host "Request Success"
}
catch {
    Write-Host "Request Failed: $_" -ForegroundColor Red
}

Write-Host "`n--- 2. Check Redis Key ---"
$keyResult = docker exec demo-redis redis-cli -a 123456 exists "learning_track::999" 2>&1
if ($keyResult -match "1") { Write-Host "Key 'learning_track::999' EXISTS" -ForegroundColor Green }
else { Write-Host "Key 'learning_track::999' NOT FOUND" -ForegroundColor Yellow }

Write-Host "`n--- 3. Report Progress (Trigger Sync & Evict) ---"
# To force sync, set currentPosition = totalDuration (Completion)
# Or wait 30s... Completion is faster.
$ts = [DateTimeOffset]::Now.ToUnixTimeMilliseconds()
$body = @{
    studentId       = 999
    chapterId       = 777 # Use different chapter to avoid messing up previous test? Or same.
    courseId        = 101
    currentPosition = 200
    totalDuration   = 200 # Completed
    clientTimestamp = $ts
} | ConvertTo-Json

try {
    Invoke-RestMethod -Uri $reportUrl -Method Post -Body $body -ContentType "application/json" | Out-Null
    Write-Host "Report Success (Video Completed)"
}
catch {
    Write-Host "Report Failed: $_" -ForegroundColor Red
}

Write-Host "`n--- 4. Check Redis Key (Should be gone) ---"
$keyResult2 = docker exec demo-redis redis-cli -a 123456 exists "learning_track::999" 2>&1
if ($keyResult2 -match "1") { Write-Host "Key 'learning_track::999' EXISTS (eviction failed)" -ForegroundColor Red }
else { Write-Host "Key 'learning_track::999' EVICTED successfully" -ForegroundColor Green }
