param(
  [int]$TimeoutSeconds = 180,
  [int]$IntervalSeconds = 5,
  [switch]$Prod,
  [string]$EnvFile = '.env',
  [bool]$AutoProd = $true,
  [switch]$IncludeVolumes
)

$ErrorActionPreference = 'Stop'

function Z([string]$s) {
  return [regex]::Replace($s, '\\u([0-9a-fA-F]{4})', { param($m) [char]([Convert]::ToInt32($m.Groups[1].Value, 16)) })
}

function Pause-IfInteractive([string]$Prompt) {
  Write-Host $Prompt
  try {
    Read-Host "Press Enter to continue..." | Out-Null
  } catch {
  }
}

function Remove-StaleContainers {
  param([string[]]$ContainerNames)

  # 清理同名残留容器，避免 docker compose 创建时出现命名冲突
  foreach ($containerName in $ContainerNames) {
    $containerId = (docker ps -aq --filter "name=^/$containerName`$" 2>$null)
    if (-not [string]::IsNullOrWhiteSpace($containerId)) {
      Write-Host ((Z '[\u63d0\u793a] \u6e05\u7406\u6b8b\u7559\u5bb9\u5668\uff1a') + " $containerName")
      docker rm -f $containerName | Out-Null
      if ($LASTEXITCODE -ne 0) {
        throw ((Z '[\u9519\u8bef] \u65e0\u6cd5\u6e05\u7406\u6b8b\u7559\u5bb9\u5668\uff1a') + " $containerName")
      }
    }
  }
}

$scriptLock = $null
$hasScriptLock = $false

try {
    # 使用命名互斥锁避免重复双击导致并发重建
    $scriptLock = New-Object System.Threading.Mutex($false, 'Global\DemoDockerRebuildLock')
    $hasScriptLock = $scriptLock.WaitOne(0, $false)
    if (-not $hasScriptLock) {
      throw (Z '[\u9519\u8bef] \u68c0\u6d4b\u5230\u53e6\u4e00\u4e2a\u91cd\u5efa\u811a\u672c\u6b63\u5728\u8fd0\u884c\uff0c\u8bf7\u7a0d\u540e\u91cd\u8bd5\u3002')
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
      $upArgs += '--force-recreate'
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
      $round = 1
      
      do {
        $snapshots = @($services | ForEach-Object { Get-ServiceHealthSnapshot -ServiceName $_ })
        $pending = @($snapshots | Where-Object {
            $_.Status -ne 'running' -or ($_.Health -ne 'healthy' -and $_.Health -ne 'no-healthcheck')
          })

        Write-Host "----------------------------------------"
        Write-Host (Z "[\u68c0\u67e5] \u7b2c $round \u8f6e\u72b6\u6001\uff1a")
        foreach ($s in $snapshots) {
            $color = if ($s.Health -eq 'healthy' -or $s.Health -eq 'no-healthcheck') { "Green" } else { "Yellow" }
            Write-Host ("  - {0}: {1} ({2})" -f $s.Service.PadRight(20), $s.Status, $s.Health) -ForegroundColor $color
        }

        if ($pending.Count -eq 0) {
          Write-Host "----------------------------------------"
          return $true
        }

        if ((Get-Date) -ge $deadline) { 
            Write-Host "----------------------------------------"
            Write-Host (Z "[\u9519\u8bef] \u5065\u5eb7\u68c0\u67e5\u8d85\u65f6") -ForegroundColor Red
            break 
        }
        
        $round++
        Start-Sleep -Seconds $IntervalSeconds
      } while ($true)

      return $false
    }

    $scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
    $repoRoot = Resolve-Path (Join-Path $scriptDir "..\\..\\..")
    Set-Location $repoRoot

    if ($AutoProd -and -not $Prod) {
      if (Test-Path 'docker-compose.prod.yml') {
        $Prod = $true
      }
    }

    $script:ComposeCliArgs = @('-f', 'docker-compose.yml')
    if ($Prod) {
      $script:ComposeCliArgs += @('-f', 'docker-compose.prod.yml')
    }

    $resolvedEnvFile = $null
    if (-not [string]::IsNullOrWhiteSpace($EnvFile)) {
      $candidateEnvFile = if ([System.IO.Path]::IsPathRooted($EnvFile)) { $EnvFile } else { Join-Path $repoRoot $EnvFile }
      if (Test-Path $candidateEnvFile) {
        $resolvedEnvFile = Resolve-Path $candidateEnvFile
        $script:ComposeCliArgs += @('--env-file', $resolvedEnvFile)
      }
    }

    if (-not $resolvedEnvFile) {
      throw (Z '[\u9519\u8bef] \u672a\u627e\u5230 .env \u6587\u4ef6')
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
      'INTERNAL_API_TOKEN',
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
      throw (Z '.env \u914d\u7f6e\u4e0d\u5b8c\u6574')
    }

    $modeText = if ($Prod) { (Z '\u751f\u4ea7\u6a21\u5f0f') } else { (Z '\u5f00\u53d1\u6a21\u5f0f') }
    Write-Host '========================================'
    Write-Host (Z '\u667a\u6167\u8bfe\u5802 - Docker \u5168\u91cf\u91cd\u65b0\u6784\u5efa')
    Write-Host '========================================'
    Write-Host ((Z '\u5f53\u524d\u6a21\u5f0f') + ": $modeText")
    Write-Host ((Z '\u6e05\u7406\u6570\u636e\u5377') + ": " + $(if ($IncludeVolumes) { (Z '\u662f') } else { (Z '\u5426') }))
    Write-Host ''

    Run-Step (Z '[1/6] \u68c0\u67e5 Docker \u72b6\u6001...') {
      docker info | Out-Null
    } (Z '[\u9519\u8bef] Docker \u672a\u542f\u52a8\u3002')

    Run-Step (Z '[2/6] \u5f3a\u5236\u5220\u9664\u6240\u6709\u5bb9\u5668\u53ca\u7f51\u7edc...') {
      $downArgs = @('down', '--remove-orphans')
      if ($IncludeVolumes) {
        $downArgs += '-v'
      }
      Invoke-Compose @downArgs
    } (Z '[\u9519\u8bef] \u6e05\u7406\u65e7\u5bb9\u5668\u5931\u8d25\u3002')

    Run-Step (Z '[3/6] \u542f\u52a8\u57fa\u7840\u8bbe\u65bd\u670d\u52a1...') {
      Invoke-ComposeUp -Services @('postgres', 'redis', 'nacos', 'minio', 'sentinel')
    } (Z '[\u9519\u8bef] \u57fa\u7840\u8bbe\u65bd\u542f\u52a8\u5931\u8d25\u3002')

    Run-Step (Z '[4/6] \u542f\u52a8\u53ef\u89c2\u6d4b\u670d\u52a1...') {
      Invoke-ComposeUp -Services @('prometheus', 'grafana', 'jaeger')
    } (Z '[\u9519\u8bef] \u53ef\u89c2\u6d4b\u670d\u52a1\u542f\u52a8\u5931\u8d25\u3002')

    Write-Host (Z '[5/6] \u7b49\u5f85\u57fa\u7840\u670d\u52a1\u5c31\u7eea\uff0830\u79d2\uff09...')
    Start-Sleep -Seconds 30

    Run-Step (Z '[6/7] \u6e05\u7406\u540c\u540d\u6b8b\u7559\u4e1a\u52a1\u5bb9\u5668...') {
      Remove-StaleContainers -ContainerNames @(
        'demo-gateway',
        'demo-user-service',
        'demo-course-service',
        'demo-homework-service',
        'demo-progress-service',
        'demo-frontend'
      )
    } (Z '[\u9519\u8bef] \u6e05\u7406\u6b8b\u7559\u4e1a\u52a1\u5bb9\u5668\u5931\u8d25\u3002')

    Run-Step (Z '[7/7] \u91cd\u65b0\u6784\u5efa\u5e76\u542f\u52a8\u4e1a\u52a1\u670d\u52a1...') {
      Invoke-ComposeUp -Build -Services @('gateway', 'user-service', 'course-service', 'homework-service', 'progress-service', 'frontend')
    } (Z '[\u9519\u8bef] \u4e1a\u52a1\u670d\u52a1\u6784\u5efa\u5931\u8d25\u3002')

    Write-Host (Z '\u6b63\u5728\u8fdb\u884c\u6700\u540e\u7684\u5065\u5eb7\u68c0\u67e5...')
    $allHealthy = Wait-AllServicesHealthy -TimeoutSeconds $TimeoutSeconds -IntervalSeconds $IntervalSeconds

    if ($allHealthy) {
      Write-Host '========================================'
      Write-Host (Z '[\u5b8c\u6210] \u6240\u6709\u5bb9\u5668\u5df2\u91cd\u65b0\u5220\u9664\u5e76\u6784\u5efa\u5b8c\u6210')
      Write-Host '========================================'
    } else {
      Write-Host (Z '[\u8b66\u544a] \u90e8\u5206\u670d\u52a1\u542f\u52a8\u5f02\u5e38')
    }
} catch {
    Write-Host "Error: $($_.Exception.Message)" -ForegroundColor Red
    Pause-IfInteractive "An error occurred."
    exit 1
} finally {
    if ($hasScriptLock -and $scriptLock) {
      try {
        $scriptLock.ReleaseMutex() | Out-Null
      } catch {
      }
      $scriptLock.Dispose()
    }
}
