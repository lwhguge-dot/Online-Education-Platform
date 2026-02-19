<script setup>
/**
 * 仪表盘骨架屏组件
 * 用于管理员仪表盘加载时展示，模拟统计卡片和图表区域的占位效果。
 *
 * @prop {Number} statsCount - 统计卡片数量
 * @prop {Boolean} showCharts - 是否显示图表占位
 */
defineProps({
  statsCount: {
    type: Number,
    default: 4
  },
  showCharts: {
    type: Boolean,
    default: true
  }
})
</script>

<template>
  <div class="skeleton-dashboard space-y-6 animate-pulse">
    <!-- 统计卡片骨架 -->
    <div class="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
      <div
        v-for="i in statsCount"
        :key="i"
        class="skeleton-stat-card bg-white/60 backdrop-blur-sm rounded-2xl p-6 border border-white/50"
        :style="{ animationDelay: (i * 0.1) + 's' }"
      >
        <div class="flex items-center justify-between mb-4">
          <div class="h-4 w-20 bg-slate-200 rounded"></div>
          <div class="h-8 w-8 bg-slate-200 rounded-lg"></div>
        </div>
        <div class="h-8 w-24 bg-slate-200 rounded mb-2"></div>
        <div class="h-3 w-16 bg-slate-200 rounded"></div>
      </div>
    </div>

    <!-- 图表区域骨架 -->
    <div v-if="showCharts" class="grid grid-cols-1 lg:grid-cols-2 gap-6">
      <!-- 折线图骨架 -->
      <div class="skeleton-chart bg-white/60 backdrop-blur-sm rounded-2xl p-6 border border-white/50">
        <div class="flex items-center justify-between mb-4">
          <div class="h-4 w-28 bg-slate-200 rounded"></div>
          <div class="flex gap-2">
            <div class="h-6 w-12 bg-slate-200 rounded"></div>
            <div class="h-6 w-12 bg-slate-200 rounded"></div>
            <div class="h-6 w-12 bg-slate-200 rounded"></div>
          </div>
        </div>
        <div class="flex items-center justify-center gap-4 mb-4">
          <div class="h-3 w-16 bg-slate-200 rounded"></div>
          <div class="h-3 w-16 bg-slate-200 rounded"></div>
          <div class="h-3 w-16 bg-slate-200 rounded"></div>
        </div>
        <div class="h-40 flex items-end justify-between gap-2 px-4">
          <div v-for="i in 7" :key="i"
               class="flex-1 bg-slate-200 rounded-t"
               :style="{ height: Math.random() * 80 + 20 + '%', animationDelay: (i * 0.1) + 's' }">
          </div>
        </div>
        <div class="flex justify-between mt-2 px-4">
          <div v-for="i in 7" :key="'l-' + i" class="h-3 w-8 bg-slate-200 rounded"></div>
        </div>
      </div>

      <!-- 饼图骨架 -->
      <div class="skeleton-chart bg-white/60 backdrop-blur-sm rounded-2xl p-6 border border-white/50">
        <div class="flex items-center justify-between mb-4">
          <div class="h-4 w-28 bg-slate-200 rounded"></div>
          <div class="h-3 w-20 bg-slate-200 rounded"></div>
        </div>
        <div class="flex items-start gap-6">
          <!-- 圆形占位 -->
          <div class="w-36 h-36 bg-slate-200 rounded-full flex-shrink-0"></div>
          <!-- 图例列表占位 -->
          <div class="flex-1 space-y-3">
            <div v-for="i in 5" :key="'legend-' + i"
                 class="flex items-center justify-between p-2"
                 :style="{ animationDelay: (i * 0.1) + 's' }">
              <div class="flex items-center gap-2">
                <div class="w-3 h-3 bg-slate-200 rounded-sm"></div>
                <div class="h-3 w-12 bg-slate-200 rounded"></div>
              </div>
              <div class="flex items-center gap-2">
                <div class="h-3 w-8 bg-slate-200 rounded"></div>
                <div class="h-3 w-8 bg-slate-200 rounded"></div>
              </div>
            </div>
          </div>
        </div>
        <!-- 底部汇总占位 -->
        <div class="mt-4 pt-3 border-t border-gray-100 flex items-center justify-around">
          <div class="text-center">
            <div class="h-6 w-12 bg-slate-200 rounded mx-auto mb-1"></div>
            <div class="h-3 w-16 bg-slate-200 rounded"></div>
          </div>
          <div class="w-px h-8 bg-gray-200"></div>
          <div class="text-center">
            <div class="h-6 w-12 bg-slate-200 rounded mx-auto mb-1"></div>
            <div class="h-3 w-16 bg-slate-200 rounded"></div>
          </div>
          <div class="w-px h-8 bg-gray-200"></div>
          <div class="text-center">
            <div class="h-6 w-12 bg-slate-200 rounded mx-auto mb-1"></div>
            <div class="h-3 w-16 bg-slate-200 rounded"></div>
          </div>
        </div>
      </div>
    </div>

    <!-- 列表区域骨架 -->
    <div class="grid grid-cols-1 lg:grid-cols-2 gap-6">
      <!-- 用户列表骨架 -->
      <div class="skeleton-list bg-white/60 backdrop-blur-sm rounded-2xl p-6 border border-white/50">
        <div class="flex items-center justify-between mb-4">
          <div class="h-4 w-24 bg-slate-200 rounded"></div>
          <div class="h-6 w-16 bg-slate-200 rounded"></div>
        </div>
        <div class="space-y-3">
          <div v-for="i in 5" :key="'user-' + i"
               class="flex items-center gap-3 p-2"
               :style="{ animationDelay: (i * 0.08) + 's' }">
            <div class="w-10 h-10 bg-slate-200 rounded-full"></div>
            <div class="flex-1">
              <div class="h-4 w-24 bg-slate-200 rounded mb-1"></div>
              <div class="h-3 w-32 bg-slate-200 rounded"></div>
            </div>
            <div class="h-6 w-14 bg-slate-200 rounded-full"></div>
          </div>
        </div>
      </div>

      <!-- 活动日志骨架 -->
      <div class="skeleton-list bg-white/60 backdrop-blur-sm rounded-2xl p-6 border border-white/50">
        <div class="flex items-center justify-between mb-4">
          <div class="h-4 w-24 bg-slate-200 rounded"></div>
          <div class="h-6 w-16 bg-slate-200 rounded"></div>
        </div>
        <div class="space-y-3">
          <div v-for="i in 5" :key="'log-' + i"
               class="flex items-start gap-3 p-2"
               :style="{ animationDelay: (i * 0.08) + 's' }">
            <div class="w-8 h-8 bg-slate-200 rounded-lg mt-0.5"></div>
            <div class="flex-1">
              <div class="h-4 w-full bg-slate-200 rounded mb-1"></div>
              <div class="h-3 w-20 bg-slate-200 rounded"></div>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
@keyframes shimmer {
  0% {
    opacity: 0.6;
  }
  50% {
    opacity: 1;
  }
  100% {
    opacity: 0.6;
  }
}

.skeleton-dashboard .bg-slate-200 {
  /* P1 第二批：仪表盘骨架动画压缩 */
  animation: shimmer var(--motion-duration-medium) var(--motion-ease-standard) infinite;
}

.skeleton-stat-card,
.skeleton-chart,
.skeleton-list {
  background: linear-gradient(90deg, rgba(255,255,255,0.6) 25%, rgba(255,255,255,0.8) 50%, rgba(255,255,255,0.6) 75%);
  background-size: 200% 100%;
}
</style>
