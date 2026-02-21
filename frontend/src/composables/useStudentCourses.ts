import { ref } from 'vue'
import { enrollmentAPI, courseAPI, chapterAPI, progressAPI } from '../services/api'
import { formatDateCN } from '../utils/datetime'

interface CourseChapter {
    id: number
    title: string
    completed: boolean
}

interface EnrolledCourse {
    id: number
    title: string
    teacher: string
    progress: number
    totalChapters: number
    completedChapters: number
    lastStudy: string
    chapters: CourseChapter[]
    subject: string
    color: string
    coverImage: string
    rating: number
    students: number
    lastChapterId: number | null
    lastChapterTitle: string | null
    lastPosition: number
    hasNewChapters: boolean
}

interface RecentCourse {
    id: number
    name: string
    progress: number
    lastChapter: string
    lastStudy: string
    lastChapterId: number | null
    lastPosition: number
}

interface AvailableCourse {
    id: number
    title: string
    teacher: string
    subject: string
    color: string
    coverImage: string
    rating: number
    students: number
}

interface TimelineEntry {
    title: string
    time: string
    action: string
    courseId: number
    chapterId: number
    chapterTitle: string
}

interface ProgressRecord {
    chapterId: number
    isCompleted?: number
    lastPosition?: number
    lastUpdateTime?: string
}

interface EnrollmentRecord {
    courseId: number
    progress?: number
    lastStudyAt?: string
}

interface ChapterRecord {
    id: number | string
    title?: string
}

// 辅助方法
const getSubjectColor = (subject: string | null | undefined): string => {
    const map: Record<string, string> = {
        '语文': 'from-yanzhi to-zhizi',
        '数学': 'from-qinghua to-halanzi',
        '英语': 'from-zijinghui to-qianniuzi',
        '物理': 'from-tianlv to-qingsong',
        '化学': 'from-zhizi to-tianlv',
        '生物': 'from-qingsong to-songshi',
        '历史': 'from-yanzhi to-mudan',
        '地理': 'from-qiuxiang to-ouhe',
        '政治': 'from-red-500 to-orange-500',
    }
    return map[subject || ''] || 'from-qinghua to-halanzi'
}

const formatTime = (dateStr: string | null | undefined): string => {
    return formatDateCN(dateStr, '暂无')
}

export function useStudentCourses() {
    const enrolledCourses = ref<EnrolledCourse[]>([])
    const recentCourses = ref<RecentCourse[]>([])
    const availableCourses = ref<AvailableCourse[]>([]) // New
    const timeline = ref<TimelineEntry[]>([])
    const loading = ref(false)

    const loadEnrolledCourses = async (studentId: number | null | undefined): Promise<void> => {
        if (!studentId) return
        loading.value = true
        try {
            const res = await enrollmentAPI.getStudentEnrollments(studentId)
            const enrollmentList: EnrollmentRecord[] = Array.isArray(res.data) ? res.data : []
            if (enrollmentList.length > 0) {
                const coursesData = await Promise.all(
                    enrollmentList.map(async (enrollment): Promise<EnrolledCourse | null> => {
                        try {
                            const courseRes = await courseAPI.getById(enrollment.courseId)
                            if (!courseRes.data) return null
                            const courseData = courseRes.data as Record<string, any>

                            // 检查新章节
                            let hasNewChapters = false
                            // let newChaptersCount = 0 // Not using currently
                            try {
                                const newChapterRes = await enrollmentAPI.checkNewChapters(enrollment.courseId, studentId)
                                if (newChapterRes.data) {
                                    const chapterData = newChapterRes.data as Record<string, any>
                                    hasNewChapters = Boolean(chapterData.hasNewChapters)
                                    // newChaptersCount = newChapterRes.data.newChaptersCount || 0
                                }
                            } catch (error) {
                                console.error(`检查新章节失败(courseId=${enrollment.courseId}):`, error)
                            }

                            return {
                                id: enrollment.courseId,
                                title: courseData.title || '未知课程',
                                teacher: courseData.teacherName || '未知教师',
                                progress: Number(enrollment.progress || 0),
                                totalChapters: 0,
                                completedChapters: 0,
                                lastStudy: enrollment.lastStudyAt ? formatTime(enrollment.lastStudyAt) : '暂无记录',
                                chapters: [],
                                subject: String(courseData.subject || ''),
                                color: getSubjectColor(courseData.subject),
                                coverImage: String(courseData.coverImage || courseData.cover || ''),
                                rating: Number(courseData.rating || 4.5),
                                students: Number(courseData.studentCount || 0),
                                lastChapterId: null,
                                lastChapterTitle: null,
                                lastPosition: 0,
                                hasNewChapters,
                                // newChaptersCount
                            }
                        } catch (err) {
                            console.error(`加载课程详情失败(courseId=${enrollment.courseId}):`, err)
                            return null
                        }
                    })
                )

                // 过滤 Promise 结果中的 null，得到可用课程列表
                enrolledCourses.value = coursesData.filter((course): course is EnrolledCourse => course !== null)

                // 加载统计所需的章节详情（并行化，减少等待时间）
                await Promise.all(
                    enrolledCourses.value.map(async (course) => {
                        try {
                            const [chaptersRes, progressRes] = await Promise.all([
                                chapterAPI.getByCourse(course.id),
                                progressAPI.getCourseProgress(course.id, studentId)
                            ])

                            const chapterList: ChapterRecord[] = Array.isArray(chaptersRes.data) ? chaptersRes.data : []
                            const progressList: ProgressRecord[] = Array.isArray(progressRes.data) ? progressRes.data : []
                            const progressMap: Record<number, ProgressRecord> = {}
                            let lastStudiedChapter: ProgressRecord | null = null
                            let lastStudyTime: string | null = null

                            for (const p of progressList) {
                                const chapterId = Number(p.chapterId)
                                if (Number.isNaN(chapterId)) continue
                                progressMap[chapterId] = { ...p, chapterId }
                                // 找到最近学习的章节
                                if (p.lastUpdateTime && (!lastStudyTime || new Date(p.lastUpdateTime) > new Date(lastStudyTime))) {
                                    lastStudyTime = p.lastUpdateTime
                                    lastStudiedChapter = { ...p, chapterId }
                                }
                            }

                            course.chapters = chapterList.map((ch) => {
                                const chapterId = Number(ch.id)
                                return {
                                    id: Number.isNaN(chapterId) ? 0 : chapterId,
                                    title: ch.title || '未命名章节',
                                    completed: progressMap[chapterId]?.isCompleted === 1
                                }
                            })
                            course.totalChapters = course.chapters.length
                            course.completedChapters = course.chapters.filter((c) => c.completed).length

                            // 设置上次学习位置
                            if (lastStudiedChapter) {
                                course.lastChapterId = lastStudiedChapter.chapterId
                                course.lastPosition = Number(lastStudiedChapter.lastPosition || 0)
                                const chapterInfo = course.chapters.find((ch) => ch.id === lastStudiedChapter.chapterId)
                                course.lastChapterTitle = chapterInfo?.title || '未知章节'
                            }
                        } catch (err) {
                            console.error(`加载课程章节进度失败(courseId=${course.id}):`, err)
                        }
                    })
                )

                recentCourses.value = enrolledCourses.value.slice(0, 3).map((c): RecentCourse => {
                    const lastChapter = c.chapters.length > 0 ? c.chapters[c.chapters.length - 1] : undefined
                    return {
                        id: c.id,
                        name: c.title,
                        progress: c.progress,
                        lastChapter: c.lastChapterTitle || lastChapter?.title || '暂无章节',
                        lastStudy: c.lastStudy,
                        lastChapterId: c.lastChapterId,
                        lastPosition: c.lastPosition
                    }
                })

                // 由已完成章节生成时间线
                const timelineEntries: TimelineEntry[] = []
                for (const course of enrolledCourses.value) {
                    course.chapters.filter((ch) => ch.completed).forEach((ch) => {
                        timelineEntries.push({
                            title: course.title,
                            time: course.lastStudy || '本周',
                            action: `完成章节: ${ch.title}`,
                            courseId: course.id,
                            chapterId: ch.id,
                            chapterTitle: ch.title
                        })
                    })
                }
                timeline.value = timelineEntries.slice(0, 5)

            } else {
                enrolledCourses.value = []
                recentCourses.value = []
                timeline.value = []
            }
        } catch (e) {
            console.error('List enrollments failed', e)
        } finally {
            loading.value = false
        }
    }

    const loadAvailableCourses = async (): Promise<void> => {
        try {
            const res = await courseAPI.getAll()
            if (res.data) {
                // Filter out already enrolled
                const enrolledIds = new Set<number>(enrolledCourses.value.map((c) => c.id))
                const courseList = Array.isArray(res.data) ? res.data : []
                availableCourses.value = courseList
                    .filter((c: any) => !enrolledIds.has(Number(c.id)))
                    .map((c: any): AvailableCourse => ({
                        id: Number(c.id),
                        title: c.title || '未知课程',
                        teacher: c.teacherName || '未知教师',
                        subject: c.subject || '',
                        color: getSubjectColor(c.subject),
                        coverImage: c.coverImage || c.cover || '',
                        rating: Number(c.rating || 4.5),
                        students: Number(c.studentCount || 0)
                    }))
            }
        } catch (e) {
            console.error('Load available courses failed', e)
        }
    }

    const enrollCourse = async (courseId: number, studentId: number): Promise<void> => {
        await enrollmentAPI.enroll(courseId, studentId)
        // Reload to update lists
        await loadEnrolledCourses(studentId)
        await loadAvailableCourses()
    }

    const dropCourse = async (courseId: number, studentId: number): Promise<void> => {
        await enrollmentAPI.drop(courseId, studentId)
        // Reload to update lists
        await loadEnrolledCourses(studentId)
        await loadAvailableCourses()
    }

    return {
        enrolledCourses,
        recentCourses,
        availableCourses,
        timeline,
        loading,
        loadEnrolledCourses,
        loadAvailableCourses,
        enrollCourse,
        dropCourse
    }
}
