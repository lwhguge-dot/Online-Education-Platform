/**
 * 受控并发映射：按输入顺序返回 PromiseSettledResult，且不会因单项失败中断整体流程。
 */
export const mapWithConcurrency = async <T, R>(
  items: T[],
  limit: number,
  worker: (item: T, index: number) => Promise<R>,
): Promise<PromiseSettledResult<R>[]> => {
  if (items.length === 0) {
    return []
  }

  // 并发上限兜底为 1，避免非法参数导致任务丢失
  const safeLimit = Math.max(1, Math.floor(limit) || 1)
  const results: PromiseSettledResult<R>[] = new Array(items.length)
  let nextIndex = 0

  const runWorker = async (): Promise<void> => {
    while (true) {
      const currentIndex = nextIndex
      nextIndex += 1
      if (currentIndex >= items.length) {
        return
      }

      // noUncheckedIndexedAccess 开启后，数组按索引读取需要显式处理“空洞”场景
      if (!(currentIndex in items)) {
        results[currentIndex] = {
          status: 'rejected',
          reason: new Error(`并发任务缺少索引 ${currentIndex} 的输入项`),
        }
        continue
      }

      try {
        const value = await worker(items[currentIndex] as T, currentIndex)
        results[currentIndex] = { status: 'fulfilled', value }
      } catch (reason) {
        results[currentIndex] = { status: 'rejected', reason }
      }
    }
  }

  const runnerCount = Math.min(safeLimit, items.length)
  const runners = Array.from({ length: runnerCount }, () => runWorker())
  await Promise.all(runners)
  return results
}
