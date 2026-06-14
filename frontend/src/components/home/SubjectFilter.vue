<script setup lang="ts">
import {
  BookOpen, BookText, Calculator, Languages,
  Atom, FlaskConical, Leaf, Scale, Clock, Globe
} from 'lucide-vue-next'
import type { Component } from 'vue'

interface SubjectItem {
  name: string
  icon: Component
  color: string
}

defineProps<{
  selected: string
}>()

const emit = defineEmits<{
  select: [name: string]
}>()

const subjects: SubjectItem[] = [
  { name: '全部', icon: BookOpen, color: 'from-danqing to-qinghua' },
  { name: '语文', icon: BookText, color: 'from-yanzhi to-qianhong' },
  { name: '数学', icon: Calculator, color: 'from-qinghua to-halanzi' },
  { name: '英语', icon: Languages, color: 'from-danqing to-qingbai' },
  { name: '物理', icon: Atom, color: 'from-zijinghui to-qianniuzi' },
  { name: '化学', icon: FlaskConical, color: 'from-tianlv to-qingsong' },
  { name: '生物', icon: Leaf, color: 'from-danya to-tianlv' },
  { name: '政治', icon: Scale, color: 'from-yanzhihong to-yanzhi' },
  { name: '历史', icon: Clock, color: 'from-tanxiang to-zhizi' },
  { name: '地理', icon: Globe, color: 'from-qinghua to-danqing' },
]
</script>

<template>
  <div role="group" aria-label="学科筛选" class="flex justify-center gap-3 mb-12 flex-wrap">
    <button
      v-for="subject in subjects"
      :key="subject.name"
      @click="emit('select', subject.name)"
      :data-testid="`subject-${subject.name}`"
      :class="[
        'flex items-center gap-2 px-5 py-2.5 rounded-full font-medium transition-[transform,box-shadow,color,border-color,background-color] duration-300 border',
        selected === subject.name
          ? `bg-gradient-to-r ${subject.color} border-transparent text-white shadow-lg shadow-danqing/20 scale-105`
          : 'bg-white border-slate-100 text-shuimo hover:border-danqing/30 hover:text-danqing hover:shadow-md'
      ]"
      :aria-label="`筛选学科：${subject.name}`"
    >
      <component :is="subject.icon" class="w-4 h-4" aria-hidden="true" />
      {{ subject.name }}
    </button>
  </div>
</template>
