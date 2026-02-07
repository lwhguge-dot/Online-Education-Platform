<script setup>
import { computed } from 'vue'
import { getImageUrl } from '../../services/api'
import { BookOpen, Play, Star, Users } from 'lucide-vue-next'

const props = defineProps({
  course: {
    type: Object,
    required: true
  }
})

const emit = defineEmits(['click'])
</script>

<template>
  <div 
    class="group relative rounded-2xl overflow-hidden card-hover-lift cursor-pointer h-64 bg-white shadow-sm border border-slate-100"
    @click="$emit('click', course)"
  >
    <!-- 封面层 -->
    <div class="absolute inset-0">
      <img 
        v-if="course.coverImage" 
        :src="getImageUrl(course.coverImage)" 
        loading="lazy" 
        class="w-full h-full object-cover transition-transform duration-700 group-hover:scale-110" 
      />
      <div 
        v-else 
        :class="['w-full h-full bg-gradient-to-br', course.color || 'from-danqing to-qinghua']"
      >
        <div class="w-full h-full flex items-center justify-center">
          <BookOpen class="w-16 h-16 text-white/30" />
        </div>
      </div>
    </div>
    
    <!-- 渐变遮罩 (增强文字可读性) -->
    <div class="absolute inset-0 bg-gradient-to-t from-black/80 via-black/20 to-transparent opacity-60 group-hover:opacity-70 transition-opacity"></div>
    
    <!-- 悬浮播放按钮 (可覆盖) -->
    <slot name="overlay">
      <div class="absolute inset-0 flex items-center justify-center opacity-0 group-hover:opacity-100 transition-all duration-300 scale-90 group-hover:scale-100">
        <div class="w-14 h-14 rounded-full bg-white/20 backdrop-blur-sm border border-white/50 flex items-center justify-center shadow-lg group-active:scale-95 transition-transform">
          <div class="w-10 h-10 rounded-full bg-white text-danqing flex items-center justify-center shadow-sm">
            <Play class="w-4 h-4 ml-0.5 fill-current" />
          </div>
        </div>
      </div>
    </slot>
    
    <!-- 学科标签（左上角）- 已移除 -->
    
    <!-- 评分标签（右上角） -->
    <slot name="badge">
      <div class="absolute top-3 right-3 px-2.5 py-1 rounded-lg backdrop-blur-md bg-black/20 border border-white/10 text-xs font-medium flex items-center gap-1 text-white shadow-sm">
        <Star class="w-3.5 h-3.5 text-yellow-400 fill-current" />
        <span>{{ course.rating }}</span>
      </div>
    </slot>
    
    <!-- 底部信息面板 (可覆盖) -->
    <div class="absolute bottom-0 left-0 right-0 p-4 transform translate-y-1 group-hover:translate-y-0 transition-transform duration-300">
      <slot name="footer">
        <h3 class="font-bold text-lg text-white mb-1 line-clamp-1 group-hover:text-qingbai transition-colors">
          {{ course.title }}
        </h3>
        <div class="flex items-center justify-between text-sm text-white/80">
          <span class="flex items-center gap-1">
            <span class="w-1.5 h-1.5 rounded-full bg-zhizi/80"></span>
            {{ course.teacher }}
          </span>
          <div class="flex items-center gap-1.5 px-2 py-0.5 rounded-full bg-white/10 backdrop-blur-sm border border-white/10">
            <Users class="w-3.5 h-3.5" />
            <span>{{ course.students }}</span>
          </div>
        </div>
      </slot>
    </div>
  </div>
</template>
