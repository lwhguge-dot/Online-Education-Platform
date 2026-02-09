// 统一的东八区时间处理工具
// 兼容策略：
// 1) 带时区的时间字符串（如 ...Z / ...+08:00）按原始时区解析
// 2) 不带时区但包含 T 的历史时间（如 2026-02-09T03:44:05）按 UTC 解析
//    再统一格式化到 Asia/Shanghai，兼容历史数据展示偏差
// 3) 仅日期字符串（YYYY-MM-DD）按东八区当天 00:00:00 解析

const SHANGHAI_TIME_ZONE = 'Asia/Shanghai'

const DATE_ONLY_REGEXP = /^\d{4}-\d{2}-\d{2}$/
const HAS_TIME_REGEXP = /T/
const HAS_ZONE_REGEXP = /(Z|[+-]\d{2}:?\d{2})$/i

export type DateInput = string | number | Date | null | undefined

export function parseToDate(input: DateInput): Date | null {
  if (input == null) return null

  if (input instanceof Date) {
    return Number.isNaN(input.getTime()) ? null : input
  }

  if (typeof input === 'number') {
    const date = new Date(input)
    return Number.isNaN(date.getTime()) ? null : date
  }

  const raw = String(input).trim()
  if (!raw) return null

  if (DATE_ONLY_REGEXP.test(raw)) {
    const date = new Date(`${raw}T00:00:00+08:00`)
    return Number.isNaN(date.getTime()) ? null : date
  }

  if (HAS_TIME_REGEXP.test(raw) && !HAS_ZONE_REGEXP.test(raw)) {
    const date = new Date(`${raw}Z`)
    return Number.isNaN(date.getTime()) ? null : date
  }

  const date = new Date(raw)
  return Number.isNaN(date.getTime()) ? null : date
}

export function formatDateTimeCN(input: DateInput, fallback = '-'): string {
  const date = parseToDate(input)
  if (!date) return fallback

  return new Intl.DateTimeFormat('zh-CN', {
    timeZone: SHANGHAI_TIME_ZONE,
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit',
    hour12: false
  }).format(date)
}

export function formatDateCN(input: DateInput, fallback = '-'): string {
  const date = parseToDate(input)
  if (!date) return fallback

  return new Intl.DateTimeFormat('zh-CN', {
    timeZone: SHANGHAI_TIME_ZONE,
    year: 'numeric',
    month: '2-digit',
    day: '2-digit'
  }).format(date)
}

export function formatTimeCN(input: DateInput, fallback = '-'): string {
  const date = parseToDate(input)
  if (!date) return fallback

  return new Intl.DateTimeFormat('zh-CN', {
    timeZone: SHANGHAI_TIME_ZONE,
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit',
    hour12: false
  }).format(date)
}

