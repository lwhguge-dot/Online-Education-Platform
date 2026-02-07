param(
  [int]$TimeoutSeconds = 180,
  [int]$IntervalSeconds = 5,
  [switch]$Prod,
  [string]$EnvFile = '.env',
  [bool]$AutoProd = $true,
  [switch]$ForceRecreate,
  [switch]$CheckOnly
)

$ErrorActionPreference = 'Stop'

function Z([string]$s) {
  return [regex]::Replace($s, '\\u([0-9a-fA-F]{4})', { param($m) [char]([Convert]::ToInt32($m.Groups[1].Value, 16)) })
}

function Pause-IfInteractive([string]$Prompt) {
  try {
    Read-Host $Prompt | Out-Null
  } catch {
  }
}

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
    Pause-IfInteractive (Z '\u6309\u56de\u8f66\u9000\u51fa')
    exit 1
  }
  Write-Host (Z '[\u5b8c\u6210]')
  Write-Host ''
}

function Invoke-Compose {
  param([Parameter(ValueFromRemainingArguments = $true)][string[]]$Args)
  & docker compose @script:ComposeCliArgs @Args
}

function Invoke-ComposeUp {
  param(
    [string[]]$Services,
    [switch]$Build
  )

  $upArgs = @('up', '-d')
  if ($Build) {
    $upArgs += '--build'
  }
  if ($ForceRecreate) {
    $upArgs += '--force-recreate'
  }
  $upArgs += $Services
  Invoke-Compose @upArgs
}

function Get-ServiceHealthSnapshot {
  param([string]$ServiceName)

  $containerId = (Invoke-Compose ps -q $ServiceName 2>$null)
  if ([string]::IsNullOrWhiteSpace($containerId)) {
    return [PSCustomObject]@{
      Service = $ServiceName
      Status = 'not-created'
      Health = 'unknown'
    }
  }

  $status = (docker inspect --format "{{.State.Status}}" $containerId 2>$null)
  $health = (docker inspect --format "{{if .State.Health}}{{.State.Health.Status}}{{else}}no-healthcheck{{end}}" $containerId 2>$null)

  return [PSCustomObject]@{
    Service = $ServiceName
    Status = $status
    Health = $health
  }
}

function Wait-AllServicesHealthy {
  param(
    [int]$TimeoutSeconds = 180,
    [int]$IntervalSeconds = 5
  )

  $services = @(Invoke-Compose ps --services)
  if ($services.Count -eq 0) {
    Write-Host (Z '[\u9519\u8bef] \u672a\u627e\u5230\u53ef\u5065\u5eb7\u68c0\u67e5\u7684\u670d\u52a1\u3002')
    return $false
  }

  $deadline = (Get-Date).AddSeconds($TimeoutSeconds)
  do {
    $snapshots = @($services | ForEach-Object { Get-ServiceHealthSnapshot -ServiceName $_ })
    $pending = @($snapshots | Where-Object {
        $_.Status -ne 'running' -or ($_.Health -ne 'healthy' -and $_.Health -ne 'no-healthcheck')
      })

    if ($pending.Count -eq 0) {
      $noHealthCheck = @($snapshots | Where-Object { $_.Health -eq 'no-healthcheck' })
      if ($noHealthCheck.Count -gt 0) {
        Write-Host (Z '[\u63d0\u793a] \u4ee5\u4e0b\u670d\u52a1\u672a\u914d\u7f6e healthcheck\uff1a')
        $noHealthCheck | ForEach-Object { Write-Host ("  - {0}" -f $_.Service) }
      }
      return $true
    }

    $pendingText = ($pending | ForEach-Object { "{0}({1}/{2})" -f $_.Service, $_.Status, $_.Health }) -join ', '
    Write-Host ((Z '[\u7b49\u5f85] \u670d\u52a1\u5c1a\u672a\u5b8c\u5168\u5c31\u7eea\uff1a') + " $pendingText")

    if ((Get-Date) -ge $deadline) { break }
    Start-Sleep -Seconds $IntervalSeconds
  } while ($true)

  Write-Host (Z '[\u9519\u8bef] \u5065\u5eb7\u68c0\u67e5\u8d85\u65f6\uff0c\u4ee5\u4e0b\u670d\u52a1\u5f02\u5e38\uff1a')
  $services | ForEach-Object {
    $item = Get-ServiceHealthSnapshot -ServiceName $_
    if ($item.Status -ne 'running' -or ($item.Health -ne 'healthy' -and $item.Health -ne 'no-healthcheck')) {
      Write-Host ("  - {0}: status={1}, health={2}" -f $item.Service, $item.Status, $item.Health)
    }
  }

  return $false
}

$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$repoRoot = Resolve-Path (Join-Path $scriptDir "..\\..\\..")
Set-Location $repoRoot

if ($TimeoutSeconds -le 0) {
  Write-Host (Z '[\u9519\u8bef] TimeoutSeconds \u5fc5\u987b\u5927\u4e8e 0\u3002')
  Pause-IfInteractive (Z '\u6309\u56de\u8f66\u9000\u51fa')
  exit 1
}

if ($IntervalSeconds -le 0) {
  Write-Host (Z '[\u9519\u8bef] IntervalSeconds \u5fc5\u987b\u5927\u4e8e 0\u3002')
  Pause-IfInteractive (Z '\u6309\u56de\u8f66\u9000\u51fa')
  exit 1
}

if ($AutoProd -and -not $Prod) {
  if (Test-Path 'docker-compose.prod.yml') {
    $Prod = $true
  }
}

$script:ComposeCliArgs = @('-f', 'docker-compose.yml')
if ($Prod) {
  if (-not (Test-Path 'docker-compose.prod.yml')) {
    Write-Host (Z '[\u9519\u8bef] \u672a\u627e\u5230 docker-compose.prod.yml\uff0c\u65e0\u6cd5\u542f\u7528\u751f\u4ea7\u6a21\u5f0f\u3002')
    Pause-IfInteractive (Z '\u6309\u56de\u8f66\u9000\u51fa')
    exit 1
  }
  $script:ComposeCliArgs += @('-f', 'docker-compose.prod.yml')
}

$resolvedEnvFile = $null
if (-not [string]::IsNullOrWhiteSpace($EnvFile)) {
  $candidateEnvFile = if ([System.IO.Path]::IsPathRooted($EnvFile)) {
    $EnvFile
  } else {
    Join-Path $repoRoot $EnvFile
  }

  if (Test-Path $candidateEnvFile) {
    $resolvedEnvFile = Resolve-Path $candidateEnvFile
    $script:ComposeCliArgs += @('--env-file', $resolvedEnvFile)
  } else {
    Write-Host ((Z '[\u63d0\u793a] \u672a\u627e\u5230 env \u6587\u4ef6\uff0c\u5df2\u5ffd\u7565\uff1a') + " $candidateEnvFile")
  }
}

if (-not $resolvedEnvFile) {
  Write-Host (Z '[\u9519\u8bef] \u672a\u627e\u5230 .env \u6587\u4ef6\uff0c\u4e3a\u907f\u514d\u4f7f\u7528\u5f31\u9ed8\u8ba4\u503c\uff0c\u542f\u52a8\u5df2\u4e2d\u6b62\u3002')
  Write-Host (Z '[\u5efa\u8bae] \u8bf7\u5148\u6267\u884c\uff1aCopy-Item .env.example .env \uff0c\u5e76\u586b\u5199\u5f3a\u5bc6\u7801\u540e\u91cd\u8bd5\u3002')
  Pause-IfInteractive (Z '\u6309\u56de\u8f66\u9000\u51fa')
  exit 1
}

$envMap = @{}
Get-Content $resolvedEnvFile | ForEach-Object {
  $line = $_.Trim()
  if ([string]::IsNullOrWhiteSpace($line) -or $line.StartsWith('#')) {
    return
  }
  $parts = $line -split '=', 2
  if ($parts.Count -eq 2) {
    $envMap[$parts[0].Trim()] = $parts[1]
  }
}

$requiredKeys = @(
  'POSTGRES_PASSWORD',
  'REDIS_PASSWORD',
  'JWT_SECRET',
  'MINIO_ROOT_USER',
  'MINIO_ROOT_PASSWORD',
  'MINIO_ACCESS_KEY',
  'MINIO_SECRET_KEY',
  'GRAFANA_ADMIN_USER',
  'GRAFANA_ADMIN_PASSWORD'
)

$missingKeys = @($requiredKeys | Where-Object { -not $envMap.ContainsKey($_) -or [string]::IsNullOrWhiteSpace($envMap[$_]) })
if ($missingKeys.Count -gt 0) {
  Write-Host (Z '[\u9519\u8bef] .env \u7f3a\u5c11\u5fc5\u9700\u53d8\u91cf\uff1a')
  $missingKeys | ForEach-Object { Write-Host ("  - {0}" -f $_) }
  Pause-IfInteractive (Z '\u6309\u56de\u8f66\u9000\u51fa')
  exit 1
}

$minLengthRules = @{
  POSTGRES_PASSWORD = 12
  REDIS_PASSWORD = 12
  JWT_SECRET = 32
  MINIO_ROOT_PASSWORD = 16
  MINIO_SECRET_KEY = 16
  GRAFANA_ADMIN_PASSWORD = 12
}

$weakValues = @(
  '123456',
  'admin',
  'minioadmin',
  'change-me-in-prod',
  'change-postgres-password',
  'change-redis-password',
  'change-jwt-secret',
  'change-minio-password',
  'change-minio-secret',
  'change-grafana-password'
)

$weakIssues = @()
foreach ($key in $minLengthRules.Keys) {
  $value = [string]$envMap[$key]
  if ($value.Length -lt $minLengthRules[$key]) {
    $weakIssues += ("{0} \u957f\u5ea6\u8fc7\u77ed\uff08\u81f3\u5c11 {1} \u4f4d\uff09" -f $key, $minLengthRules[$key])
  }
  if ($weakValues -contains $value -or $value -match '^change-') {
    $weakIssues += ("{0} \u4ecd\u4e3a\u793a\u4f8b\u6216\u5f31\u53e3\u4ee4" -f $key)
  }
}

if ($weakIssues.Count -gt 0) {
  Write-Host (Z '[\u9519\u8bef] .env \u4e2d\u5b58\u5728\u5f31\u5bc6\u7801\u6216\u5360\u4f4d\u503c\uff0c\u5df2\u62d2\u7edd\u542f\u52a8\uff1a')
  $weakIssues | ForEach-Object { Write-Host ("  - {0}" -f $_) }
  Write-Host (Z '[\u5efa\u8bae] \u8bf7\u66ff\u6362\u4e3a\u5f3a\u5bc6\u7801\u540e\u91cd\u8bd5\u3002')
  Pause-IfInteractive (Z '\u6309\u56de\u8f66\u9000\u51fa')
  exit 1
}

$modeText = if ($Prod) { (Z '\u751f\u4ea7\u6a21\u5f0f') } else { (Z '\u5f00\u53d1\u6a21\u5f0f') }
Write-Host '========================================'
Write-Host (Z '\u667a\u6167\u8bfe\u5802 - Docker \u542f\u52a8\u52a9\u624b')
Write-Host '========================================'
Write-Host ((Z '\u5f53\u524d\u6a21\u5f0f') + ": $modeText")
Write-Host ((Z '\u5f3a\u5236\u91cd\u5efa') + ": " + $(if ($ForceRecreate) { (Z '\u662f') } else { (Z '\u5426') }))
if ($resolvedEnvFile) {
  Write-Host ((Z '\u73af\u5883\u6587\u4ef6') + ": $resolvedEnvFile")
}
Write-Host ''

if ($CheckOnly) {
  Write-Host (Z '[\u63d0\u793a] \u4ec5\u6267\u884c\u73af\u5883\u9884\u68c0\uff0c\u4e0d\u542f\u52a8\u5bb9\u5668\u3002')
  Run-Step (Z '[1/2] \u68c0\u67e5 docker compose...') {
    docker compose version | Out-Null
  } (Z '[\u9519\u8bef] \u672a\u68c0\u6d4b\u5230 docker compose v2\u3002')

  Run-Step (Z '[2/2] \u6821\u9a8c\u7f16\u6392\u6587\u4ef6...') {
    Invoke-Compose config | Out-Null
  } (Z '[\u9519\u8bef] docker compose \u914d\u7f6e\u6821\u9a8c\u5931\u8d25\u3002')

  Write-Host (Z '[\u5b8c\u6210] \u9884\u68c0\u901a\u8fc7\uff1a.env \u5408\u89c4\u4e14 compose \u914d\u7f6e\u6709\u6548\u3002')
  Pause-IfInteractive (Z '\u6309\u56de\u8f66\u9000\u51fa')
  exit 0
}

Run-Step (Z '[1/8] \u68c0\u67e5 Docker \u670d\u52a1\u72b6\u6001...') {
  docker info | Out-Null
} (Z '[\u9519\u8bef] Docker \u672a\u542f\u52a8\uff0c\u8bf7\u5148\u542f\u52a8 Docker Desktop\u3002')

Run-Step (Z '[2/8] \u68c0\u67e5 docker compose...') {
  docker compose version | Out-Null
} (Z '[\u9519\u8bef] \u672a\u68c0\u6d4b\u5230 docker compose v2\u3002')

Run-Step (Z '[3/8] \u6821\u9a8c\u7f16\u6392\u6587\u4ef6...') {
  Invoke-Compose config | Out-Null
} (Z '[\u9519\u8bef] docker compose \u914d\u7f6e\u6821\u9a8c\u5931\u8d25\u3002')

Write-Host (Z '[4/8] \u505c\u6b62\u65e7\u7684\u4e1a\u52a1\u5bb9\u5668\uff08\u5982\u5b58\u5728\uff09...')
Invoke-Compose stop gateway user-service course-service homework-service progress-service frontend | Out-Null
Write-Host (Z '[\u5b8c\u6210]')
Write-Host ''

Run-Step (Z '[5/8] \u542f\u52a8\u57fa\u7840\u8bbe\u65bd\u670d\u52a1...') {
  Invoke-ComposeUp -Services @('postgres', 'redis', 'nacos', 'minio', 'sentinel')
} (Z '[\u9519\u8bef] \u57fa\u7840\u8bbe\u65bd\u670d\u52a1\u542f\u52a8\u5931\u8d25\u3002')

Run-Step (Z '[6/8] \u542f\u52a8\u53ef\u89c2\u6d4b\u670d\u52a1...') {
  Invoke-ComposeUp -Services @('prometheus', 'grafana', 'jaeger')
} (Z '[\u9519\u8bef] \u53ef\u89c2\u6d4b\u670d\u52a1\u542f\u52a8\u5931\u8d25\u3002')

Write-Host (Z '[7/8] \u7b49\u5f85\u5065\u5eb7\u68c0\u67e5\u51c6\u5907\uff08\u7ea6 30 \u79d2\uff09...')
Start-Sleep -Seconds 30
Write-Host (Z '[\u5b8c\u6210]')
Write-Host ''

Run-Step (Z '[8/8] \u542f\u52a8\u4e1a\u52a1\u670d\u52a1...') {
  Invoke-ComposeUp -Build -Services @('gateway', 'user-service', 'course-service', 'homework-service', 'progress-service', 'frontend')
} (Z '[\u9519\u8bef] \u4e1a\u52a1\u670d\u52a1\u542f\u52a8\u5931\u8d25\u3002\u8bf7\u67e5\u770b\u65e5\u5fd7\uff1adocker compose logs -f [service]')

Write-Host ((Z '[9/9] \u81ea\u52a8\u68c0\u67e5\u670d\u52a1\u5065\u5eb7\u72b6\u6001\uff08\u6700\u957f {0} \u79d2\uff0c\u95f4\u9694 {1} \u79d2\uff09...') -f $TimeoutSeconds, $IntervalSeconds)
$allHealthy = Wait-AllServicesHealthy -TimeoutSeconds $TimeoutSeconds -IntervalSeconds $IntervalSeconds
if (-not $allHealthy) {
  Write-Host (Z '[\u9519\u8bef] \u5b58\u5728\u672a\u5c31\u7eea\u670d\u52a1\uff0c\u8bf7\u6267\u884c docker compose ps \u6216 docker compose logs -f [service] \u6392\u67e5\u3002')
  Pause-IfInteractive (Z '\u6309\u56de\u8f66\u9000\u51fa')
  exit 1
}

Write-Host (Z '[\u5b8c\u6210] \u6240\u6709\u670d\u52a1\u5065\u5eb7\u72b6\u6001\u6b63\u5e38\u3002')
Write-Host ''
Write-Host '========================================'
Write-Host (Z '[\u5b8c\u6210] \u6240\u6709\u670d\u52a1\u5df2\u542f\u52a8')
Write-Host '========================================'
Write-Host (Z '\u524d\u7aef\u5730\u5740') ': http://localhost'
Write-Host (Z 'API \u7f51\u5173') ': http://localhost:8090'
Write-Host 'Nacos:       http://localhost:8848/nacos'
Write-Host 'Sentinel:    http://localhost:8858'
Write-Host 'MinIO:       http://localhost:9001'
Write-Host 'Prometheus:  http://localhost:9090'
Write-Host 'Grafana:     http://localhost:3000'
Write-Host 'Jaeger:      http://localhost:16686'
Write-Host ''
Write-Host (Z '\u5e38\u7528\u547d\u4ee4\uff1a')
Write-Host '  docker compose ps'
Write-Host '  docker compose logs -f [service]'
Write-Host '  docker compose restart [service]'
Write-Host '  docker compose down'
Write-Host ''

Pause-IfInteractive (Z '\u6309\u56de\u8f66\u9000\u51fa')
