$ErrorActionPreference = 'Stop'

function Z([string]$s) {
  return [regex]::Replace($s, '\\u([0-9a-fA-F]{4})', { param($m) [char]([Convert]::ToInt32($m.Groups[1].Value, 16)) })
}

Write-Host "========================================"
Write-Host (Z '\u667a\u6167\u8bfe\u5802 - Docker \u542f\u52a8\u52a9\u624b')
Write-Host "========================================"
Write-Host ""

$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$repoRoot = Resolve-Path (Join-Path $scriptDir "..\\..")
Set-Location $repoRoot

function Run-Step {
  param(
    [string]$Title,
    [scriptblock]$Action,
    [string]$ErrorMessage
  )

  Write-Host $Title
  & $Action
  if ($LASTEXITCODE -ne 0) {
    Write-Host $ErrorMessage
    Read-Host (Z '\u6309\u56de\u8f66\u9000\u51fa')
    exit 1
  }
  Write-Host (Z '[\u5b8c\u6210]')
  Write-Host ""
}

Run-Step (Z '[1/8] \u68c0\u67e5 Docker \u670d\u52a1\u72b6\u6001...') {
  docker info | Out-Null
} (Z '[\u9519\u8bef] Docker \u672a\u542f\u52a8\uff0c\u8bf7\u5148\u542f\u52a8 Docker Desktop\u3002')

Run-Step (Z '[2/8] \u68c0\u67e5 docker compose...') {
  docker compose version | Out-Null
} (Z '[\u9519\u8bef] \u672a\u68c0\u6d4b\u5230 docker compose v2\u3002')

Run-Step (Z '[3/8] \u6821\u9a8c\u7f16\u6392\u6587\u4ef6...') {
  docker compose -f docker-compose.yml config | Out-Null
} (Z '[\u9519\u8bef] docker-compose.yml \u6821\u9a8c\u5931\u8d25\u3002')

Write-Host (Z '[4/8] \u505c\u6b62\u65e7\u7684\u4e1a\u52a1\u5bb9\u5668\uff08\u5982\u5b58\u5728\uff09...')
docker compose stop gateway user-service course-service homework-service progress-service frontend | Out-Null
Write-Host (Z '[\u5b8c\u6210]')
Write-Host ""

Run-Step (Z '[5/8] \u542f\u52a8\u57fa\u7840\u8bbe\u65bd\u670d\u52a1...') {
  docker compose up -d postgres redis nacos minio sentinel | Out-Null
} (Z '[\u9519\u8bef] \u57fa\u7840\u8bbe\u65bd\u670d\u52a1\u542f\u52a8\u5931\u8d25\u3002')

Run-Step (Z '[6/8] \u542f\u52a8\u53ef\u89c2\u6d4b\u670d\u52a1...') {
  docker compose up -d prometheus grafana jaeger | Out-Null
} (Z '[\u9519\u8bef] \u53ef\u89c2\u6d4b\u670d\u52a1\u542f\u52a8\u5931\u8d25\u3002')

Write-Host (Z '[7/8] \u7b49\u5f85\u5065\u5eb7\u68c0\u67e5\u5b8c\u6210\uff08\u7ea6 30 \u79d2\uff09...')
Start-Sleep -Seconds 30
Write-Host (Z '[\u5b8c\u6210]')
Write-Host ""

Run-Step (Z '[8/8] \u542f\u52a8\u4e1a\u52a1\u670d\u52a1...') {
  docker compose up -d --build gateway user-service course-service homework-service progress-service frontend | Out-Null
} (Z '[\u9519\u8bef] \u4e1a\u52a1\u670d\u52a1\u542f\u52a8\u5931\u8d25\u3002\u8bf7\u67e5\u770b\u65e5\u5fd7\uff1adocker compose logs -f [service]')

Write-Host "========================================"
Write-Host (Z '[\u5b8c\u6210] \u6240\u6709\u670d\u52a1\u5df2\u542f\u52a8')
Write-Host "========================================"
Write-Host (Z '\u524d\u7aef\u5730\u5740') ': http://localhost'
Write-Host (Z 'API \u7f51\u5173') ': http://localhost:8090'
Write-Host 'Nacos:       http://localhost:8848/nacos'
Write-Host 'Sentinel:    http://localhost:8858'
Write-Host 'MinIO:       http://localhost:9001'
Write-Host 'Prometheus:  http://localhost:9090'
Write-Host 'Grafana:     http://localhost:3000'
Write-Host 'Jaeger:      http://localhost:16686'
Write-Host ""
Write-Host (Z '\u5e38\u7528\u547d\u4ee4\uff1a')
Write-Host '  docker compose ps'
Write-Host '  docker compose logs -f [service]'
Write-Host '  docker compose restart [service]'
Write-Host '  docker compose down'
Write-Host ""
Read-Host (Z '\u6309\u56de\u8f66\u9000\u51fa')