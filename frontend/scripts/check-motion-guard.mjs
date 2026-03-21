import fs from 'node:fs'
import path from 'node:path'
import process from 'node:process'

const projectRoot = process.cwd()
const srcRoot = path.join(projectRoot, 'src')

/**
 * 中文注释：递归收集目标文件，仅检查 Vue 与 CSS，避免误扫构建产物。
 * @param {string} dir
 * @returns {string[]}
 */
function collectTargetFiles(dir) {
  const entries = fs.readdirSync(dir, { withFileTypes: true })
  const files = []
  for (const entry of entries) {
    const fullPath = path.join(dir, entry.name)
    if (entry.isDirectory()) {
      files.push(...collectTargetFiles(fullPath))
      continue
    }
    if (!entry.isFile()) {
      continue
    }
    if (fullPath.endsWith('.css') || fullPath.endsWith('.vue')) {
      files.push(fullPath)
    }
  }
  return files
}

/**
 * 中文注释：检查 animation: ... infinite 是否在同一规则块内配置了 iteration-count 门控。
 * @param {string[]} lines
 * @param {number} startIndex
 * @returns {boolean}
 */
function hasIterationGuard(lines, startIndex) {
  for (let i = startIndex + 1; i < Math.min(startIndex + 10, lines.length); i += 1) {
    const line = lines[i]
    if (/animation-iteration-count\s*:/.test(line)) {
      return true
    }
    if (/^\s*}\s*$/.test(line)) {
      return false
    }
  }
  return false
}

const violations = []
const files = collectTargetFiles(srcRoot)

for (const filePath of files) {
  const content = fs.readFileSync(filePath, 'utf8')
  const lines = content.split(/\r?\n/)
  const relativePath = path.relative(projectRoot, filePath).replace(/\\/g, '/')

  lines.forEach((line, index) => {
    if (/\btransition-all\b/.test(line)) {
      violations.push({
        rule: 'no-transition-all',
        file: relativePath,
        line: index + 1,
        detail: line.trim()
      })
    }

    if (/animation\s*:\s*[^;]*\binfinite\b/.test(line)) {
      const sameLineGuard = /animation-iteration-count\s*:/.test(line)
      if (sameLineGuard) {
        return
      }
      const guarded = hasIterationGuard(lines, index)
      if (!guarded) {
        violations.push({
          rule: 'infinite-animation-must-be-guarded',
          file: relativePath,
          line: index + 1,
          detail: line.trim()
        })
      }
    }
  })
}

if (violations.length > 0) {
  console.error('Motion guard failed. 发现以下违规项：')
  for (const violation of violations) {
    console.error(
      `- [${violation.rule}] ${violation.file}:${violation.line} -> ${violation.detail}`
    )
  }
  process.exit(1)
}

console.log('Motion guard passed. 未发现 transition-all 或未门控 infinite 动画。')
