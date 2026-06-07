import { describe, expect, it } from 'vitest'
import { formatDateCN, formatDateTimeCN, formatTimeCN, parseToDate } from './datetime'

describe('datetime utils', () => {
  it('应将纯日期按东八区零点解析', () => {
    const parsed = parseToDate('2026-02-19')
    expect(parsed).not.toBeNull()
    // 2026-02-19 00:00:00 +08:00 对应 UTC 前一天 16:00:00
    expect(parsed?.toISOString()).toBe('2026-02-18T16:00:00.000Z')
  })

  it('应将无时区的 T 格式时间按 UTC 解析', () => {
    const parsed = parseToDate('2026-02-09T03:44:05')
    expect(parsed).not.toBeNull()
    expect(parsed?.toISOString()).toBe('2026-02-09T03:44:05.000Z')
  })

  it('应保持带时区时间的原始语义', () => {
    const parsed = parseToDate('2026-02-09T03:44:05+08:00')
    expect(parsed).not.toBeNull()
    expect(parsed?.toISOString()).toBe('2026-02-08T19:44:05.000Z')
  })

  it('无效输入应返回 fallback', () => {
    expect(formatDateTimeCN('invalid-date', 'N/A')).toBe('N/A')
    expect(formatDateCN(null, 'N/A')).toBe('N/A')
    expect(formatTimeCN(undefined, 'N/A')).toBe('N/A')
  })

  it('有效输入应输出中文日期时间文本', () => {
    const value = formatDateTimeCN('2026-02-09T03:44:05Z')
    expect(value).toContain('2026')
    expect(value).toContain('02')
  })
})
