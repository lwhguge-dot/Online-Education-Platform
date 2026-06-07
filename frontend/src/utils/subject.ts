/**
 * 学科相关工具函数
 * 统一管理学科颜色映射，避免跨文件重复定义
 */

/**
 * 获取学科渐变色（用于卡片背景）
 * @param subject 学科名称
 * @returns Tailwind 渐变色类名
 */
export const getSubjectColor = (subject: string | null | undefined): string => {
  const colors: Record<string, string> = {
    '语文': 'from-yanzhi to-qianhong',
    '数学': 'from-qinghua to-halanzi',
    '英语': 'from-danqing to-qingbai',
    '物理': 'from-zijinghui to-qianniuzi',
    '化学': 'from-tianlv to-qingsong',
    '生物': 'from-danya to-tianlv',
    '政治': 'from-yanzhihong to-yanzhi',
    '历史': 'from-tanxiang to-zhizi',
    '地理': 'from-qinghua to-danqing'
  }
  return colors[subject || ''] || 'from-danqing to-qinghua'
}

/**
 * 获取学科按钮样式（用于筛选按钮）
 * @param subject 学科名称
 * @returns Tailwind 类名
 */
export const getSubjectBtnStyle = (subject: string): string => {
  const styles: Record<string, string> = {
    '语文': 'bg-yanzhihong hover:bg-yanzhi text-white',
    '数学': 'bg-qinghua hover:bg-halanzi text-white',
    '英语': 'bg-danqing hover:bg-qingbai text-white',
    '物理': 'bg-zijinghui hover:bg-qianniuzi text-white',
    '化学': 'bg-tianlv hover:bg-qingsong text-white',
    '生物': 'bg-danya hover:bg-tianlv text-text-main',
    '政治': 'bg-yanzhihong hover:bg-yanzhi text-white',
    '历史': 'bg-tanxiang hover:bg-zhizi text-white',
    '地理': 'bg-qinghua hover:bg-danqing text-white'
  }
  return styles[subject] || 'bg-white text-shuimo hover:bg-gray-100'
}
