# AI Skills 月度维护策略（项目本地）

## 1. 目标
- 保持 `.codex/skills` 能力稳定、可追溯、低噪音。
- 仅维护当前项目本地技能，不改全局技能目录。

## 2. 维护频率
- 每月一次，建议每月第一个工作周执行。
- 若出现高优先级安全需求，可额外执行临时更新。

## 3. 固定技能范围
- `planning-with-files`（1 个）
- `superpowers` 核心 14 个
- `antigravity` 精选 8 个（固定，不默认扩容）
- `ui-ux-pro-max`（保留）

## 4. 标准维护流程
1. 执行健康检查脚本，确认当前状态正常：
   - `powershell -ExecutionPolicy Bypass -File .\\ops\\scripts\\skills\\skill-health-check.ps1`
2. 备份本地技能目录：
   - `New-Item -ItemType Directory -Force -Path .\\ops\\backups | Out-Null`
   - `$stamp = Get-Date -Format 'yyyyMMdd-HHmmss'`
   - `Compress-Archive -Path .\\.codex\\skills\\* -DestinationPath ".\\ops\\backups\\skills-$stamp.zip"`
3. 按既定来源重新安装（覆盖更新）：
   - 安装器：`$installer = "$HOME/.codex/skills/.system/skill-installer/scripts/install-skill-from-github.py"`
   - 使用 `python` 执行安装器（Windows 环境默认）
4. 更新后再次执行健康检查脚本。
5. 抽样做触发验证（至少 3 条）：
   - `Use planning-with-files ...`
   - `Use brainstorming ...`
   - `Use api-security-best-practices ...`

## 5. 更新命令模板
```powershell
$installer = "$HOME/.codex/skills/.system/skill-installer/scripts/install-skill-from-github.py"
$dest = Join-Path (Get-Location) ".codex/skills"

# planning-with-files
python $installer `
  --repo OthmanAdi/planning-with-files `
  --path ".codex/skills/planning-with-files" `
  --dest $dest `
  --method auto

# superpowers（14 个）
$sp = @(
  "brainstorming","dispatching-parallel-agents","executing-plans",
  "finishing-a-development-branch","receiving-code-review","requesting-code-review",
  "subagent-driven-development","systematic-debugging","test-driven-development",
  "using-git-worktrees","using-superpowers","verification-before-completion",
  "writing-plans","writing-skills"
) | ForEach-Object { "skills/$_" }

python $installer `
  --repo obra/superpowers `
  --path $sp `
  --dest $dest `
  --method auto

# antigravity 精选 8 个（固定集）
$ag = @(
  "api-security-best-practices","backend-security-coder","docker-expert",
  "deployment-procedures","distributed-tracing","observability-engineer",
  "postgres-best-practices","microservices-patterns"
) | ForEach-Object { "skills/$_" }

python $installer `
  --repo sickn33/antigravity-awesome-skills `
  --path $ag `
  --dest $dest `
  --method auto
```

## 6. 回滚策略
- 更新异常时，先删除异常技能目录，再解压最近一次备份恢复：
  - `Remove-Item -Recurse -Force .\\.codex\\skills\\<skill-name>`
  - `Expand-Archive -Path .\\ops\\backups\\skills-<timestamp>.zip -DestinationPath .\\temp\\skills-restore`
- 恢复后重新运行健康检查脚本并确认通过。

