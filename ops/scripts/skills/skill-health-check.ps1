param(
    [string]$ProjectRoot = (Resolve-Path (Join-Path $PSScriptRoot "..\..\..")).Path,
    [string]$OutputJson = "",
    [switch]$Strict
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

# 简体中文注释：从 AGENTS.md 的“已安装 Skill 清单”区域提取项目本地 skill 基线
function Get-RequiredSkillsFromAgents {
    param(
        [string]$AgentsPath
    )

    if (-not (Test-Path -Path $AgentsPath -PathType Leaf)) {
        throw "AGENTS.md 不存在: $AgentsPath"
    }

    $content = Get-Content -Path $AgentsPath -Raw -Encoding UTF8
    $sectionMatch = [regex]::Match(
        $content,
        '(?ms)^##\s*2\.\s*已安装 Skill 清单\s*(.*?)^##\s*3\.',
        [System.Text.RegularExpressions.RegexOptions]::Multiline
    )

    if (-not $sectionMatch.Success) {
        throw "无法在 AGENTS.md 中定位 [已安装 Skill 清单] 区域。"
    }

    $skills = [regex]::Matches($sectionMatch.Groups[1].Value, '(?m)^\s*-\s*`([^`]+)`') |
        ForEach-Object { $_.Groups[1].Value.Trim() }

    # 简体中文注释：系统边界技能不属于项目本地 skill 基线，不参与目录一致性检查
    $excluded = @("skill-creator", "skill-installer")
    $projectSkills = $skills | Where-Object { $_ -and ($_ -notin $excluded) }

    return @($projectSkills | Sort-Object -Unique)
}

# 简体中文注释：检查 .gitignore 中是否错误地忽略了 .codex/ 根路径
function Is-CodexRootIgnored {
    param(
        [string]$GitignorePath
    )

    if (-not (Test-Path -Path $GitignorePath -PathType Leaf)) {
        return $false
    }

    return [bool](Select-String -Path $GitignorePath -Pattern '^\s*\.codex/\s*$' -Quiet)
}

$agentsPath = Join-Path $ProjectRoot "AGENTS.md"
$skillsRoot = Join-Path $ProjectRoot ".codex/skills"
$gitignorePath = Join-Path $ProjectRoot ".gitignore"

$parseError = $null
$requiredSkills = @()
try {
    $requiredSkills = Get-RequiredSkillsFromAgents -AgentsPath $agentsPath
}
catch {
    $parseError = $_.Exception.Message
}

if ($requiredSkills.Count -eq 0) {
    $message = "未从 AGENTS.md 解析到项目 skill 基线。"
    if ($parseError) {
        $message = "$message 详细原因: $parseError"
    }
    Write-Host "[FAIL] $message" -ForegroundColor Red
    if ($Strict) {
        exit 1
    }
}

if (-not (Test-Path -Path $skillsRoot -PathType Container)) {
    Write-Host "[FAIL] skill 目录不存在: $skillsRoot" -ForegroundColor Red
    exit 1
}

$directorySkills = @(
    Get-ChildItem -Path $skillsRoot -Directory |
        Select-Object -ExpandProperty Name |
        Sort-Object -Unique
)

$skillChecks = foreach ($skill in $requiredSkills) {
    $skillDir = Join-Path $skillsRoot $skill
    $skillMd = Join-Path $skillDir "SKILL.md"
    [pscustomobject]@{
        Skill          = $skill
        DirectoryFound = Test-Path -Path $skillDir -PathType Container
        SkillMdFound   = Test-Path -Path $skillMd -PathType Leaf
    }
}

$missingSkills = @($requiredSkills | Where-Object { $_ -notin $directorySkills })
$extraSkills = @($directorySkills | Where-Object { $_ -notin $requiredSkills })
$failedSkills = @($skillChecks | Where-Object { -not $_.DirectoryFound -or -not $_.SkillMdFound })
$codexRootIgnored = Is-CodexRootIgnored -GitignorePath $gitignorePath

$summary = [pscustomobject]@{
    RequiredSkillsCount = $requiredSkills.Count
    DirectorySkillsCount = $directorySkills.Count
    MissingSkillCount = $missingSkills.Count
    ExtraSkillCount = $extraSkills.Count
    MissingFileCount = $failedSkills.Count
    CodexRootIgnored = $codexRootIgnored
    StrictMode = [bool]$Strict
    AgentsParseError = if ($parseError) { $parseError } else { "" }
}

Write-Host ""
Write-Host "== Skill 健康检查结果 ==" -ForegroundColor Cyan
if ($skillChecks.Count -gt 0) {
    $skillChecks | Sort-Object Skill | Format-Table -AutoSize
}
else {
    Write-Host "[WARN] 未生成 skill 明细，请检查 AGENTS.md 解析结果。" -ForegroundColor Yellow
}

Write-Host ""
if ($codexRootIgnored) {
    Write-Host "[FAIL] .gitignore 仍存在 .codex/ 根忽略规则，不符合当前入库策略" -ForegroundColor Red
}
else {
    Write-Host "[PASS] .gitignore 未忽略 .codex/ 根路径，符合入库策略" -ForegroundColor Green
}

if ($missingSkills.Count -gt 0) {
    Write-Host ""
    Write-Host "[FAIL] 以下 skill 在目录中缺失：" -ForegroundColor Red
    $missingSkills | Sort-Object | ForEach-Object { Write-Host " - $_" -ForegroundColor Red }
}
else {
    Write-Host "[PASS] 所有基线 skill 目录均存在" -ForegroundColor Green
}

if ($failedSkills.Count -gt 0) {
    Write-Host ""
    Write-Host "[FAIL] 以下 skill 缺失 SKILL.md：" -ForegroundColor Red
    $failedSkills | Format-Table -AutoSize
}
else {
    Write-Host "[PASS] 所有基线 skill 均包含 SKILL.md" -ForegroundColor Green
}

if ($extraSkills.Count -gt 0) {
    $color = if ($Strict) { "Red" } else { "Yellow" }
    $level = if ($Strict) { "FAIL" } else { "WARN" }
    Write-Host ""
    Write-Host "[$level] 检测到基线之外的额外 skill：" -ForegroundColor $color
    $extraSkills | Sort-Object | ForEach-Object { Write-Host " - $_" -ForegroundColor $color }
}
else {
    Write-Host "[PASS] 未发现基线之外的额外 skill" -ForegroundColor Green
}

$result = [pscustomobject]@{
    summary = $summary
    requiredSkills = $requiredSkills
    directorySkills = $directorySkills
    missingSkills = $missingSkills
    extraSkills = $extraSkills
    failedSkills = $failedSkills
}

if ($OutputJson) {
    $outputDir = Split-Path -Parent $OutputJson
    if ($outputDir -and -not (Test-Path -Path $outputDir -PathType Container)) {
        New-Item -ItemType Directory -Path $outputDir -Force | Out-Null
    }
    $result | ConvertTo-Json -Depth 8 | Out-File -FilePath $OutputJson -Encoding utf8
    Write-Host ""
    Write-Host "[INFO] JSON 报告已输出: $OutputJson" -ForegroundColor Cyan
}

$hasBlockingFailure = $false
if ($parseError) { $hasBlockingFailure = $true }
if ($missingSkills.Count -gt 0) { $hasBlockingFailure = $true }
if ($failedSkills.Count -gt 0) { $hasBlockingFailure = $true }
if ($codexRootIgnored) { $hasBlockingFailure = $true }
if ($Strict -and $extraSkills.Count -gt 0) { $hasBlockingFailure = $true }

if ($hasBlockingFailure) {
    exit 1
}

exit 0
