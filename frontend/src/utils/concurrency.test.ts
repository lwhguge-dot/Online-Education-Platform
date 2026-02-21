import { describe, expect, it } from 'vitest'
import { mapWithConcurrency } from './concurrency'

describe('mapWithConcurrency', () => {
  it('应限制最大并发数并保持结果顺序', async () => {
    const input = [1, 2, 3, 4, 5, 6]
    let running = 0
    let peakRunning = 0

    const results = await mapWithConcurrency(input, 2, async (item) => {
      running += 1
      peakRunning = Math.max(peakRunning, running)
      await new Promise((resolve) => setTimeout(resolve, item % 2 === 0 ? 8 : 2))
      running -= 1
      return item * 10
    })

    expect(peakRunning).toBeLessThanOrEqual(2)
    expect(results).toEqual([
      { status: 'fulfilled', value: 10 },
      { status: 'fulfilled', value: 20 },
      { status: 'fulfilled', value: 30 },
      { status: 'fulfilled', value: 40 },
      { status: 'fulfilled', value: 50 },
      { status: 'fulfilled', value: 60 },
    ])
  })

  it('应返回 rejected 结果而不是直接中断整体流程', async () => {
    const results = await mapWithConcurrency([1, 2, 3], 2, async (item) => {
      if (item === 2) {
        throw new Error('模拟失败')
      }
      return item
    })

    expect(results[0]).toEqual({ status: 'fulfilled', value: 1 })
    expect(results[2]).toEqual({ status: 'fulfilled', value: 3 })
    expect(results[1]?.status).toBe('rejected')
  })
})
