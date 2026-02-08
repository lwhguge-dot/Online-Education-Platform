param(
  [string]$Mode,
  [string]$CommitMessage,
  [string]$Confirm
)

$ErrorActionPreference = 'Stop'

function Z([string]$s) {
  return [regex]::Replace($s, '\\u([0-9a-fA-F]{4})', { param($m) [char]([Convert]::ToInt32($m.Groups[1].Value, 16)) })
}

Write-Host "=============================="
Write-Host (Z '\u0047\u0069\u0074 \u4e00\u952e\u63a8\u9001\u52a9\u624b')
Write-Host "=============================="
Write-Host ""

$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$repoRoot = Resolve-Path (Join-Path $scriptDir "..\\..\\..")
Set-Location $repoRoot

if (-not (Get-Command git -ErrorAction SilentlyContinue)) {
  Write-Host (Z '[\u9519\u8bef] \u672a\u68c0\u6d4b\u5230 Git\uff0c\u8bf7\u5148\u5b89\u88c5\u5e76\u52a0\u5165 PATH\u3002')
  Read-Host (Z '\u6309\u56de\u8f66\u9000\u51fa')
  exit 1
}

Write-Host (Z '[1/7] \u68c0\u67e5\u6587\u4ef6\u53d8\u52a8...')
Write-Host ""
$status = @(git status --short)
$status | ForEach-Object { Write-Host $_ }
Write-Host ""

git diff --quiet --exit-code | Out-Null
$unstaged = $LASTEXITCODE
git diff --quiet --cached --exit-code | Out-Null
$staged = $LASTEXITCODE
$untracked = @(git ls-files --others --exclude-standard).Count

# 检查是否有本地已提交但未推送的记录
$unpushed = @(git cherry -v 2>$null).Count

if ($unstaged -eq 0 -and $staged -eq 0 -and $untracked -eq 0) {
  if ($unpushed -gt 0) {
    Write-Host (Z "[\u63d0\u793a] \u68c0\u6d4b\u5230\u6709 $unpushed \u4e2a\u672c\u5730\u63d0\u4ea4\u5c1a\u672a\u63a8\u9001\u3002")
    $retryPush = Read-Host (Z '\u662f\u5426\u5c1d\u8bd5\u91cd\u65b0\u63a8\u9001\uff1f[y/N]')
    if ($retryPush.ToUpperInvariant() -eq "Y") {
        Write-Host ""
        Write-Host (Z '[6/7] \u6b63\u5728\u91cd\u8bd5\u63a8\u9001...')
        git push
        if ($LASTEXITCODE -eq 0) {
            Write-Host (Z '[\u5b8c\u6210] \u63a8\u9001\u6210\u529f\u3002')
            Read-Host (Z '\u6309\u56de\u8f66\u9000\u51fa')
            exit 0
        }
    }
  }
  Write-Host (Z '\u6ca1\u6709\u4efb\u4f55\u6587\u4ef6\uff0c\u4e5f\u65e0\u5f85\u63a8\u9001\u7684\u63d0\u4ea4\u3002')
  Read-Host (Z '\u6309\u56de\u8f66\u9000\u51fa')
  exit 0
}

Write-Host (Z '[2/7] \u81ea\u52a8\u751f\u6210\u672c\u6b21\u66f4\u65b0\u8bf4\u660e...')

$summary = New-Object System.Collections.Generic.List[string]
function Add-Summary([string]$text) {
  if (-not $summary.Contains($text)) { $summary.Add($text) }
}

foreach ($line in $status) {
  if ($line.Length -lt 4) { continue }
  $path = $line.Substring(3).Trim()
  if ($path -like "README*") { Add-Summary (Z '\u66f4\u65b0\u9879\u76ee\u6587\u6863\u4e0e\u8bf4\u660e'); continue }
  if ($path -like "docker-compose*") { Add-Summary (Z '\u8c03\u6574 Docker \u7f16\u6392\u914d\u7f6e'); continue }
  if ($path -like "tools/scripts*") { Add-Summary (Z '\u66f4\u65b0\u8fd0\u7ef4\u4e0e\u53d1\u5e03\u811a\u672c'); continue }
  if ($path -like "frontend/*") { Add-Summary (Z '\u4fee\u6539\u524d\u7aef\u529f\u80fd\u6216\u6784\u5efa\u914d\u7f6e'); continue }
  if ($path -like "backend/*") { Add-Summary (Z '\u4fee\u6539\u540e\u7aef\u670d\u52a1\u903b\u8f91\u6216\u914d\u7f6e'); continue }
  Add-Summary (Z '\u66f4\u65b0\u5176\u5b83\u5de5\u7a0b\u6587\u4ef6')
}

if ($summary.Count -eq 0) { Add-Summary (Z '\u4ee3\u7801\u7ef4\u62a4\u4e0e\u7ec6\u8282\u4fee\u590d') }
$autoSummary = ($summary -join (Z '\uff1b'))

Write-Host ""
Write-Host (Z '===== \u81ea\u52a8\u66f4\u65b0\u8bf4\u660e\uff08\u53ef\u4fee\u6539\uff09 =====')
Write-Host $autoSummary
Write-Host "==============================="
Write-Host ""

Write-Host (Z '[3/7] \u8bf7\u9009\u62e9\u63d0\u4ea4\u4fe1\u606f\u6765\u6e90\uff1a')
Write-Host (Z '  1. \u4f7f\u7528\u81ea\u52a8\u66f4\u65b0\u8bf4\u660e\uff08\u63a8\u8350\uff09')
Write-Host (Z '  2. \u7f16\u8f91\u81ea\u52a8\u66f4\u65b0\u8bf4\u660e')
Write-Host (Z '  3. \u5b8c\u5168\u624b\u52a8\u8f93\u5165')

if ([string]::IsNullOrWhiteSpace($Mode)) {
  $Mode = Read-Host (Z '\u8bf7\u8f93\u5165 [1/2/3]')
} else {
  Write-Host ((Z '\u8bf7\u8f93\u5165 [1/2/3]') + ": $Mode")
}
$Mode = $Mode.Trim()

if ($Mode -eq "1" -and [string]::IsNullOrWhiteSpace($Confirm) -and $CommitMessage -match '^(?i:y|n)$') {
  $Confirm = $CommitMessage
  $CommitMessage = $null
}

switch ($Mode) {
  "1" { $commitMsg = $autoSummary }
  "2" {
    if ([string]::IsNullOrWhiteSpace($CommitMessage)) {
      $commitMsg = Read-Host (Z '\u8bf7\u7f16\u8f91\u540e\u8f93\u5165\u63d0\u4ea4\u4fe1\u606f')
    } else {
      $commitMsg = $CommitMessage
      Write-Host ((Z '\u8bf7\u7f16\u8f91\u540e\u8f93\u5165\u63d0\u4ea4\u4fe1\u606f') + ": $commitMsg")
    }
  }
  default {
    if ([string]::IsNullOrWhiteSpace($CommitMessage)) {
      $commitMsg = Read-Host (Z '\u8bf7\u8f93\u5165\u63d0\u4ea4\u4fe1\u606f')
    } else {
      $commitMsg = $CommitMessage
      Write-Host ((Z '\u8bf7\u8f93\u5165\u63d0\u4ea4\u4fe1\u606f') + ": $commitMsg")
    }
  }
}

if ([string]::IsNullOrWhiteSpace($commitMsg)) {
  Write-Host (Z '\u63d0\u4ea4\u4fe1\u606f\u4e0d\u80fd\u4e3a\u7a7a\uff0c\u5df2\u53d6\u6d88\u3002')
  Read-Host (Z '\u6309\u56de\u8f66\u9000\u51fa')
  exit 1
}

Write-Host ""
Write-Host (Z '\u672c\u6b21\u5c06\u4f7f\u7528\u4ee5\u4e0b\u63d0\u4ea4\u4fe1\u606f\uff1a')
Write-Host $commitMsg
Write-Host ""

if ([string]::IsNullOrWhiteSpace($Confirm)) {
  $confirmInput = Read-Host (Z '\u786e\u8ba4\u63d0\u4ea4\u5e76\u63a8\u9001\u5417\uff1f[y/N]')
} else {
  $confirmInput = $Confirm
  Write-Host ((Z '\u786e\u8ba4\u63d0\u4ea4\u5e76\u63a8\u9001\u5417\uff1f[y/N]') + ": $confirmInput")
}

if ($confirmInput.ToUpperInvariant() -ne "Y") {
  Write-Host (Z '\u5df2\u53d6\u6d88\u3002')
  Read-Host (Z '\u6309\u56de\u8f66\u9000\u51fa')
  exit 0
}

Write-Host ""
Write-Host (Z '[4/7] \u6dfb\u52a0\u53d8\u52a8\u6587\u4ef6...')
git add .

Write-Host (Z '[5/7] \u63d0\u4ea4\u4e2d...')
git commit -m "$commitMsg"
if ($LASTEXITCODE -ne 0) {
  Write-Host (Z '\u63d0\u4ea4\u5931\u8d25\uff0c\u8bf7\u68c0\u67e5\u9519\u8bef\u4fe1\u606f\u3002')
  Read-Host (Z '\u6309\u56de\u8f66\u9000\u51fa')
  exit 1
}

Write-Host ""
Write-Host (Z '[6/7] \u63a8\u9001\u5230 GitHub...')
git push
if ($LASTEXITCODE -ne 0) {
  Write-Host (Z '\u63a8\u9001\u5931\u8d25\uff0c\u8bf7\u68c0\u67e5\u7f51\u7edc\u6216\u8fdc\u7aef\u5206\u652f\u51b2\u7a81\u3002')
  Read-Host (Z '\u6309\u56de\u8f66\u9000\u51fa')
  exit 1
}

Write-Host ""
Write-Host (Z '[7/7] \u5b8c\u6210\u3002')
Write-Host "=============================="
Write-Host (Z '  \u63a8\u9001\u6210\u529f')
Write-Host "=============================="
Write-Host (Z '\u4ed3\u5e93\u5730\u5740') ": https://github.com/lwhguge-dot/Online-Education-Platform"
Write-Host ""
Read-Host (Z '\u6309\u56de\u8f66\u9000\u51fa')
