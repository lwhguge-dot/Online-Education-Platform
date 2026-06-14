# =============================================================================
# Docker 运维脚本公共模块
#
# 用法: . "$PSScriptRoot\common.ps1"
# 提供: Run-Step, Invoke-Compose, Invoke-ComposeUp,
#       Get-ServiceHealthSnapshot, Wait-AllServicesHealthy,
#       Read-EnvFile, Pause-IfInteractive
# =============================================================================

$ErrorActionPreference = 'Stop'

function Pause-IfInteractive {
  param([string]$Prompt)
  Write-Host $Prompt
  try {
    Read-Host "Press Enter to continue..." | Out-Null
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
    throw $ErrorMessage
  }
  Write-Host '[完成]'
  Write-Host ''
}

function Invoke-Compose {
  param([Parameter(ValueFromRemainingArguments = $true)][string[]]$Args)
  & docker compose @script:ComposeCliArgs @Args
}

function Invoke-ComposeUp {
  param(
    [string[]]$Services,
    [switch]$Build,
    [switch]$ForceRecreate
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
      Status  = 'not-created'
      Health  = 'unknown'
    }
  }

  $status = (docker inspect --format "{{.State.Status}}" $containerId 2>$null)
  $health = (docker inspect --format "{{if .State.Health}}{{.State.Health.Status}}{{else}}no-healthcheck{{end}}" $containerId 2>$null)

  return [PSCustomObject]@{
    Service = $ServiceName
    Status  = $status
    Health  = $health
  }
}

function Wait-AllServicesHealthy {
  param(
    [int]$TimeoutSeconds = 180,
    [int]$IntervalSeconds = 5
  )

  $services = @(Invoke-Compose ps --services)
  if ($services.Count -eq 0) {
    Write-Host '[错误] 未找到可健康检查的服务。'
    return $false
  }

  $deadline = (Get-Date).AddSeconds($TimeoutSeconds)
  $round = 1

  do {
    $snapshots = @($services | ForEach-Object { Get-ServiceHealthSnapshot -ServiceName $_ })
    $pending = @($snapshots | Where-Object {
        $_.Status -ne 'running' -or ($_.Health -ne 'healthy' -and $_.Health -ne 'no-healthcheck')
      })

    Write-Host "----------------------------------------"
    Write-Host "[检查] 第 $round 轮状态："
    foreach ($s in $snapshots) {
      $color = if ($s.Health -eq 'healthy' -or $s.Health -eq 'no-healthcheck') { "Green" } else { "Yellow" }
      Write-Host ("  - {0}: {1} ({2})" -f $s.Service.PadRight(20), $s.Status, $s.Health) -ForegroundColor $color
    }

    if ($pending.Count -eq 0) {
      $noHealthCheck = @($snapshots | Where-Object { $_.Health -eq 'no-healthcheck' })
      if ($noHealthCheck.Count -gt 0) {
        Write-Host '[提示] 以下服务未配置 healthcheck：'
        $noHealthCheck | ForEach-Object { Write-Host ("  - {0}" -f $_.Service) }
      }
      Write-Host "----------------------------------------"
      return $true
    }

    if ((Get-Date) -ge $deadline) {
      Write-Host "----------------------------------------"
      Write-Host '[错误] 健康检查超时' -ForegroundColor Red
      break
    }

    $round++
    Start-Sleep -Seconds $IntervalSeconds
  } while ($true)

  Write-Host '[错误] 健康检查超时，以下服务异常：'
  $services | ForEach-Object {
    $item = Get-ServiceHealthSnapshot -ServiceName $_
    if ($item.Status -ne 'running' -or ($item.Health -ne 'healthy' -and $item.Health -ne 'no-healthcheck')) {
      Write-Host ("  - {0}: status={1}, health={2}" -f $item.Service, $item.Status, $item.Health)
    }
  }

  return $false
}

function Read-EnvFile {
  param(
    [string]$EnvFile = '.env',
    [string]$RepoRoot
  )

  $candidateEnvFile = if ([System.IO.Path]::IsPathRooted($EnvFile)) {
    $EnvFile
  } else {
    Join-Path $RepoRoot $EnvFile
  }

  if (-not (Test-Path $candidateEnvFile)) {
    return $null
  }

  $resolvedPath = Resolve-Path $candidateEnvFile

  # 解析 env 文件为 hashtable
  $envMap = @{}
  Get-Content $resolvedPath | ForEach-Object {
    $line = $_.Trim()
    if ([string]::IsNullOrWhiteSpace($line) -or $line.StartsWith('#')) {
      return
    }
    $parts = $line -split '=', 2
    if ($parts.Count -eq 2) {
      $envMap[$parts[0].Trim()] = $parts[1]
    }
  }

  # 校验必填变量
  $requiredKeys = @(
    'POSTGRES_PASSWORD',
    'REDIS_PASSWORD',
    'JWT_SECRET',
    'INTERNAL_API_TOKEN',
    'MINIO_ROOT_USER',
    'MINIO_ROOT_PASSWORD',
    'MINIO_ACCESS_KEY',
    'MINIO_SECRET_KEY'
  )

  $missingKeys = @($requiredKeys | Where-Object { -not $envMap.ContainsKey($_) -or [string]::IsNullOrWhiteSpace($envMap[$_]) })
  if ($missingKeys.Count -gt 0) {
    Write-Host '[错误] .env 缺少必需变量：'
    $missingKeys | ForEach-Object { Write-Host ("  - {0}" -f $_) }
    return $null
  }

  # 弱密码检测
  $minLengthRules = @{
    POSTGRES_PASSWORD    = 12
    REDIS_PASSWORD       = 12
    JWT_SECRET           = 32
    INTERNAL_API_TOKEN   = 32
    MINIO_ROOT_PASSWORD  = 16
    MINIO_SECRET_KEY     = 16
  }

  $weakValues = @(
    '123456', 'admin', 'minioadmin',
    'change-postgres-password', 'change-redis-password',
    'change-jwt-secret', 'change-internal-api-token',
    'change-minio-password', 'change-minio-secret'
  )

  $weakIssues = @()
  foreach ($key in $minLengthRules.Keys) {
    $value = [string]$envMap[$key]
    if ($value.Length -lt $minLengthRules[$key]) {
      $weakIssues += ("{0} 长度过短（至少 {1} 位）" -f $key, $minLengthRules[$key])
    }
    if ($weakValues -contains $value -or $value -match '^change-') {
      $weakIssues += ("{0} 仍为示例或弱口令" -f $key)
    }
  }

  if ($weakIssues.Count -gt 0) {
    Write-Host '[错误] .env 中存在弱密码或占位值，已拒绝启动：'
    $weakIssues | ForEach-Object { Write-Host ("  - {0}" -f $_) }
    Write-Host '[建议] 请替换为强密码后重试。'
    return $null
  }

  return @{
    Path   = $resolvedPath
    EnvMap = $envMap
  }
}
