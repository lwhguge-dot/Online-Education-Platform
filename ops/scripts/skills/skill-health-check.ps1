param(
    [string]$ProjectRoot = (Resolve-Path (Join-Path $PSScriptRoot "..\..\..")).Path
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

# 简体中文注释：定义本项目必须存在的 skill 列表（包含前端、后端与流程技能）
$requiredSkills = @(
    "planning-with-files",
    "ui-ux-pro-max",
    "brainstorming",
    "dispatching-parallel-agents",
    "executing-plans",
    "finishing-a-development-branch",
    "receiving-code-review",
    "requesting-code-review",
    "subagent-driven-development",
    "systematic-debugging",
    "test-driven-development",
    "using-git-worktrees",
    "using-superpowers",
    "verification-before-completion",
    "writing-plans",
    "writing-skills",
    "api-security-best-practices",
    "backend-security-coder",
    "docker-expert",
    "deployment-procedures",
    "distributed-tracing",
    "observability-engineer",
    "postgres-best-practices",
    "microservices-patterns"
)

# 简体中文注释：检查每个 skill 目录和 SKILL.md 文件是否存在
$skillsRoot = Join-Path $ProjectRoot ".codex/skills"
$skillChecks = foreach ($skill in $requiredSkills) {
    $skillDir = Join-Path $skillsRoot $skill
    $skillMd = Join-Path $skillDir "SKILL.md"

    [pscustomobject]@{
        Skill          = $skill
        DirectoryFound = Test-Path -Path $skillDir -PathType Container
        SkillMdFound   = Test-Path -Path $skillMd -PathType Leaf
    }
}

# 简体中文注释：校验 .gitignore 是否包含 .codex/ 忽略规则，避免本地技能误提交
$gitignorePath = Join-Path $ProjectRoot ".gitignore"
$codexIgnored = $false
if (Test-Path -Path $gitignorePath -PathType Leaf) {
    $codexIgnored = Select-String -Path $gitignorePath -Pattern '^\s*\.codex/\s*$' -Quiet
}

$failedSkills = @($skillChecks | Where-Object { -not $_.DirectoryFound -or -not $_.SkillMdFound })

Write-Host ""
Write-Host "== Skill 健康检查结果 ==" -ForegroundColor Cyan
$skillChecks | Sort-Object Skill | Format-Table -AutoSize

Write-Host ""
if ($codexIgnored) {
    Write-Host "[PASS] .gitignore 已包含 .codex/ 忽略规则" -ForegroundColor Green
}
else {
    Write-Host "[FAIL] .gitignore 缺少 .codex/ 忽略规则" -ForegroundColor Red
}

if ($failedSkills.Count -gt 0) {
    Write-Host ""
    Write-Host "[FAIL] 以下 skill 缺失目录或 SKILL.md：" -ForegroundColor Red
    $failedSkills | Format-Table -AutoSize
}
else {
    Write-Host "[PASS] 所有必需 skill 均存在且包含 SKILL.md" -ForegroundColor Green
}

if ($failedSkills.Count -gt 0 -or -not $codexIgnored) {
    exit 1
}

exit 0
