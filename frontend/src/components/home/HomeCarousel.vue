<script setup lang="ts">
import { ref, onMounted, onUnmounted } from 'vue'
import { useRouter } from 'vue-router'
import BaseButton from '../ui/BaseButton.vue'
import { ChevronRight, ChevronLeft, Play, Star } from 'lucide-vue-next'

interface CarouselCourse {
  id: number
  title: string
  teacher: string
  subject: string
  rating: number
  students: number
  color: string
  btnStyle: string
}

const props = defineProps<{
  courses: CarouselCourse[]
}>()

const router = useRouter()
const currentSlide = ref(0)
const carouselInterval = ref<ReturnType<typeof setInterval> | null>(null)
const isHovering = ref(false)

const startCarousel = () => {
  if (carouselInterval.value) clearInterval(carouselInterval.value)
  const isDarkSmallViewport = typeof window !== 'undefined'
    && window.matchMedia('(max-width: 1024px)').matches
    && document.documentElement.classList.contains('dark')
  const intervalMs = isDarkSmallViewport ? 5600 : 4000
  carouselInterval.value = setInterval(() => {
    if (!isHovering.value && props.courses.length > 0) {
      currentSlide.value = (currentSlide.value + 1) % props.courses.length
    }
  }, intervalMs)
}

const stopCarousel = () => {
  if (carouselInterval.value) {
    clearInterval(carouselInterval.value)
    carouselInterval.value = null
  }
}

const goToSlide = (index: number) => {
  currentSlide.value = index
  startCarousel()
}

const prevSlide = () => {
  currentSlide.value = currentSlide.value === 0
    ? props.courses.length - 1
    : currentSlide.value - 1
  startCarousel()
}

const nextSlide = () => {
  currentSlide.value = (currentSlide.value + 1) % props.courses.length
  startCarousel()
}

onMounted(() => startCarousel())
onUnmounted(() => stopCarousel())
</script>

<template>
  <section class="relative py-6 overflow-hidden" aria-labelledby="home-featured-heading">
    <h1 id="home-featured-heading" class="sr-only">智慧课堂精选课程</h1>
    <div class="max-w-6xl mx-auto px-6">
      <div
        class="relative rounded-2xl overflow-hidden group shadow-2xl"
        @mouseenter="isHovering = true"
        @mouseleave="isHovering = false"
      >
        <div class="relative h-[360px] md:h-[420px]">
          <transition-group name="carousel-fade">
            <div
              v-for="(course, index) in courses"
              :key="course.id"
              v-show="currentSlide === index"
              class="absolute inset-0 cursor-pointer overflow-hidden"
              @click="router.push(`/course/${course.id}`)"
            >
              <div
                class="home-hero-layer absolute -inset-4 transition-transform duration-500 ease-out transform-gpu will-change-transform"
                :class="[isHovering ? 'home-hero-scale-hover' : 'scale-100']"
              >
                <div :class="['absolute inset-0 bg-gradient-to-br', course.color]" />
                <div class="hero-texture-layer absolute inset-0 opacity-20 bg-[url('https://www.transparenttextures.com/patterns/cubes.png')]" />
                <div class="absolute inset-0 bg-gradient-to-t from-black/80 via-black/20 to-transparent" />
              </div>

              <div class="absolute inset-x-0 bottom-0 top-0 flex items-end p-8 md:p-12 pb-16 pointer-events-none">
                <div class="relative z-10 w-full md:w-2/3 lg:w-1/2 space-y-4 animate-slide-up pointer-events-auto">
                  <div class="flex items-center gap-3">
                    <span class="px-3 py-1 rounded-full bg-white/20 backdrop-blur-md border border-white/20 text-white text-sm font-medium">
                      精选推荐
                    </span>
                    <div class="flex items-center gap-1 text-white/95" aria-label="课程评分">
                      <Star class="w-4 h-4 fill-current" aria-hidden="true" />
                      <span class="text-white font-bold">{{ course.rating }}</span>
                    </div>
                  </div>

                  <h2 class="text-3xl md:text-5xl font-bold text-white leading-tight tracking-tight shadow-black/10 drop-shadow-lg">
                    {{ course.title }}
                  </h2>

                  <p class="text-lg text-white/80 line-clamp-2 leading-relaxed">
                    由 {{ course.teacher }} 老师倾力打造，已有 {{ course.students }} 名同学加入学习。
                  </p>

                  <div class="pt-4 flex items-center gap-4">
                    <BaseButton
                      variant="custom"
                      size="lg"
                      :class="['!rounded-full !px-8 border-0', course.btnStyle]"
                    >
                      <Play class="w-5 h-5 mr-2 fill-current" aria-hidden="true" />
                      立即开始
                    </BaseButton>
                  </div>
                </div>
              </div>
            </div>
          </transition-group>
        </div>

        <div class="absolute right-8 bottom-8 flex items-center gap-4 z-20">
          <button
            @click.stop="prevSlide"
            data-testid="carousel-prev"
            class="w-12 h-12 rounded-full bg-white/10 backdrop-blur-md border border-white/20 flex items-center justify-center text-white hover:bg-white/20 hover:scale-105 transition-[background-color,transform,color] duration-300 text-white/50 hover:text-white"
            aria-label="上一张课程"
          >
            <ChevronLeft class="w-6 h-6" aria-hidden="true" />
          </button>
          <button
            @click.stop="nextSlide"
            data-testid="carousel-next"
            class="w-12 h-12 rounded-full bg-white/10 backdrop-blur-md border border-white/20 flex items-center justify-center text-white hover:bg-white/20 hover:scale-105 transition-[background-color,transform,color] duration-300 text-white/50 hover:text-white"
            aria-label="下一张课程"
          >
            <ChevronRight class="w-6 h-6" aria-hidden="true" />
          </button>
        </div>

        <div class="absolute top-8 right-8 flex gap-2 z-20">
          <button
            v-for="(course, index) in courses"
            :key="'dot-' + course.id"
            @mouseenter="goToSlide(index)"
            :class="[
              'w-2.5 h-1 rounded-full transition-[width,background-color] duration-300',
              currentSlide === index ? 'bg-white w-8' : 'bg-white/30 hover:bg-white/50'
            ]"
            :aria-label="`切换到第 ${index + 1} 张课程`"
          />
        </div>
      </div>
    </div>
  </section>
</template>

<style scoped>
.carousel-fade-enter-active,
.carousel-fade-leave-active {
  transition: opacity var(--motion-duration-medium) var(--motion-ease-standard);
}
.carousel-fade-enter-from,
.carousel-fade-leave-to {
  opacity: 0;
}
.carousel-fade-enter-to,
.carousel-fade-leave-from {
  opacity: 1;
}

.home-hero-scale-hover {
  transform: scale(1.05);
}

:global(html.dark) .home-hero-scale-hover,
:global(.dark) .home-hero-scale-hover {
  transform: scale(1);
}

:global(html.dark) .hero-texture-layer,
:global(.dark) .hero-texture-layer {
  display: none;
}

@media (max-width: 1024px) {
  :global(html.dark) .home-hero-scale-hover,
  :global(.dark) .home-hero-scale-hover {
    transform: scale(1);
  }
}
</style>
