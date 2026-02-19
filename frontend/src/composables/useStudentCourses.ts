import { ref, computed } from 'vue'
import { enrollmentAPI, courseAPI, chapterAPI, progressAPI } from '../services/api'
import { formatDateCN } from '../utils/datetime'

// 辅助方法
const getSubjectColor = (subject) => {
    const map = {
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
    return map[subject] || 'from-qinghua to-halanzi'
}

const formatTime = (dateStr) => {
    return formatDateCN(dateStr, '暂无')
}

export function useStudentCourses() {
    const enrolledCourses = ref([])
    const recentCourses = ref([])
    const availableCourses = ref([]) // New
    const timeline = ref([])
    const loading = ref(false)

    const loadEnrolledCourses = async (studentId) => {
        if (!studentId) return
        loading.value = true
        try {
            const res = await enrollmentAPI.getStudentEnrollments(studentId)
            if (res.data && res.data.length > 0) {
                const coursesData = await Promise.all(
                    res.data.map(async (enrollment) => {
                        try {
                            const courseRes = await courseAPI.getById(enrollment.courseId)
                            if (!courseRes.data) return null

                            // 检查新章节
                            let hasNewChapters = false
                            // let newChaptersCount = 0 // Not using currently
                            try {
                                const newChapterRes = await enrollmentAPI.checkNewChapters(enrollment.courseId, studentId)
                                if (newChapterRes.data) {
                                    hasNewChapters = newChapterRes.data.hasNewChapters || false
                                    // newChaptersCount = newChapterRes.data.newChaptersCount || 0
                                }
                            } catch (err) {
                                // console.warn(`检查新章节失败(courseId=${enrollment.courseId}):`, err)
                            }

                            return {
                                id: enrollment.courseId,
                                title: courseRes.data.title || '未知课程',
                                teacher: courseRes.data.teacherName || '未知教师',
                                progress: enrollment.progress || 0,
                                totalChapters: 0,
                                completedChapters: 0,
                                lastStudy: enrollment.lastStudyAt ? formatTime(enrollment.lastStudyAt) : '暂无记录',
                                chapters: [],
                                subject: courseRes.data.subject,
                                color: getSubjectColor(courseRes.data.subject),
                                coverImage: courseRes.data.coverImage || courseRes.data.cover,
                                rating: courseRes.data.rating || 4.5,
                                students: courseRes.data.studentCount || 0,
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

                enrolledCourses.value = coursesData.filter(Boolean)

                // 加载统计所需的章节详情（并行化，减少等待时间）
                await Promise.all(
                    enrolledCourses.value.map(async (course) => {
                        try {
                            const [chaptersRes, progressRes] = await Promise.all([
                                chapterAPI.getByCourse(course.id),
                                progressAPI.getCourseProgress(course.id, studentId)
                            ])

                            if (chaptersRes.data) {
                                const progressMap = {}
                                let lastStudiedChapter = null
                                let lastStudyTime = null

                                if (progressRes.data) {
                                    progressRes.data.forEach(p => {
                                        progressMap[p.chapterId] = p
                                        // 找到最近学习的章节
                                        if (p.lastUpdateTime && (!lastStudyTime || new Date(p.lastUpdateTime) > new Date(lastStudyTime))) {
                                            lastStudyTime = p.lastUpdateTime
                                            lastStudiedChapter = p
                                        }
                                    })
                                }

                                course.chapters = chaptersRes.data.map(ch => ({
                                    id: ch.id,
                                    title: ch.title,
                                    completed: progressMap[ch.id]?.isCompleted === 1
                                }))
                                course.totalChapters = course.chapters.length
                                course.completedChapters = course.chapters.filter(c => c.completed).length

                                // 设置上次学习位置
                                if (lastStudiedChapter) {
                                    course.lastChapterId = lastStudiedChapter.chapterId
                                    course.lastPosition = lastStudiedChapter.lastPosition || 0
                                    const chapterInfo = course.chapters.find(ch => ch.id === lastStudiedChapter.chapterId)
                                    course.lastChapterTitle = chapterInfo?.title || '未知章节'
                                }
                            }
                        } catch (err) {
                            console.error(`加载课程章节进度失败(courseId=${course.id}):`, err)
                        }
                    })
                )

                recentCourses.value = enrolledCourses.value.slice(0, 3).map(c => ({
                    id: c.id, name: c.title, progress: c.progress,
                    lastChapter: c.lastChapterTitle || (c.chapters && c.chapters.length > 0 ? c.chapters[c.chapters.length - 1].title : '暂无章节'),
                    lastStudy: c.lastStudy,
                    lastChapterId: c.lastChapterId,
                    lastPosition: c.lastPosition
                }))

                // 由已完成章节生成时间线
                const timelineEntries = []
                for (const course of enrolledCourses.value) {
                    if (course.chapters) {
                        course.chapters.filter(ch => ch.completed).forEach(ch => {
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

    const loadAvailableCourses = async () => {
        try {
            const res = await courseAPI.getAll()
            if (res.data) {
                // Filter out already enrolled
                const enrolledIds = new Set(enrolledCourses.value.map(c => c.id))
                availableCourses.value = res.data
                    .filter(c => !enrolledIds.has(c.id))
                    .map(c => ({
                        id: c.id,
                        title: c.title,
                        teacher: c.teacherName,
                        subject: c.subject,
                        color: getSubjectColor(c.subject),
                        coverImage: c.coverImage || c.cover,
                        rating: c.rating || 4.5,
                        students: c.studentCount || 0
                    }))
            }
        } catch (e) {
            console.error('Load available courses failed', e)
        }
    }

    const enrollCourse = async (courseId, studentId) => {
        await enrollmentAPI.enroll(courseId, studentId)
        // Reload to update lists
        await loadEnrolledCourses(studentId)
        await loadAvailableCourses()
    }

    const dropCourse = async (courseId, studentId) => {
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
