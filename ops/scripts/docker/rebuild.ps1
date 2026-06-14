param(
  [int]$TimeoutSeconds = 180,
  [int]$IntervalSeconds = 5,
  [string]$EnvFile = '.env',
  [switch]$IncludeVolumes
)

$ErrorActionPreference = 'Stop'
. "$PSScriptRoot\common.ps1"

function Remove-StaleContainers {
  param([string[]]$ContainerNames)

  # 清理同名残留容器，避免 docker compose 创建时出现命名冲突
  foreach ($containerName in $ContainerNames) {
    $containerId = (docker ps -aq --filter "name=^/$containerName`$" 2>$null)
    if (-not [string]::IsNullOrWhiteSpace($containerId)) {
      Write-Host ("[提示] 清理残留容器：" + " $containerName")
      docker rm -f $containerName | Out-Null
      if ($LASTEXITCODE -ne 0) {
        throw ("[错误] 无法清理残留容器：" + " $containerName")
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
      throw '[错误] 检测到另一个重建脚本正在运行，请稍后重试。'
    }

    $scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
    $repoRoot = Resolve-Path (Join-Path $scriptDir "..\..\..")
    Set-Location $repoRoot

    $script:ComposeCliArgs = @('-f', 'docker-compose.yml')

    $envResult = Read-EnvFile -EnvFile $EnvFile -RepoRoot $repoRoot
    if (-not $envResult) {
      throw '[错误] 未找到 .env 文件或配置不完整'
    }

    $script:ComposeCliArgs += @('--env-file', $envResult.Path)

    Write-Host '========================================'
    Write-Host '智慧课堂 - Docker 全量重新构建'
    Write-Host '========================================'
    Write-Host ("清理数据卷: " + $(if ($IncludeVolumes) { '是' } else { '否' }))
    Write-Host ''

    Run-Step '[1/6] 检查 Docker 状态...' {
      docker info | Out-Null
    } '[错误] Docker 未启动。'

    Run-Step '[2/5] 强制删除所有容器及网络...' {
      $downArgs = @('down', '--remove-orphans')
      if ($IncludeVolumes) {
        $downArgs += '-v'
      }
      Invoke-Compose @downArgs
    } '[错误] 清理旧容器失败。'

    Run-Step '[3/5] 启动基础设施服务...' {
      Invoke-ComposeUp -Services @('postgres', 'redis', 'nacos', 'minio') -ForceRecreate
    } '[错误] 基础设施启动失败。'

    Write-Host '[4/5] 等待基础服务就绪（30秒）...'
    Start-Sleep -Seconds 30

    Run-Step '[5/5] 清理同名残留业务容器...' {
      Remove-StaleContainers -ContainerNames @(
        'demo-gateway',
        'demo-user-service',
        'demo-course-service',
        'demo-homework-service',
        'demo-progress-service',
        'demo-frontend'
      )
    } '[错误] 清理残留业务容器失败。'

    Write-Host '正在重新构建并启动业务服务...'
    Invoke-ComposeUp -Build -ForceRecreate -Services @('gateway', 'user-service', 'course-service', 'homework-service', 'progress-service', 'frontend')
    Write-Host '[完成]'

    Write-Host '正在进行最后的健康检查...'
    $allHealthy = Wait-AllServicesHealthy -TimeoutSeconds $TimeoutSeconds -IntervalSeconds $IntervalSeconds

    if ($allHealthy) {
      Write-Host '========================================'
      Write-Host '[完成] 所有容器已重新删除并构建完成'
      Write-Host '========================================'
    } else {
      Write-Host '[警告] 部分服务启动异常'
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
