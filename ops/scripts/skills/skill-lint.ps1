param(
    [string]$SkillsRoot = (Join-Path $PSScriptRoot "..\..\..\.codex\skills"),
    [string]$ContractFile = (Join-Path $PSScriptRoot "..\..\skills\route-contract.json"),
    [string]$OutputJson = ""
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

# 简体中文注释：返回 UTF-8 严格解码结果，发现非法字节时直接判定失败
function Test-ValidUtf8 {
    param([string]$Path)
    try {
        $bytes = [System.IO.File]::ReadAllBytes($Path)
        $utf8 = [System.Text.UTF8Encoding]::new($false, $true)
        $null = $utf8.GetString($bytes)
        return $true
    }
    catch {
        return $false
    }
}

# 简体中文注释：尝试解析 frontmatter，返回 name/description 供后续规则检查
function Get-FrontmatterMeta {
    param([string]$Content)

    $frontmatter = [regex]::Match($Content, '(?ms)^---\s*\r?\n(.*?)\r?\n---')
    if (-not $frontmatter.Success) {
        return $null
    }

    $block = $frontmatter.Groups[1].Value
    $nameMatch = [regex]::Match($block, '(?m)^name:\s*(.+)$')
    $descMatch = [regex]::Match($block, '(?m)^description:\s*(.+)$')

    $name = if ($nameMatch.Success) { $nameMatch.Groups[1].Value.Trim() } else { "" }
    $description = if ($descMatch.Success) { $descMatch.Groups[1].Value.Trim() } else { "" }
    if ($description.StartsWith('"') -and $description.EndsWith('"') -and $description.Length -ge 2) {
        $description = $description.Substring(1, $description.Length - 2)
    }

    return [pscustomobject]@{
        Name = $name
        Description = $description
    }
}

# 简体中文注释：校验引用路径是否真实存在（相对 skill 目录或仓库根目录）
function Test-PathReferenceExists {
    param(
        [string]$Token,
        [string]$SkillDir,
        [string]$RepoRoot
    )

    if ($Token -match '^(https?://|mailto:|#)') { return $true }
    if ($Token -match '^\$') { return $true }
    if ($Token -match '^--') { return $true }
    if ($Token -match '^\*') { return $true }
    if ($Token -match '[\r\n]') { return $true }
    if ($Token -match '^(TODO|FIXME)$') { return $true }

    $skillRel = Join-Path $SkillDir $Token
    $repoRel = Join-Path $RepoRoot $Token

    return (Test-Path -Path $Token) -or
        (Test-Path -Path $skillRel) -or
        (Test-Path -Path $repoRel)
}

# 简体中文注释：过滤伪路径（占位符、命令片段、API 路径、样式类名等），仅检查真实文件路径
function Should-CheckPathToken {
    param([string]$Token)

    if ([string]::IsNullOrWhiteSpace($Token)) { return $false }
    if ($Token -match '\s') { return $false }
    if ($Token -match '[<>{}]') { return $false }
    if ($Token -match '^\$') { return $false }
    if ($Token -match '^@') { return $false }
    if ($Token -match '\*') { return $false }
    if ($Token -match '^\~\/') { return $false }
    if ($Token -match '^/api/') { return $false }
    if ($Token -match '^(bg-|border-|text-|hover:|focus:|from-|to-|via-)') { return $false }
    if ($Token -match '^(task_plan\.md|findings\.md|progress\.md)$') { return $false }
    if ($Token -match '^(docs/plans/|worktrees/|\.worktrees/|design-system/)') { return $false }
    if ($Token -match '^\.[a-z0-9]+$') { return $false }
    if ($Token -match '^[A-Za-z0-9._-]+$') { return $false }
    if ($Token -match '[\\/]') { return $true }
    if ($Token -match '\.(md|json|ya?ml|py|ps1|sh|js|ts|txt)$') { return $true }
    return $false
}

if (-not (Test-Path -Path $SkillsRoot -PathType Container)) {
    Write-Host "[FAIL] SkillsRoot 不存在: $SkillsRoot" -ForegroundColor Red
    exit 1
}

$repoRoot = (Resolve-Path (Join-Path $SkillsRoot "..\..")).Path
$skillDirs = Get-ChildItem -Path $SkillsRoot -Directory | Sort-Object Name

$bannedRules = @(
    @{ Name = "ForbiddenWordClaude"; Pattern = '\bClaude\b' },
    @{ Name = "ForbiddenWordTodoWrite"; Pattern = '\bTodoWrite\b' },
    @{ Name = "ForbiddenPathDotClaude"; Pattern = '\.claude' }
)

$violations = @()
$files = @()

foreach ($skillDir in $skillDirs) {
    $skillName = $skillDir.Name
    $skillFile = Join-Path $skillDir.FullName "SKILL.md"
    $fileViolations = @()

    if (-not (Test-Path -Path $skillFile -PathType Leaf)) {
        $fileViolations += [pscustomobject]@{
                rule = "MissingSkillMd"
                message = "缺少 SKILL.md"
                detail = $skillFile
            }
        $files += [pscustomobject]@{
                skill = $skillName
                file = $skillFile
                violationCount = $fileViolations.Count
            }
        $fileViolations | ForEach-Object { $violations += [pscustomobject]@{ skill = $skillName; file = $skillFile; rule = $_.rule; message = $_.message; detail = $_.detail } }
        continue
    }

    if (-not (Test-ValidUtf8 -Path $skillFile)) {
        $fileViolations += [pscustomobject]@{
                rule = "InvalidUtf8"
                message = "文件不是严格 UTF-8 编码"
                detail = $skillFile
            }
    }

    $content = Get-Content -Path $skillFile -Raw -Encoding UTF8
    $meta = Get-FrontmatterMeta -Content $content

    if ($null -eq $meta) {
        $fileViolations += [pscustomobject]@{
                rule = "MissingFrontmatter"
                message = "缺少 frontmatter（---）"
                detail = "必须包含 name 与 description"
            }
    }
    else {
        if ([string]::IsNullOrWhiteSpace($meta.Name)) {
            $fileViolations += [pscustomobject]@{
                    rule = "MissingName"
                    message = "frontmatter 缺少 name"
                    detail = ""
                }
        }

        if ([string]::IsNullOrWhiteSpace($meta.Description)) {
            $fileViolations += [pscustomobject]@{
                    rule = "MissingDescription"
                    message = "frontmatter 缺少 description"
                    detail = ""
                }
        }
        elseif ($meta.Description -notmatch '^Use when\b') {
            $fileViolations += [pscustomobject]@{
                    rule = "DescriptionMustStartUseWhen"
                    message = "description 必须以 [Use when] 开头"
                    detail = $meta.Description
                }
        }
    }

    if ($content -notmatch '##\s*中文执行层') {
        $fileViolations += [pscustomobject]@{
                rule = "MissingChineseExecutionSection"
                message = "缺少 [中文执行层] 章节"
                detail = "需包含触发条件/前置条件/执行步骤/完成证据/失败回退"
            }
    }

    foreach ($rule in $bannedRules) {
        if ([regex]::IsMatch($content, $rule.Pattern)) {
            $fileViolations += [pscustomobject]@{
                    rule = $rule.Name
                    message = "检测到禁用平台耦合词"
                    detail = $rule.Pattern
                }
        }
    }

    # 简体中文注释：移除代码块再扫描行内反引号，降低伪路径误报
    $contentNoFences = [regex]::Replace($content, '(?ms)```.*?```', '')
    $tokens = [regex]::Matches($contentNoFences, '`([^`]+)`') |
        ForEach-Object { $_.Groups[1].Value.Trim() } |
        Select-Object -Unique

    foreach ($token in $tokens) {
        if (-not (Should-CheckPathToken -Token $token)) {
            continue
        }
        if (-not (Test-PathReferenceExists -Token $token -SkillDir $skillDir.FullName -RepoRoot $repoRoot)) {
            $fileViolations += [pscustomobject]@{
                    rule = "MissingPathReference"
                    message = "文档中的路径引用不存在"
                    detail = $token
                }
        }
    }

    $files += [pscustomobject]@{
            skill = $skillName
            file = $skillFile
            violationCount = $fileViolations.Count
        }

    foreach ($entry in $fileViolations) {
        $violations += [pscustomobject]@{
                skill = $skillName
                file = $skillFile
                rule = $entry.rule
                message = $entry.message
                detail = $entry.detail
            }
    }
}

# 简体中文注释：可选校验 route-contract 的字段完整性及 skill 引用合法性
if ($ContractFile -and (Test-Path -Path $ContractFile -PathType Leaf)) {
    try {
        $contract = Get-Content -Path $ContractFile -Raw -Encoding UTF8 | ConvertFrom-Json
        $requiredFields = @("scenarioId", "input", "expectedSkills", "requiredOrder", "forbiddenSkills", "notes")
        $skillNames = @($skillDirs | Select-Object -ExpandProperty Name)
        foreach ($item in $contract) {
            foreach ($field in $requiredFields) {
                if (-not ($item.PSObject.Properties.Name -contains $field)) {
                    $violations += [pscustomobject]@{
                            skill = "(contract)"
                            file = $ContractFile
                            rule = "ContractFieldMissing"
                            message = "契约项缺少字段"
                            detail = "$($item.scenarioId): $field"
                        }
                }
            }
            foreach ($skill in @($item.expectedSkills)) {
                if ($skill -notin $skillNames) {
                    $violations += [pscustomobject]@{
                            skill = "(contract)"
                            file = $ContractFile
                            rule = "UnknownSkillInContract"
                            message = "契约中引用了不存在的 skill"
                            detail = "$($item.scenarioId): $skill"
                        }
                }
            }
        }
    }
    catch {
        $violations += [pscustomobject]@{
                skill = "(contract)"
                file = $ContractFile
                rule = "ContractJsonInvalid"
                message = "契约 JSON 解析失败"
                detail = $_.Exception.Message
            }
    }
}

$summary = [pscustomobject]@{
    skillsChecked = $skillDirs.Count
    fileCount = $files.Count
    violationCount = $violations.Count
    passed = ($violations.Count -eq 0)
}

$report = [pscustomobject]@{
    summary = $summary
    violations = @($violations)
    files = @($files)
}

Write-Host ""
Write-Host "== Skill Lint 结果 ==" -ForegroundColor Cyan
Write-Host "检查文件: $($summary.fileCount)" -ForegroundColor Cyan
Write-Host "违规数量: $($summary.violationCount)" -ForegroundColor Cyan

if ($violations.Count -gt 0) {
    $violations |
        Select-Object skill, rule, message, detail |
        Format-Table -AutoSize
}
else {
    Write-Host "[PASS] 未发现 lint 违规项。" -ForegroundColor Green
}

if ($OutputJson) {
    $outputDir = Split-Path -Parent $OutputJson
    if ($outputDir -and -not (Test-Path -Path $outputDir -PathType Container)) {
        New-Item -ItemType Directory -Path $outputDir -Force | Out-Null
    }
    $report | ConvertTo-Json -Depth 10 | Out-File -FilePath $OutputJson -Encoding utf8
    Write-Host "[INFO] JSON 报告已输出: $OutputJson" -ForegroundColor Cyan
}

if ($violations.Count -gt 0) {
    exit 1
}

exit 0
