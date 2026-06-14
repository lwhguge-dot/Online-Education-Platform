---
name: vue-component-split
description: Use when user says "拆分组件", "组件太大", "split component", "extract component". Extract self-contained sub-components from large Vue files.
---

# Vue 组件拆分

> 触发条件：用户说"拆分组件"、"组件太大"、"split component"、"extract component"、"大于300行"等。

## 适用场景

- Vue 文件超过 300 行
- 模板中有可独立的子功能（表单、列表项、筛选栏、弹窗、卡片等）
- 组件职责过多，需要关注点分离

## 拆分流程

### Phase 1: 分析源文件

```bash
# 统计行数
(Get-Content "src/views/XxxView.vue" | Measure-Object -Line).Lines

# 识别可提取部分
# - 模板中重复出现的 UI 模式
# - 独立的表单/弹窗/卡片
# - 可复用的列表项/筛选栏
```

读取完整文件，识别：
1. **自包含 UI 片段** — 有明确边界（开始/结束标签），不依赖父组件内部状态
2. **独立逻辑块** — 如日期选择器、轮播逻辑、题目渲染
3. **可复用组件** — 如筛选栏、加载骨架

### Phase 2: 设计组件接口

选择最小通信方式：
- **v-model** — 表单状态（推荐用于输入值）
- **props + emit** — 父子通信（推荐用于事件）
- **provide/inject** — 深层嵌套（谨慎使用）

接口设计原则：
- 新组件应该是自包含的（self-contained）
- 父组件保留页面编排逻辑
- 新组件的 scoped CSS 随组件迁移

### Phase 3: 创建新组件

创建路径规则：
- 页面级组件 → `src/components/{domain}/XxxItem.vue`
- UI 通用组件 → `src/components/ui/XxxWidget.vue`
- 按业务域分目录：`student/`、`teacher/`、`homework/`、`course/`、`home/`

新组件结构：
```vue
<script setup lang="ts">
// 1. 定义 props 和 emit
// 2. 实现组件逻辑
// 3. 保持组件自包含
</script>

<template>
  <!-- 从父组件模板中提取的片段 -->
</template>

<style scoped>
/* 从父组件迁移的 scoped 样式 */
</style>
```

### Phase 4: 更新父组件

1. 导入新组件
2. 替换模板中的内联代码为 `<ComponentName ...>`
3. 传递 props 和事件
4. 删除已迁移的逻辑和样式
5. 清理未使用的导入

### Phase 5: 验证

```bash
# 1. ESLint
npm run lint 2>&1 | Select-Object -Last 5

# 2. 类型检查
npm run type-check 2>&1 | Select-String "error TS" | Measure-Object | Select-Object -ExpandProperty Count

# 3. 测试
npm run test 2>&1
```

### Phase 6: 记录

更新 MEMORY.md 的"前端组件拆分"条目，记录：
- 原始行数 → 拆分后行数
- 提取的子组件名称和用途
- 降行百分比

## 拆分模式参考

| 模式 | 示例 | 通信方式 |
|------|------|---------|
| 弹窗/表单 | DatePicker, PasswordResetModal | v-model |
| 筛选栏 | SubjectFilter, HomeworkFilterBar | props(selected) + emit(select) |
| 卡片/列表项 | QuestionCard, ChapterList | props + slot |
| 轮播/动画 | HomeCarousel | props(data) |
| 图表 | UserTrendChart, QuizTrendChart | props(data) |

## 注意事项

- 拆分后父组件应 <300 行
- 新组件应 <250 行
- 不要过度拆分（<200 行的组件不需要拆）
- scoped CSS 必须随组件迁移，避免全局样式污染
- 拆分后运行完整的 lint + type-check + test 验证
