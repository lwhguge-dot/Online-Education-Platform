param(
    [string]$CasesFile = (Join-Path $PSScriptRoot "..\..\skills\regression-cases.json"),
    [string]$ContractFile = (Join-Path $PSScriptRoot "..\..\skills\route-contract.json"),
    [string]$OutputJson = "",
    [switch]$FailOnWarning
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

# 简体中文注释：基于 AGENTS.md 路由约定实现可测试的规则路由器
function Resolve-SkillsFromInput {
    param([string]$InputText)

    $text = ($InputText ?? "").ToLowerInvariant()
    $skills = New-Object System.Collections.Generic.List[string]

    # 简体中文注释：全局守卫始终先判定
    $skills.Add("using-superpowers")

    if ($text -match '复杂|complex|multi-step|多步骤|拆解') {
        $skills.Add("planning-with-files")
        $skills.Add("writing-plans")
    }

    if ($text -match '新功能|new feature|creative|idea|设计方案|brainstorm') {
        $skills.Add("brainstorming")
        $skills.Add("writing-plans")
    }

    if ($text -match '按这个计划实现|implement this plan|执行计划|按计划落地') {
        $skills.Add("executing-plans")
    }

    if ($text -match '多个子任务|subagent|并行实现|并行开发') {
        $skills.Add("subagent-driven-development")
    }

    if ($text -match '独立测试失败|independent test failures|independent failures|两个独立测试失败') {
        $skills.Add("dispatching-parallel-agents")
    }

    if (($text -match '并行|parallel') -and ($text -match '失败|故障|bug|test failure')) {
        $skills.Add("dispatching-parallel-agents")
    }

    if ($text -match '\bbug\b|这个 bug|bug 修复|故障|失败测试|test failure') {
        $skills.Add("systematic-debugging")
        $skills.Add("test-driven-development")
    }

    if ($text -match 'ui|ux|页面|前端设计|redesign') {
        $skills.Add("ui-ux-pro-max")
    }

    if ($text -match 'api 安全|api security|鉴权|authentication endpoint|oauth|jwt') {
        $skills.Add("api-security-best-practices")
        $skills.Add("auth-implementation-patterns")
    }

    if ($text -match 'sql|postgres|postgresql|索引|查询优化') {
        $skills.Add("postgres-best-practices")
    }

    if ($text -match '微服务|microservice|服务拆分|service boundary') {
        $skills.Add("microservices-patterns")
    }

    if ($text -match '监控|可观测|observability|链路|tracing|latency') {
        $skills.Add("observability-engineer")
        $skills.Add("distributed-tracing")
    }

    if ($text -match 'docker|容器|镜像|dockerfile') {
        $skills.Add("docker-expert")
    }

    if ($text -match 'github actions|ci 失败|failing checks|gh-fix-ci|ci 状态|ci check') {
        $skills.Add("gh-fix-ci")
    }

    if ($text -match '发布|deploy|上线|rollback|release') {
        $skills.Add("deployment-procedures")
    }

    if ($text -match 'review comments|pr 评论|代码评审意见|address comments') {
        $skills.Add("address-github-comments")
    }

    if ($text -match 'playwright|登录流程|截图|browser automation') {
        $skills.Add("playwright-skill")
    }

    if ($text -match 'sentry|unresolved issues|生产错误|event') {
        $skills.Add("sentry")
    }

    if ($text -match 'threat model|威胁建模|abuse path|appsec') {
        $skills.Add("security-threat-model")
    }

    if ($text -match '创建 skill|新 skill|install skill|writing-skills') {
        $skills.Add("writing-skills")
    }

    if ($text -match '开始开发分支|worktree|隔离分支') {
        $skills.Add("using-git-worktrees")
    }

    if ($text -match '完成|通过|ready to merge|交付') {
        $skills.Add("verification-before-completion")
        $skills.Add("receiving-code-review")
    }

    if ($text -match '收尾分支|merge strategy|branch cleanup') {
        $skills.Add("finishing-a-development-branch")
    }

    if ($text -match '请求评审|request review|发起评审') {
        $skills.Add("requesting-code-review")
    }

    # 简体中文注释：去重且保持首次出现顺序
    $seen = @{}
    $ordered = New-Object System.Collections.Generic.List[string]
    foreach ($skill in $skills) {
        if (-not $seen.ContainsKey($skill)) {
            $seen[$skill] = $true
            $ordered.Add($skill)
        }
    }

    return @($ordered)
}

# 简体中文注释：判断顺序约束是否满足（requiredOrder 按子序列匹配）
function Test-RequiredOrder {
    param(
        [string[]]$PredictedSkills,
        [string[]]$RequiredOrder
    )

    if (-not $RequiredOrder -or $RequiredOrder.Count -eq 0) {
        return $true
    }

    $cursor = -1
    foreach ($required in $RequiredOrder) {
        $idx = [Array]::IndexOf($PredictedSkills, $required)
        if ($idx -lt 0 -or $idx -le $cursor) {
            return $false
        }
        $cursor = $idx
    }

    return $true
}

if (-not (Test-Path -Path $CasesFile -PathType Leaf)) {
    Write-Host "[FAIL] 回放样例文件不存在: $CasesFile" -ForegroundColor Red
    exit 1
}

if (-not (Test-Path -Path $ContractFile -PathType Leaf)) {
    Write-Host "[FAIL] 路由契约文件不存在: $ContractFile" -ForegroundColor Red
    exit 1
}

$cases = Get-Content -Path $CasesFile -Raw -Encoding UTF8 | ConvertFrom-Json
$contract = Get-Content -Path $ContractFile -Raw -Encoding UTF8 | ConvertFrom-Json

$contractMap = @{}
foreach ($item in $contract) {
    $contractMap[$item.scenarioId] = $item
}

$failedCases = @()
$orderingErrors = @()
$unexpectedSkills = @()
$warnings = @()

$allExpectedSkillSet = New-Object System.Collections.Generic.HashSet[string]

foreach ($case in $cases) {
    $scenarioId = [string]$case.scenarioId
    $inputText = [string]$case.input
    $expectedSkills = @($case.expectedSkills)
    $requiredOrder = @($case.requiredOrder)
    $forbiddenSkills = @($case.forbiddenSkills)

    foreach ($skill in $expectedSkills) {
        $null = $allExpectedSkillSet.Add([string]$skill)
    }

    if (-not $contractMap.ContainsKey($scenarioId)) {
        $warnings += [pscustomobject]@{
                scenarioId = $scenarioId
                type = "CaseNotInContract"
                detail = "回放样例未在 route-contract.json 中定义"
            }
    }

    $predicted = @(Resolve-SkillsFromInput -InputText $inputText)
    $missing = @($expectedSkills | Where-Object { $_ -notin $predicted })
    $unexpected = @($predicted | Where-Object { $_ -notin $expectedSkills -and $_ -notin $forbiddenSkills })
    $forbiddenHit = @($forbiddenSkills | Where-Object { $_ -in $predicted })

    if ($missing.Count -gt 0 -or $forbiddenHit.Count -gt 0) {
        $failedCases += [pscustomobject]@{
                scenarioId = $scenarioId
                input = $inputText
                expectedSkills = $expectedSkills
                predictedSkills = $predicted
                missingSkills = $missing
                forbiddenHit = $forbiddenHit
            }
    }

    if (-not (Test-RequiredOrder -PredictedSkills $predicted -RequiredOrder $requiredOrder)) {
        $orderingErrors += [pscustomobject]@{
                scenarioId = $scenarioId
                input = $inputText
                requiredOrder = $requiredOrder
                predictedSkills = $predicted
            }
    }

    if ($unexpected.Count -gt 0) {
        $unexpectedSkills += [pscustomobject]@{
                scenarioId = $scenarioId
                input = $inputText
                unexpected = $unexpected
                predictedSkills = $predicted
            }
    }
}

# 简体中文注释：检查每个 skill 至少在一个样例中被期望触发
$allSkillDirs = Get-ChildItem -Path (Join-Path $PSScriptRoot "..\..\..\.codex\skills") -Directory |
    Select-Object -ExpandProperty Name
foreach ($skill in $allSkillDirs) {
    if (-not $allExpectedSkillSet.Contains($skill)) {
        $warnings += [pscustomobject]@{
                scenarioId = "(global)"
                type = "SkillCoverageMissing"
                detail = "回放样例未覆盖 skill: $skill"
            }
    }
}

$totalCases = $cases.Count
$failedCaseCount = $failedCases.Count + $orderingErrors.Count
$passCount = [Math]::Max(0, $totalCases - $failedCaseCount)
$passRate = if ($totalCases -gt 0) { [Math]::Round(($passCount / $totalCases) * 100, 2) } else { 0 }

$summary = [pscustomobject]@{
    totalCases = $totalCases
    passedCases = $passCount
    failedCases = $failedCases.Count
    orderingErrorCount = $orderingErrors.Count
    unexpectedSkillCount = $unexpectedSkills.Count
    warningCount = $warnings.Count
    passRate = $passRate
    failOnWarning = [bool]$FailOnWarning
}

$report = [pscustomobject]@{
    summary = $summary
    failedCases = @($failedCases)
    orderingErrors = @($orderingErrors)
    unexpectedSkills = @($unexpectedSkills)
    warnings = @($warnings)
}

Write-Host ""
Write-Host "== Skill Regression 结果 ==" -ForegroundColor Cyan
Write-Host "总样例: $totalCases" -ForegroundColor Cyan
Write-Host "通过率: $passRate%" -ForegroundColor Cyan
Write-Host "失败样例: $($failedCases.Count)" -ForegroundColor Cyan
Write-Host "顺序错误: $($orderingErrors.Count)" -ForegroundColor Cyan
Write-Host "意外命中: $($unexpectedSkills.Count)" -ForegroundColor Cyan
Write-Host "告警数量: $($warnings.Count)" -ForegroundColor Cyan

if ($failedCases.Count -gt 0) {
    Write-Host ""
    Write-Host "[FAIL] 存在触发缺失或禁配命中：" -ForegroundColor Red
    $failedCases | Select-Object scenarioId, missingSkills, forbiddenHit | Format-Table -AutoSize
}

if ($orderingErrors.Count -gt 0) {
    Write-Host ""
    Write-Host "[FAIL] 存在顺序约束违规：" -ForegroundColor Red
    $orderingErrors | Select-Object scenarioId, requiredOrder | Format-Table -AutoSize
}

if ($unexpectedSkills.Count -gt 0) {
    Write-Host ""
    Write-Host "[WARN] 检测到意外 skill 命中：" -ForegroundColor Yellow
    $unexpectedSkills | Select-Object scenarioId, unexpected | Format-Table -AutoSize
}

if ($warnings.Count -gt 0) {
    Write-Host ""
    Write-Host "[WARN] 回归告警：" -ForegroundColor Yellow
    $warnings | Select-Object scenarioId, type, detail | Format-Table -AutoSize
}

if ($OutputJson) {
    $outputDir = Split-Path -Parent $OutputJson
    if ($outputDir -and -not (Test-Path -Path $outputDir -PathType Container)) {
        New-Item -ItemType Directory -Path $outputDir -Force | Out-Null
    }
    $report | ConvertTo-Json -Depth 12 | Out-File -FilePath $OutputJson -Encoding utf8
    Write-Host "[INFO] JSON 报告已输出: $OutputJson" -ForegroundColor Cyan
}

$blockingFailure = $failedCases.Count -gt 0 -or $orderingErrors.Count -gt 0
if ($FailOnWarning -and ($warnings.Count -gt 0 -or $unexpectedSkills.Count -gt 0)) {
    $blockingFailure = $true
}

if ($blockingFailure) {
    exit 1
}

exit 0
