param(
  [int]$TimeoutSeconds = 180,
  [int]$IntervalSeconds = 5,
  [string]$EnvFile = '.env',
  [switch]$ForceRecreate,
  [switch]$CheckOnly
)

$ErrorActionPreference = 'Stop'
. "$PSScriptRoot\common.ps1"

$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$repoRoot = Resolve-Path (Join-Path $scriptDir "..\..\..")
Set-Location $repoRoot

if ($TimeoutSeconds -le 0) {
  Write-Host '[错误] TimeoutSeconds 必须大于 0。'
  Pause-IfInteractive '按回车退出'
  exit 1
}

if ($IntervalSeconds -le 0) {
  Write-Host '[错误] IntervalSeconds 必须大于 0。'
  Pause-IfInteractive '按回车退出'
  exit 1
}

$script:ComposeCliArgs = @('-f', 'docker-compose.yml')

$envResult = Read-EnvFile -EnvFile $EnvFile -RepoRoot $repoRoot
if (-not $envResult) {
  Write-Host '[错误] 未找到 .env 文件，为避免使用弱默认值，启动已中止。'
  Write-Host '[建议] 请先执行：Copy-Item .env.example .env ，并填写强密码后重试。'
  Pause-IfInteractive '按回车退出'
  exit 1
}

$resolvedEnvFile = $envResult.Path
$script:ComposeCliArgs += @('--env-file', $resolvedEnvFile)

Write-Host '========================================'
Write-Host '智慧课堂 - Docker 启动助手'
Write-Host '========================================'
Write-Host ("强制重建: " + $(if ($ForceRecreate) { '是' } else { '否' }))
Write-Host ("环境文件: $resolvedEnvFile")
Write-Host ''

if ($CheckOnly) {
  Write-Host '[提示] 仅执行环境预检，不启动容器。'
  try {
    Run-Step '[1/2] 检查 docker compose...' {
      docker compose version | Out-Null
    } '[错误] 未检测到 docker compose v2。'

    Run-Step '[2/2] 校验编排文件...' {
      Invoke-Compose config | Out-Null
    } '[错误] docker compose 配置校验失败。'
  } catch {
    Write-Host $_.Exception.Message -ForegroundColor Red
    Pause-IfInteractive '按回车退出'
    exit 1
  }

  Write-Host '[完成] 预检通过：.env 合规且 compose 配置有效。'
  Pause-IfInteractive '按回车退出'
  exit 0
}

try {
  Run-Step '[1/8] 检查 Docker 服务状态...' {
    docker info | Out-Null
  } '[错误] Docker 未启动，请先启动 Docker Desktop。'

  Run-Step '[2/8] 检查 docker compose...' {
    docker compose version | Out-Null
  } '[错误] 未检测到 docker compose v2。'

  Run-Step '[3/8] 校验编排文件...' {
    Invoke-Compose config | Out-Null
  } '[错误] docker compose 配置校验失败。'

  Write-Host '[4/8] 停止旧的业务容器（如存在）...'
  Invoke-Compose stop gateway user-service course-service homework-service progress-service frontend | Out-Null
  Write-Host '[完成]'
  Write-Host ''

  Run-Step '[5/8] 启动基础设施服务...' {
    Invoke-ComposeUp -Services @('postgres', 'redis', 'nacos', 'minio') -ForceRecreate:$ForceRecreate
  } '[错误] 基础设施服务启动失败。'

  Write-Host '[6/8] 等待健康检查准备（约 30 秒）...'
  Start-Sleep -Seconds 30
  Write-Host '[完成]'
  Write-Host ''

  Run-Step '[7/8] 启动业务服务...' {
    Invoke-ComposeUp -Build -ForceRecreate:$ForceRecreate -Services @('gateway', 'user-service', 'course-service', 'homework-service', 'progress-service', 'frontend')
  } '[错误] 业务服务启动失败。请查看日志：docker compose logs -f [service]'
} catch {
  Write-Host $_.Exception.Message -ForegroundColor Red
  Pause-IfInteractive '按回车退出'
  exit 1
}

Write-Host ("自动检查服务健康状况（最长 {0} 秒，间隔 {1} 秒）..." -f $TimeoutSeconds, $IntervalSeconds)
$allHealthy = Wait-AllServicesHealthy -TimeoutSeconds $TimeoutSeconds -IntervalSeconds $IntervalSeconds
if (-not $allHealthy) {
  Write-Host '[错误] 存在未就绪服务，请执行 docker compose ps 或 docker compose logs -f [service] 排查。'
  Pause-IfInteractive '按回车退出'
  exit 1
}

Write-Host '[完成] 所有服务健康状态正常。'
Write-Host ''
Write-Host '========================================'
Write-Host '[完成] 所有服务已启动'
Write-Host '========================================'
Write-Host '前端地址: http://localhost'
Write-Host 'API 网关: http://localhost:8090'
Write-Host 'Nacos:       http://localhost:8848/nacos'
Write-Host 'MinIO:       http://localhost:9001'
Write-Host ''
Write-Host '常用命令：'
Write-Host '  docker compose ps'
Write-Host '  docker compose logs -f [service]'
Write-Host '  docker compose restart [service]'
Write-Host '  docker compose down'
Write-Host ''

Pause-IfInteractive '按回车退出'
