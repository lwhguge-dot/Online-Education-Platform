param(
    [string]$ProjectRoot = (Resolve-Path (Join-Path $PSScriptRoot "..\..\..")).Path,
    [string]$OutputJson = ""
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

# 简体中文注释：执行单项冒烟检查并统一收集结果，便于 CI 输出机器可读报告
function Invoke-SmokeCheck {
    param(
        [string]$Name,
        [scriptblock]$Action
    )

    $record = [ordered]@{
        name = $Name
        passed = $false
        exitCode = 0
        error = ""
        output = ""
    }

    try {
        $global:LASTEXITCODE = 0
        $actionOutput = & $Action 2>&1
        if ($actionOutput) {
            $record.output = ($actionOutput | ForEach-Object { $_.ToString().TrimEnd() }) -join "`n"
        }
        $record.exitCode = [int]$global:LASTEXITCODE
        $record.passed = ($record.exitCode -eq 0)
    }
    catch {
        $record.exitCode = if ($global:LASTEXITCODE) { [int]$global:LASTEXITCODE } else { 1 }
        $record.error = $_.Exception.Message
        $record.passed = $false
    }

    return [pscustomobject]$record
}

$pythonCmd = Get-Command python -ErrorAction SilentlyContinue
if (-not $pythonCmd) {
    $pythonCmd = Get-Command py -ErrorAction SilentlyContinue
}

$checks = @()

$checks += (Invoke-SmokeCheck -Name "playwright-detect-dev-servers" -Action {
            Push-Location (Join-Path $ProjectRoot ".codex/skills/playwright-skill")
            try {
                node -e "require('./lib/helpers').detectDevServers().then(s=>{console.log(JSON.stringify(s));}).catch(e=>{console.error(e.message);process.exit(1);});"
            }
            finally {
                Pop-Location
            }
        })

$checks += (Invoke-SmokeCheck -Name "playwright-run-wrapper" -Action {
            Push-Location (Join-Path $ProjectRoot ".codex/skills/playwright-skill")
            try {
                node run.js "const browser = await chromium.launch({ headless: true }); const page = await browser.newPage(); await page.goto('about:blank'); console.log('SMOKE_OK'); await browser.close();"
            }
            finally {
                Pop-Location
            }
        })

$checks += (Invoke-SmokeCheck -Name "gh-fix-ci-script-help" -Action {
            if (-not $pythonCmd) {
                throw "未找到 python/py 命令。"
            }
            & $pythonCmd.Source (Join-Path $ProjectRoot ".codex/skills/gh-fix-ci/scripts/inspect_pr_checks.py") --help | Out-Null
        })

$checks += (Invoke-SmokeCheck -Name "sentry-script-help" -Action {
            if (-not $pythonCmd) {
                throw "未找到 python/py 命令。"
            }
            & $pythonCmd.Source (Join-Path $ProjectRoot ".codex/skills/sentry/scripts/sentry_api.py") --help | Out-Null
        })

$checks += (Invoke-SmokeCheck -Name "planning-with-files-check-complete" -Action {
            & (Join-Path $ProjectRoot ".codex/skills/planning-with-files/scripts/check-complete.ps1") -PlanFile "__skill_smoke_nonexistent__.md" | Out-Null
        })

$checks += (Invoke-SmokeCheck -Name "ui-ux-pro-max-search-help" -Action {
            if (-not $pythonCmd) {
                throw "未找到 python/py 命令。"
            }
            Push-Location (Join-Path $ProjectRoot ".codex/skills/ui-ux-pro-max/scripts")
            try {
                & $pythonCmd.Source "search.py" --help | Out-Null
            }
            finally {
                Pop-Location
            }
        })

$failed = @($checks | Where-Object { -not $_.passed })
$summary = [pscustomobject]@{
    totalChecks = $checks.Count
    passedChecks = ($checks | Where-Object { $_.passed }).Count
    failedChecks = $failed.Count
    passed = ($failed.Count -eq 0)
}

$report = [pscustomobject]@{
    summary = $summary
    checks = @($checks)
}

Write-Host ""
Write-Host "== Skill Smoke 结果 ==" -ForegroundColor Cyan
$checks | Select-Object name, passed, exitCode, error | Format-Table -AutoSize

if ($OutputJson) {
    $outputDir = Split-Path -Parent $OutputJson
    if ($outputDir -and -not (Test-Path -Path $outputDir -PathType Container)) {
        New-Item -ItemType Directory -Path $outputDir -Force | Out-Null
    }
    $report | ConvertTo-Json -Depth 8 | Out-File -FilePath $OutputJson -Encoding utf8
    Write-Host "[INFO] JSON 报告已输出: $OutputJson" -ForegroundColor Cyan
}

if ($failed.Count -gt 0) {
    exit 1
}

exit 0
