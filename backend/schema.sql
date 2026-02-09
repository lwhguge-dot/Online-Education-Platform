-- =====================================================
-- 智慧课堂在线教育平台 - 数据库表结构
-- 数据库: PostgreSQL 15+
-- 字符集: UTF-8
-- 表数量: 29张
-- 默认数据库账号密码: postgres / 123456
-- 最后更新时间: 2026-02-08
-- =====================================================
-- 创建数据库（如果需要手动创建，请在psql中执行）
-- CREATE DATABASE edu_platform WITH ENCODING 'UTF8' LC_COLLATE='zh_CN.UTF-8' LC_CTYPE='zh_CN.UTF-8' TEMPLATE=template0;
-- \c edu_platform;
-- =====================================================
-- 1. 用户相关表
-- =====================================================
-- 用户表
CREATE TABLE IF NOT EXISTS users (
    id BIGSERIAL PRIMARY KEY,
    email VARCHAR(255) NOT NULL UNIQUE,
    username VARCHAR(50) NOT NULL UNIQUE,
    password VARCHAR(255) NOT NULL,
    name VARCHAR(100) NOT NULL,
    role VARCHAR(20) NOT NULL DEFAULT 'student' CHECK (role IN ('student', 'teacher', 'admin')),
    avatar VARCHAR(500) DEFAULT NULL,
    phone VARCHAR(20) DEFAULT NULL,
    birthday DATE DEFAULT NULL,
    gender VARCHAR(10) DEFAULT NULL,
    status SMALLINT DEFAULT 1,
    last_login_at TIMESTAMP DEFAULT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
COMMENT ON TABLE users IS '用户表';
COMMENT ON COLUMN users.id IS '用户ID';
COMMENT ON COLUMN users.email IS '邮箱（登录账号）';
COMMENT ON COLUMN users.username IS '用户名（显示名称，可修改）';
COMMENT ON COLUMN users.password IS '密码（BCrypt加密）';
COMMENT ON COLUMN users.name IS '真实姓名（注册后不可修改）';
COMMENT ON COLUMN users.role IS '用户角色';
COMMENT ON COLUMN users.avatar IS '头像URL';
COMMENT ON COLUMN users.phone IS '手机号';
COMMENT ON COLUMN users.birthday IS '出生年月日';
COMMENT ON COLUMN users.gender IS '性别：male/female';
COMMENT ON COLUMN users.status IS '状态：1启用 0禁用';
COMMENT ON COLUMN users.last_login_at IS '最后登录时间';
COMMENT ON COLUMN users.created_at IS '创建时间';
COMMENT ON COLUMN users.updated_at IS '更新时间';

CREATE INDEX idx_users_email ON users(email);
CREATE INDEX idx_users_username ON users(username);
-- 用户会话表
CREATE TABLE IF NOT EXISTS user_session (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    jti VARCHAR(64) NOT NULL UNIQUE,
    status VARCHAR(20) DEFAULT 'ONLINE' CHECK (status IN ('ONLINE', 'OFFLINE')),
    login_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    last_active_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    logout_time TIMESTAMP DEFAULT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
COMMENT ON TABLE user_session IS '用户会话表';
COMMENT ON COLUMN user_session.id IS '会话ID';
COMMENT ON COLUMN user_session.user_id IS '用户ID';
COMMENT ON COLUMN user_session.jti IS 'JWT唯一标识';
COMMENT ON COLUMN user_session.status IS '会话状态';
COMMENT ON COLUMN user_session.login_time IS '登录时间';
COMMENT ON COLUMN user_session.last_active_time IS '最后活跃时间';
COMMENT ON COLUMN user_session.logout_time IS '登出时间';

CREATE INDEX idx_user_session_jti ON user_session(jti);
CREATE INDEX idx_user_session_user_status ON user_session(user_id, status);
-- 学生扩展信息表
CREATE TABLE IF NOT EXISTS student_profiles (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL UNIQUE,
    grade VARCHAR(50) DEFAULT NULL,
    school VARCHAR(200) DEFAULT NULL,
    study_days INT DEFAULT 0,
    total_study_time INT DEFAULT 0,
    notification_settings JSONB DEFAULT NULL,
    study_goal JSONB DEFAULT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
COMMENT ON TABLE student_profiles IS '学生扩展信息表';
COMMENT ON COLUMN student_profiles.user_id IS '用户ID';
COMMENT ON COLUMN student_profiles.grade IS '年级';
COMMENT ON COLUMN student_profiles.school IS '学校名称';
COMMENT ON COLUMN student_profiles.study_days IS '累计学习天数';
COMMENT ON COLUMN student_profiles.total_study_time IS '累计学习时长（分钟）';
COMMENT ON COLUMN student_profiles.notification_settings IS '通知设置';
COMMENT ON COLUMN student_profiles.study_goal IS '学习目标';

CREATE INDEX idx_student_profiles_user_id ON student_profiles(user_id);
-- 教师扩展信息表
CREATE TABLE IF NOT EXISTS teacher_profiles (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL UNIQUE,
    title VARCHAR(100) DEFAULT NULL,
    department VARCHAR(200) DEFAULT NULL,
    subjects VARCHAR(500) DEFAULT NULL,
    introduction TEXT DEFAULT NULL,
    total_students INT DEFAULT 0,
    total_courses INT DEFAULT 0,
    teaching_subjects JSONB DEFAULT NULL,
    default_grading_criteria JSONB DEFAULT NULL,
    dashboard_layout JSONB DEFAULT NULL,
    notification_settings JSONB DEFAULT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
COMMENT ON TABLE teacher_profiles IS '教师扩展信息表';
COMMENT ON COLUMN teacher_profiles.user_id IS '用户ID';
COMMENT ON COLUMN teacher_profiles.title IS '职称';
COMMENT ON COLUMN teacher_profiles.department IS '所属院系/学校';
COMMENT ON COLUMN teacher_profiles.subjects IS '擅长科目（JSON数组）';
COMMENT ON COLUMN teacher_profiles.introduction IS '教师介绍';
COMMENT ON COLUMN teacher_profiles.total_students IS '累计学生数';
COMMENT ON COLUMN teacher_profiles.total_courses IS '发布课程数';
COMMENT ON COLUMN teacher_profiles.teaching_subjects IS '教学科目设置';
COMMENT ON COLUMN teacher_profiles.default_grading_criteria IS '默认评分标准配置';
COMMENT ON COLUMN teacher_profiles.dashboard_layout IS '仪表盘布局自定义';
COMMENT ON COLUMN teacher_profiles.notification_settings IS '细粒度通知设置';

CREATE INDEX idx_teacher_profiles_user_id ON teacher_profiles(user_id);
-- =====================================================
-- 2. 课程相关表
-- =====================================================
-- 学科分类表
CREATE TABLE IF NOT EXISTS subjects (
    id SERIAL PRIMARY KEY,
    name VARCHAR(50) NOT NULL,
    code VARCHAR(20) NOT NULL UNIQUE,
    category VARCHAR(20) NOT NULL CHECK (category IN ('main', 'science', 'liberal')),
    icon VARCHAR(100) DEFAULT NULL,
    color VARCHAR(50) DEFAULT NULL,
    sort_order INT DEFAULT 0,
    status SMALLINT DEFAULT 1,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
COMMENT ON TABLE subjects IS '学科分类表';
COMMENT ON COLUMN subjects.name IS '学科名称';
COMMENT ON COLUMN subjects.code IS '学科代码';
COMMENT ON COLUMN subjects.category IS '分类：主科/理科/文科';
COMMENT ON COLUMN subjects.icon IS '图标名称';
COMMENT ON COLUMN subjects.color IS '主题色';
COMMENT ON COLUMN subjects.sort_order IS '排序';
COMMENT ON COLUMN subjects.status IS '状态';

CREATE INDEX idx_subjects_code ON subjects(code);
-- 学科初始化数据
INSERT INTO subjects (name, code, category, icon, color, sort_order)
VALUES ('语文', 'chinese', 'main', 'book', '#e74c3c', 1),
    ('数学', 'math', 'main', 'calculator', '#3498db', 2),
    ('英语', 'english', 'main', 'globe', '#2ecc71', 3),
    ('物理', 'physics', 'science', 'atom', '#9b59b6', 4),
    ('化学', 'chemistry', 'science', 'flask', '#f39c12', 5),
    ('生物', 'biology', 'science', 'leaf', '#1abc9c', 6),
    ('历史', 'history', 'liberal', 'landmark', '#e67e22', 7),
    ('地理', 'geography', 'liberal', 'map', '#16a085', 8),
    ('政治', 'politics', 'liberal', 'balance-scale', '#c0392b', 9)
ON CONFLICT (code) DO NOTHING;
-- 课程表
CREATE TABLE IF NOT EXISTS courses (
    id BIGSERIAL PRIMARY KEY,
    title VARCHAR(200) NOT NULL,
    description TEXT DEFAULT NULL,
    subject VARCHAR(50) NOT NULL,
    cover_image VARCHAR(500) DEFAULT NULL,
    teacher_id BIGINT DEFAULT NULL,
    teacher_name VARCHAR(100) DEFAULT NULL,
    rating DOUBLE PRECISION DEFAULT 0,
    student_count INT DEFAULT 0,
    status VARCHAR(20) DEFAULT 'DRAFT',
    submit_time TIMESTAMP DEFAULT NULL,
    audit_by BIGINT DEFAULT NULL,
    audit_time TIMESTAMP DEFAULT NULL,
    audit_remark VARCHAR(500) DEFAULT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
COMMENT ON TABLE courses IS '课程表';
COMMENT ON COLUMN courses.id IS '课程ID';
COMMENT ON COLUMN courses.title IS '课程标题';
COMMENT ON COLUMN courses.description IS '课程描述';
COMMENT ON COLUMN courses.subject IS '学科名称';
COMMENT ON COLUMN courses.cover_image IS '封面图片URL';
COMMENT ON COLUMN courses.teacher_id IS '教师ID';
COMMENT ON COLUMN courses.teacher_name IS '教师名称';
COMMENT ON COLUMN courses.rating IS '评分';
COMMENT ON COLUMN courses.student_count IS '选课人数';
COMMENT ON COLUMN courses.status IS '课程状态：DRAFT/REVIEWING/PUBLISHED/REJECTED';
COMMENT ON COLUMN courses.submit_time IS '提交审核时间';
COMMENT ON COLUMN courses.audit_by IS '审核人ID';
COMMENT ON COLUMN courses.audit_time IS '审核时间';
COMMENT ON COLUMN courses.audit_remark IS '审核备注';

CREATE INDEX idx_courses_teacher ON courses(teacher_id);
CREATE INDEX idx_courses_subject ON courses(subject);
CREATE INDEX idx_courses_status ON courses(status);
-- 章节表
CREATE TABLE IF NOT EXISTS chapters (
    id BIGSERIAL PRIMARY KEY,
    course_id BIGINT NOT NULL,
    title VARCHAR(200) NOT NULL,
    description TEXT DEFAULT NULL,
    sort_order INT DEFAULT 0,
    video_url VARCHAR(500) DEFAULT NULL,
    video_duration INT DEFAULT 0,
    unlock_video_rate DECIMAL(3, 2) DEFAULT 0.90,
    unlock_quiz_score INT DEFAULT 60,
    status SMALLINT DEFAULT 1,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
COMMENT ON TABLE chapters IS '章节表';
COMMENT ON COLUMN chapters.id IS '章节ID';
COMMENT ON COLUMN chapters.course_id IS '课程ID';
COMMENT ON COLUMN chapters.title IS '章节标题';
COMMENT ON COLUMN chapters.description IS '章节描述';
COMMENT ON COLUMN chapters.sort_order IS '排序';
COMMENT ON COLUMN chapters.video_url IS '视频URL';
COMMENT ON COLUMN chapters.video_duration IS '视频时长（秒）';
COMMENT ON COLUMN chapters.unlock_video_rate IS '解锁下一章需观看视频比率';
COMMENT ON COLUMN chapters.unlock_quiz_score IS '解锁下一章需测验分数';
COMMENT ON COLUMN chapters.status IS '状态';

CREATE INDEX idx_chapters_course ON chapters(course_id);
-- 章节测验表
CREATE TABLE IF NOT EXISTS chapter_quizzes (
    id BIGSERIAL PRIMARY KEY,
    chapter_id BIGINT NOT NULL,
    question TEXT NOT NULL,
    question_type VARCHAR(20) NOT NULL CHECK (question_type IN ('single', 'multiple', 'fill')),
    options JSONB DEFAULT NULL,
    correct_answer VARCHAR(500) NOT NULL,
    score INT DEFAULT 10,
    sort_order INT DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_quiz_chapter FOREIGN KEY (chapter_id) REFERENCES chapters(id) ON DELETE CASCADE
);
COMMENT ON TABLE chapter_quizzes IS '章节测验表';
COMMENT ON COLUMN chapter_quizzes.chapter_id IS '章节ID';
COMMENT ON COLUMN chapter_quizzes.question IS '题目内容';
COMMENT ON COLUMN chapter_quizzes.question_type IS '题型：单选/多选/填空';
COMMENT ON COLUMN chapter_quizzes.options IS '选项（JSON数组）';
COMMENT ON COLUMN chapter_quizzes.correct_answer IS '正确答案';
COMMENT ON COLUMN chapter_quizzes.score IS '分值';
COMMENT ON COLUMN chapter_quizzes.sort_order IS '排序';

CREATE INDEX idx_chapter_quizzes_chapter ON chapter_quizzes(chapter_id);
-- 学生选课表
CREATE TABLE IF NOT EXISTS enrollments (
    id BIGSERIAL PRIMARY KEY,
    student_id BIGINT NOT NULL,
    course_id BIGINT NOT NULL,
    enrolled_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    last_study_at TIMESTAMP DEFAULT NULL,
    progress INT DEFAULT 0,
    status VARCHAR(20) DEFAULT 'active',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_student_course UNIQUE (student_id, course_id)
);
COMMENT ON TABLE enrollments IS '学生选课表';
COMMENT ON COLUMN enrollments.student_id IS '学生ID';
COMMENT ON COLUMN enrollments.course_id IS '课程ID';
COMMENT ON COLUMN enrollments.enrolled_at IS '选课时间';
COMMENT ON COLUMN enrollments.last_study_at IS '最后学习时间';
COMMENT ON COLUMN enrollments.progress IS '学习进度百分比';
COMMENT ON COLUMN enrollments.status IS '状态：active/completed/dropped';

CREATE INDEX idx_enrollments_student ON enrollments(student_id);
CREATE INDEX idx_enrollments_course ON enrollments(course_id);
-- =====================================================
-- 3. 学习进度表
-- =====================================================
-- 章节学习进度表
CREATE TABLE IF NOT EXISTS chapter_progress (
    id BIGSERIAL PRIMARY KEY,
    student_id BIGINT NOT NULL,
    chapter_id BIGINT NOT NULL,
    course_id BIGINT DEFAULT NULL,
    video_rate DECIMAL(5, 2) DEFAULT 0.00,
    video_watch_time INT DEFAULT 0,
    quiz_score INT DEFAULT NULL,
    quiz_submitted_at TIMESTAMP DEFAULT NULL,
    is_completed SMALLINT DEFAULT 0,
    completed_at TIMESTAMP DEFAULT NULL,
    last_position INT DEFAULT 0,
    last_update_time TIMESTAMP DEFAULT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_student_chapter UNIQUE (student_id, chapter_id)
);
COMMENT ON TABLE chapter_progress IS '章节学习进度表';
COMMENT ON COLUMN chapter_progress.id IS '进度记录ID';
COMMENT ON COLUMN chapter_progress.student_id IS '学生ID';
COMMENT ON COLUMN chapter_progress.chapter_id IS '章节ID';
COMMENT ON COLUMN chapter_progress.course_id IS '课程ID';
COMMENT ON COLUMN chapter_progress.video_rate IS '视频观看完成率';
COMMENT ON COLUMN chapter_progress.video_watch_time IS '已观看时长（秒）';
COMMENT ON COLUMN chapter_progress.quiz_score IS '测验得分';
COMMENT ON COLUMN chapter_progress.quiz_submitted_at IS '测验提交时间';
COMMENT ON COLUMN chapter_progress.is_completed IS '是否完成：0否 1是';
COMMENT ON COLUMN chapter_progress.completed_at IS '完成时间';
COMMENT ON COLUMN chapter_progress.last_position IS '上次播放位置（秒）';
COMMENT ON COLUMN chapter_progress.last_update_time IS '最后更新时间';

CREATE INDEX idx_chapter_progress_student ON chapter_progress(student_id);
CREATE INDEX idx_chapter_progress_chapter ON chapter_progress(chapter_id);
CREATE INDEX idx_chapter_progress_course ON chapter_progress(course_id);
CREATE INDEX idx_chapter_progress_completed ON chapter_progress(is_completed);
-- =====================================================
-- 4. 作业相关表
-- =====================================================
-- 作业表
CREATE TABLE IF NOT EXISTS homeworks (
    id BIGSERIAL PRIMARY KEY,
    course_id BIGINT DEFAULT NULL,
    chapter_id BIGINT DEFAULT NULL,
    teacher_id BIGINT DEFAULT NULL,
    title VARCHAR(255) NOT NULL,
    description TEXT DEFAULT NULL,
    homework_type VARCHAR(50) DEFAULT 'objective',
    total_score INT DEFAULT 100,
    deadline TIMESTAMP DEFAULT NULL,
    test_type VARCHAR(50) DEFAULT 'chapter',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
COMMENT ON TABLE homeworks IS '作业表';
COMMENT ON COLUMN homeworks.course_id IS '课程ID';
COMMENT ON COLUMN homeworks.chapter_id IS '章节ID';
COMMENT ON COLUMN homeworks.teacher_id IS '教师ID';
COMMENT ON COLUMN homeworks.title IS '作业标题';
COMMENT ON COLUMN homeworks.description IS '作业描述';
COMMENT ON COLUMN homeworks.homework_type IS '作业类型：objective/subjective/mixed';
COMMENT ON COLUMN homeworks.total_score IS '总分';
COMMENT ON COLUMN homeworks.deadline IS '截止时间';
COMMENT ON COLUMN homeworks.test_type IS '测试类型：chapter/final';

CREATE INDEX idx_homeworks_course ON homeworks(course_id);
CREATE INDEX idx_homeworks_chapter ON homeworks(chapter_id);
CREATE INDEX idx_homeworks_teacher ON homeworks(teacher_id);

ALTER TABLE IF EXISTS homeworks
    ADD COLUMN IF NOT EXISTS teacher_id BIGINT DEFAULT NULL;

-- 历史兼容迁移（幂等）：如旧库缺少 teacher_id 字段，则自动补齐
-- 作业题目表
CREATE TABLE IF NOT EXISTS homework_questions (
    id BIGSERIAL PRIMARY KEY,
    homework_id BIGINT NOT NULL,
    question_type VARCHAR(50) NOT NULL,
    content TEXT NOT NULL,
    options TEXT DEFAULT NULL,
    correct_answer VARCHAR(255) DEFAULT NULL,
    answer_analysis TEXT DEFAULT NULL,
    score INT DEFAULT 10,
    sort_order INT DEFAULT 1,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
COMMENT ON TABLE homework_questions IS '作业题目表';
COMMENT ON COLUMN homework_questions.homework_id IS '作业ID';
COMMENT ON COLUMN homework_questions.question_type IS '题型：single/multiple/fill/subjective';
COMMENT ON COLUMN homework_questions.content IS '题目内容';
COMMENT ON COLUMN homework_questions.options IS '选项（JSON格式）';
COMMENT ON COLUMN homework_questions.correct_answer IS '正确答案';
COMMENT ON COLUMN homework_questions.answer_analysis IS '答案解析';
COMMENT ON COLUMN homework_questions.score IS '分值';
COMMENT ON COLUMN homework_questions.sort_order IS '排序';

CREATE INDEX idx_homework_questions_homework ON homework_questions(homework_id);
-- 作业提交表
CREATE TABLE IF NOT EXISTS homework_submissions (
    id BIGSERIAL PRIMARY KEY,
    student_id BIGINT NOT NULL,
    homework_id BIGINT NOT NULL,
    submit_status VARCHAR(50) DEFAULT 'draft',
    objective_score INT DEFAULT NULL,
    subjective_score INT DEFAULT NULL,
    total_score INT DEFAULT NULL,
    submitted_at TIMESTAMP DEFAULT NULL,
    graded_at TIMESTAMP DEFAULT NULL,
    graded_by BIGINT DEFAULT NULL,
    feedback TEXT DEFAULT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_student_homework UNIQUE (student_id, homework_id)
);
COMMENT ON TABLE homework_submissions IS '作业提交表';
COMMENT ON COLUMN homework_submissions.student_id IS '学生ID';
COMMENT ON COLUMN homework_submissions.homework_id IS '作业ID';
COMMENT ON COLUMN homework_submissions.submit_status IS '提交状态：draft/submitted/graded';
COMMENT ON COLUMN homework_submissions.objective_score IS '客观题得分';
COMMENT ON COLUMN homework_submissions.subjective_score IS '主观题得分';
COMMENT ON COLUMN homework_submissions.total_score IS '总得分';
COMMENT ON COLUMN homework_submissions.submitted_at IS '提交时间';
COMMENT ON COLUMN homework_submissions.graded_at IS '批改时间';
COMMENT ON COLUMN homework_submissions.graded_by IS '批改人ID';
COMMENT ON COLUMN homework_submissions.feedback IS '总体反馈';

CREATE INDEX idx_homework_submissions_student ON homework_submissions(student_id);
CREATE INDEX idx_homework_submissions_homework ON homework_submissions(homework_id);
-- 作业答案表
CREATE TABLE IF NOT EXISTS homework_answers (
    id BIGSERIAL PRIMARY KEY,
    submission_id BIGINT NOT NULL,
    question_id BIGINT NOT NULL,
    student_answer TEXT DEFAULT NULL,
    is_correct INT DEFAULT NULL,
    score INT DEFAULT NULL,
    ai_feedback TEXT DEFAULT NULL,
    teacher_feedback TEXT DEFAULT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
COMMENT ON TABLE homework_answers IS '作业答案表';
COMMENT ON COLUMN homework_answers.submission_id IS '提交ID';
COMMENT ON COLUMN homework_answers.question_id IS '题目ID';
COMMENT ON COLUMN homework_answers.student_answer IS '学生答案';
COMMENT ON COLUMN homework_answers.is_correct IS '是否正确：1正确 0错误';
COMMENT ON COLUMN homework_answers.score IS '得分';
COMMENT ON COLUMN homework_answers.ai_feedback IS 'AI反馈';
COMMENT ON COLUMN homework_answers.teacher_feedback IS '教师反馈';

CREATE INDEX idx_homework_answers_submission ON homework_answers(submission_id);
CREATE INDEX idx_homework_answers_question ON homework_answers(question_id);
-- 作业解锁表
CREATE TABLE IF NOT EXISTS homework_unlocks (
    id BIGSERIAL PRIMARY KEY,
    student_id BIGINT NOT NULL,
    homework_id BIGINT NOT NULL,
    unlock_status INT DEFAULT 0,
    unlocked_at TIMESTAMP DEFAULT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_student_homework_unlock UNIQUE (student_id, homework_id)
);
COMMENT ON TABLE homework_unlocks IS '作业解锁表';
COMMENT ON COLUMN homework_unlocks.student_id IS '学生ID';
COMMENT ON COLUMN homework_unlocks.homework_id IS '作业ID';
COMMENT ON COLUMN homework_unlocks.unlock_status IS '解锁状态：0未解锁 1已解锁';
COMMENT ON COLUMN homework_unlocks.unlocked_at IS '解锁时间';

CREATE INDEX idx_homework_unlocks_student ON homework_unlocks(student_id);
CREATE INDEX idx_homework_unlocks_homework ON homework_unlocks(homework_id);
-- =====================================================
-- 5. 评论与互动表
-- =====================================================
-- 学生主观题作答与评论权限关联表
CREATE TABLE IF NOT EXISTS subjective_answer_permission (
    id BIGSERIAL PRIMARY KEY,
    student_id BIGINT NOT NULL,
    question_id BIGINT NOT NULL,
    answer_content TEXT DEFAULT NULL,
    answer_status SMALLINT DEFAULT 0,
    comment_visible SMALLINT DEFAULT 0,
    answered_at TIMESTAMP DEFAULT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_student_question UNIQUE (student_id, question_id),
    CONSTRAINT fk_sap_student FOREIGN KEY (student_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT fk_sap_question FOREIGN KEY (question_id) REFERENCES homework_questions(id) ON DELETE CASCADE
);
COMMENT ON TABLE subjective_answer_permission IS '学生主观题作答与评论权限关联表';
COMMENT ON COLUMN subjective_answer_permission.student_id IS '学生ID';
COMMENT ON COLUMN subjective_answer_permission.question_id IS '主观题ID';
COMMENT ON COLUMN subjective_answer_permission.answer_content IS '学生答案内容';
COMMENT ON COLUMN subjective_answer_permission.answer_status IS '作答状态：0未发布 1已发布';
COMMENT ON COLUMN subjective_answer_permission.comment_visible IS '评论可见性：0仅见问题 1全可见';
COMMENT ON COLUMN subjective_answer_permission.answered_at IS '答案发布时间';

CREATE INDEX idx_sap_student ON subjective_answer_permission(student_id);
CREATE INDEX idx_sap_question ON subjective_answer_permission(question_id);
-- 作业问答表（学生针对作业题目提问，教师回复）
CREATE TABLE IF NOT EXISTS homework_questions_discussion (
    id BIGSERIAL PRIMARY KEY,
    homework_id BIGINT NOT NULL,
    question_id BIGINT DEFAULT NULL,
    student_id BIGINT NOT NULL,
    question_content TEXT NOT NULL,
    teacher_reply TEXT DEFAULT NULL,
    replied_by BIGINT DEFAULT NULL,
    replied_at TIMESTAMP DEFAULT NULL,
    status VARCHAR(20) DEFAULT 'pending',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
COMMENT ON TABLE homework_questions_discussion IS '作业问答表';
COMMENT ON COLUMN homework_questions_discussion.homework_id IS '作业ID';
COMMENT ON COLUMN homework_questions_discussion.question_id IS '题目ID（可选，针对具体题目提问）';
COMMENT ON COLUMN homework_questions_discussion.student_id IS '学生ID';
COMMENT ON COLUMN homework_questions_discussion.question_content IS '问题内容';
COMMENT ON COLUMN homework_questions_discussion.teacher_reply IS '教师回复';
COMMENT ON COLUMN homework_questions_discussion.replied_by IS '回复教师ID';
COMMENT ON COLUMN homework_questions_discussion.replied_at IS '回复时间';
COMMENT ON COLUMN homework_questions_discussion.status IS '状态：pending/answered';

CREATE INDEX idx_hqd_homework ON homework_questions_discussion(homework_id);
CREATE INDEX idx_hqd_student ON homework_questions_discussion(student_id);
CREATE INDEX idx_hqd_status ON homework_questions_discussion(status);
-- 主观题评论表
CREATE TABLE IF NOT EXISTS subjective_comments (
    id BIGSERIAL PRIMARY KEY,
    question_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    parent_id BIGINT DEFAULT NULL,
    content TEXT NOT NULL,
    is_answer SMALLINT DEFAULT 0,
    is_top SMALLINT DEFAULT 0,
    like_count INT DEFAULT 0,
    status SMALLINT DEFAULT 1,
    answer_status VARCHAR(20) DEFAULT 'pending',
    answered_at TIMESTAMP DEFAULT NULL,
    answered_by BIGINT DEFAULT NULL,
    course_id BIGINT DEFAULT NULL,
    chapter_id BIGINT DEFAULT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
COMMENT ON TABLE subjective_comments IS '主观题评论表';
COMMENT ON COLUMN subjective_comments.question_id IS '题目ID';
COMMENT ON COLUMN subjective_comments.user_id IS '用户ID';
COMMENT ON COLUMN subjective_comments.parent_id IS '父评论ID';
COMMENT ON COLUMN subjective_comments.content IS '评论内容';
COMMENT ON COLUMN subjective_comments.is_answer IS '是否为答案：0否 1是';
COMMENT ON COLUMN subjective_comments.is_top IS '是否置顶：0否 1是';
COMMENT ON COLUMN subjective_comments.like_count IS '点赞数';
COMMENT ON COLUMN subjective_comments.status IS '状态：1正常 0删除';
COMMENT ON COLUMN subjective_comments.answer_status IS '回答状态：pending/answered/follow_up';
COMMENT ON COLUMN subjective_comments.answered_at IS '回答时间';
COMMENT ON COLUMN subjective_comments.answered_by IS '回答人ID';
COMMENT ON COLUMN subjective_comments.course_id IS '关联课程ID';
COMMENT ON COLUMN subjective_comments.chapter_id IS '关联章节ID';

CREATE INDEX idx_sc_question_id ON subjective_comments(question_id);
CREATE INDEX idx_sc_user_id ON subjective_comments(user_id);
CREATE INDEX idx_sc_parent_id ON subjective_comments(parent_id);
CREATE INDEX idx_sc_answer_status ON subjective_comments(answer_status);
CREATE INDEX idx_sc_course_id ON subjective_comments(course_id);
CREATE INDEX idx_sc_chapter_id ON subjective_comments(chapter_id);
-- =====================================================
-- 6. 徽章与成就表
-- =====================================================
-- 徽章定义表
CREATE TABLE IF NOT EXISTS badges (
    id SERIAL PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    description VARCHAR(500) DEFAULT NULL,
    icon VARCHAR(100) DEFAULT NULL,
    condition_type VARCHAR(50) NOT NULL,
    condition_value INT DEFAULT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
COMMENT ON TABLE badges IS '徽章定义表';
COMMENT ON COLUMN badges.name IS '徽章名称';
COMMENT ON COLUMN badges.description IS '徽章描述';
COMMENT ON COLUMN badges.icon IS '图标';
COMMENT ON COLUMN badges.condition_type IS '获取条件类型';
COMMENT ON COLUMN badges.condition_value IS '条件值';
-- 徽章初始化数据
INSERT INTO badges (name, description, icon, condition_type, condition_value)
VALUES ('学习新手', '完成第一个章节学习', 'star', 'chapter_complete', 1),
    ('勤奋学员', '累计学习7天', 'fire', 'study_days', 7),
    ('知识达人', '完成10个章节学习', 'trophy', 'chapter_complete', 10),
    ('满分王者', '获得一次满分', 'crown', 'perfect_score', 1),
    ('坚持不懈', '连续学习30天', 'medal', 'study_days', 30)
ON CONFLICT DO NOTHING;
-- 学生徽章获得表
CREATE TABLE IF NOT EXISTS student_badges (
    id BIGSERIAL PRIMARY KEY,
    student_id BIGINT NOT NULL,
    badge_id INT NOT NULL,
    earned_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_student_badge UNIQUE (student_id, badge_id),
    CONSTRAINT fk_sb_student FOREIGN KEY (student_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT fk_sb_badge FOREIGN KEY (badge_id) REFERENCES badges(id) ON DELETE CASCADE
);
COMMENT ON TABLE student_badges IS '学生徽章获得表';
COMMENT ON COLUMN student_badges.student_id IS '学生ID';
COMMENT ON COLUMN student_badges.badge_id IS '徽章ID';
COMMENT ON COLUMN student_badges.earned_at IS '获得时间';

CREATE INDEX idx_student_badges_student ON student_badges(student_id);
CREATE INDEX idx_student_badges_badge ON student_badges(badge_id);
-- =====================================================
-- 7. 通知表
-- =====================================================
-- 系统通知表
CREATE TABLE IF NOT EXISTS notifications (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    title VARCHAR(200) NOT NULL,
    content TEXT DEFAULT NULL,
    type VARCHAR(20) DEFAULT 'system' CHECK (type IN ('system', 'course', 'homework', 'comment')),
    is_read SMALLINT DEFAULT 0,
    related_id BIGINT DEFAULT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_notification_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);
COMMENT ON TABLE notifications IS '系统通知表';
COMMENT ON COLUMN notifications.user_id IS '接收用户ID';
COMMENT ON COLUMN notifications.title IS '通知标题';
COMMENT ON COLUMN notifications.content IS '通知内容';
COMMENT ON COLUMN notifications.type IS '通知类型';
COMMENT ON COLUMN notifications.is_read IS '是否已读：0未读 1已读';
COMMENT ON COLUMN notifications.related_id IS '关联ID';

CREATE INDEX idx_notifications_user ON notifications(user_id);
CREATE INDEX idx_notifications_read ON notifications(is_read);
-- =====================================================
-- 8. 管理员功能表
-- =====================================================
-- 审计日志表
CREATE TABLE IF NOT EXISTS audit_logs (
    id BIGSERIAL PRIMARY KEY,
    operator_id BIGINT NOT NULL,
    operator_name VARCHAR(50) NOT NULL,
    action_type VARCHAR(50) NOT NULL,
    target_type VARCHAR(50) NOT NULL,
    target_id BIGINT DEFAULT NULL,
    target_name VARCHAR(100) DEFAULT NULL,
    details TEXT DEFAULT NULL,
    ip_address VARCHAR(50) DEFAULT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
COMMENT ON TABLE audit_logs IS '审计日志表';
COMMENT ON COLUMN audit_logs.id IS '日志ID';
COMMENT ON COLUMN audit_logs.operator_id IS '操作人ID';
COMMENT ON COLUMN audit_logs.operator_name IS '操作人用户名';
COMMENT ON COLUMN audit_logs.action_type IS '操作类型：USER_ENABLE/USER_DISABLE/USER_DELETE/COURSE_APPROVE/COURSE_REJECT/COURSE_OFFLINE';
COMMENT ON COLUMN audit_logs.target_type IS '目标类型：USER/COURSE/ANNOUNCEMENT';
COMMENT ON COLUMN audit_logs.target_id IS '目标ID';
COMMENT ON COLUMN audit_logs.target_name IS '目标名称';
COMMENT ON COLUMN audit_logs.details IS '操作详情';
COMMENT ON COLUMN audit_logs.ip_address IS 'IP地址';
COMMENT ON COLUMN audit_logs.created_at IS '创建时间';

CREATE INDEX idx_audit_logs_action_type ON audit_logs(action_type);
CREATE INDEX idx_audit_logs_operator_id ON audit_logs(operator_id);
CREATE INDEX idx_audit_logs_target_type ON audit_logs(target_type);
CREATE INDEX idx_audit_logs_created_at ON audit_logs(created_at);
-- 系统公告表
CREATE TABLE IF NOT EXISTS announcements (
    id BIGSERIAL PRIMARY KEY,
    title VARCHAR(200) NOT NULL,
    content TEXT NOT NULL,
    target_audience VARCHAR(20) DEFAULT 'ALL',
    course_id BIGINT DEFAULT NULL,
    status VARCHAR(20) DEFAULT 'DRAFT',
    is_pinned SMALLINT DEFAULT 0,
    publish_time TIMESTAMP DEFAULT NULL,
    expire_time TIMESTAMP DEFAULT NULL,
    read_count INT DEFAULT 0,
    created_by BIGINT NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
COMMENT ON TABLE announcements IS '系统公告表';
COMMENT ON COLUMN announcements.id IS '公告ID';
COMMENT ON COLUMN announcements.title IS '公告标题';
COMMENT ON COLUMN announcements.content IS '公告内容';
COMMENT ON COLUMN announcements.target_audience IS '目标受众：ALL/TEACHER/STUDENT';
COMMENT ON COLUMN announcements.course_id IS '关联课程ID（NULL表示全局公告）';
COMMENT ON COLUMN announcements.status IS '状态：DRAFT/SCHEDULED/PUBLISHED/EXPIRED';
COMMENT ON COLUMN announcements.is_pinned IS '是否置顶：0否 1是';
COMMENT ON COLUMN announcements.publish_time IS '发布时间（定时发布）';
COMMENT ON COLUMN announcements.expire_time IS '过期时间';
COMMENT ON COLUMN announcements.read_count IS '阅读次数';
COMMENT ON COLUMN announcements.created_by IS '创建人ID';
COMMENT ON COLUMN announcements.created_at IS '创建时间';
COMMENT ON COLUMN announcements.updated_at IS '更新时间';

CREATE INDEX idx_announcements_status ON announcements(status);
CREATE INDEX idx_announcements_publish_time ON announcements(publish_time);
CREATE INDEX idx_announcements_target_audience ON announcements(target_audience);
CREATE INDEX idx_announcements_created_by ON announcements(created_by);
CREATE INDEX idx_announcements_course_id ON announcements(course_id);
-- 公告阅读记录表
CREATE TABLE IF NOT EXISTS announcement_reads (
    id BIGSERIAL PRIMARY KEY,
    announcement_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    read_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_announcement_user UNIQUE (announcement_id, user_id)
);
COMMENT ON TABLE announcement_reads IS '公告阅读记录表';
COMMENT ON COLUMN announcement_reads.id IS '记录ID';
COMMENT ON COLUMN announcement_reads.announcement_id IS '公告ID';
COMMENT ON COLUMN announcement_reads.user_id IS '用户ID';
COMMENT ON COLUMN announcement_reads.read_at IS '阅读时间';

CREATE INDEX idx_announcement_reads_announcement ON announcement_reads(announcement_id);
CREATE INDEX idx_announcement_reads_user ON announcement_reads(user_id);
-- =====================================================
-- 9. 教学日历表
-- =====================================================
-- 教学事件表
CREATE TABLE IF NOT EXISTS teaching_events (
    id BIGSERIAL PRIMARY KEY,
    teacher_id BIGINT NOT NULL,
    title VARCHAR(200) NOT NULL,
    description TEXT DEFAULT NULL,
    event_type VARCHAR(50) NOT NULL,
    start_time TIMESTAMP NOT NULL,
    end_time TIMESTAMP DEFAULT NULL,
    all_day SMALLINT DEFAULT 0,
    course_id BIGINT DEFAULT NULL,
    homework_id BIGINT DEFAULT NULL,
    color VARCHAR(20) DEFAULT '#3498db',
    reminder_minutes INT DEFAULT NULL,
    is_recurring SMALLINT DEFAULT 0,
    recurrence_rule VARCHAR(200) DEFAULT NULL,
    status VARCHAR(20) DEFAULT 'active',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
COMMENT ON TABLE teaching_events IS '教学事件表';
COMMENT ON COLUMN teaching_events.id IS '事件ID';
COMMENT ON COLUMN teaching_events.teacher_id IS '教师ID';
COMMENT ON COLUMN teaching_events.title IS '事件标题';
COMMENT ON COLUMN teaching_events.description IS '事件描述';
COMMENT ON COLUMN teaching_events.event_type IS '事件类型：homework/exam/meeting/reminder/other';
COMMENT ON COLUMN teaching_events.start_time IS '开始时间';
COMMENT ON COLUMN teaching_events.end_time IS '结束时间';
COMMENT ON COLUMN teaching_events.all_day IS '是否全天事件';
COMMENT ON COLUMN teaching_events.course_id IS '关联课程ID';
COMMENT ON COLUMN teaching_events.homework_id IS '关联作业ID';
COMMENT ON COLUMN teaching_events.color IS '事件颜色';
COMMENT ON COLUMN teaching_events.reminder_minutes IS '提前提醒分钟数';
COMMENT ON COLUMN teaching_events.is_recurring IS '是否重复事件';
COMMENT ON COLUMN teaching_events.recurrence_rule IS '重复规则（iCal格式）';
COMMENT ON COLUMN teaching_events.status IS '状态：active/cancelled/completed';

CREATE INDEX idx_teaching_events_teacher ON teaching_events(teacher_id);
CREATE INDEX idx_teaching_events_start_time ON teaching_events(start_time);
CREATE INDEX idx_teaching_events_course ON teaching_events(course_id);
-- =====================================================
-- 10. 评论系统表
-- =====================================================
-- 章节评论表
CREATE TABLE IF NOT EXISTS chapter_comments (
    id BIGSERIAL PRIMARY KEY,
    chapter_id BIGINT NOT NULL,
    course_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    parent_id BIGINT DEFAULT NULL,
    content TEXT NOT NULL,
    like_count INT DEFAULT 0,
    reply_count INT DEFAULT 0,
    is_pinned SMALLINT DEFAULT 0,
    status SMALLINT DEFAULT 1,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
COMMENT ON TABLE chapter_comments IS '章节评论表';
COMMENT ON COLUMN chapter_comments.id IS '评论ID';
COMMENT ON COLUMN chapter_comments.chapter_id IS '章节ID';
COMMENT ON COLUMN chapter_comments.course_id IS '课程ID';
COMMENT ON COLUMN chapter_comments.user_id IS '用户ID';
COMMENT ON COLUMN chapter_comments.parent_id IS '父评论ID';
COMMENT ON COLUMN chapter_comments.content IS '评论内容';
COMMENT ON COLUMN chapter_comments.like_count IS '点赞数';
COMMENT ON COLUMN chapter_comments.reply_count IS '回复数';
COMMENT ON COLUMN chapter_comments.is_pinned IS '是否置顶';
COMMENT ON COLUMN chapter_comments.status IS '状态：1正常 0删除';
COMMENT ON COLUMN chapter_comments.created_at IS '创建时间';
COMMENT ON COLUMN chapter_comments.updated_at IS '更新时间';

CREATE INDEX idx_chapter_comments_chapter ON chapter_comments(chapter_id);
CREATE INDEX idx_chapter_comments_course ON chapter_comments(course_id);
CREATE INDEX idx_chapter_comments_user ON chapter_comments(user_id);
CREATE INDEX idx_chapter_comments_parent ON chapter_comments(parent_id);
CREATE INDEX idx_chapter_comments_created ON chapter_comments(created_at);
-- 评论点赞表
CREATE TABLE IF NOT EXISTS comment_likes (
    id BIGSERIAL PRIMARY KEY,
    comment_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_comment_user UNIQUE (comment_id, user_id)
);
COMMENT ON TABLE comment_likes IS '评论点赞表';
COMMENT ON COLUMN comment_likes.id IS '点赞ID';
COMMENT ON COLUMN comment_likes.comment_id IS '评论ID';
COMMENT ON COLUMN comment_likes.user_id IS '用户ID';
COMMENT ON COLUMN comment_likes.created_at IS '创建时间';

CREATE INDEX idx_comment_likes_comment ON comment_likes(comment_id);
CREATE INDEX idx_comment_likes_user ON comment_likes(user_id);
-- 禁言用户表
CREATE TABLE IF NOT EXISTS muted_users (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    course_id BIGINT DEFAULT NULL,
    muted_by BIGINT DEFAULT NULL,
    operator_id BIGINT DEFAULT NULL,
    reason VARCHAR(500) DEFAULT NULL,
    muted_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    mute_until TIMESTAMP DEFAULT NULL,
    unmuted_at TIMESTAMP DEFAULT NULL,
    status SMALLINT DEFAULT 1,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
COMMENT ON TABLE muted_users IS '禁言用户表';
COMMENT ON COLUMN muted_users.id IS '禁言记录ID';
COMMENT ON COLUMN muted_users.user_id IS '被禁言用户ID';
COMMENT ON COLUMN muted_users.course_id IS '课程ID（NULL表示全局禁言）';
COMMENT ON COLUMN muted_users.muted_by IS '禁言操作人ID（现行业务字段）';
COMMENT ON COLUMN muted_users.operator_id IS '操作人ID（历史兼容字段）';
COMMENT ON COLUMN muted_users.reason IS '禁言原因';
COMMENT ON COLUMN muted_users.muted_at IS '禁言开始时间（现行业务字段）';
COMMENT ON COLUMN muted_users.mute_until IS '禁言截止时间（NULL表示永久）';
COMMENT ON COLUMN muted_users.unmuted_at IS '解除禁言时间（现行业务字段）';
COMMENT ON COLUMN muted_users.status IS '状态：1生效 0已解除';
COMMENT ON COLUMN muted_users.created_at IS '创建时间';
COMMENT ON COLUMN muted_users.updated_at IS '更新时间';

CREATE INDEX idx_muted_users_user ON muted_users(user_id);
CREATE INDEX idx_muted_users_course ON muted_users(course_id);
CREATE INDEX idx_muted_users_status ON muted_users(status);

-- muted_users 历史兼容迁移（幂等）
-- 迁移说明：适配旧版仅包含 operator_id/mute_until 的表结构
ALTER TABLE IF EXISTS muted_users
    ADD COLUMN IF NOT EXISTS muted_by BIGINT,
    ADD COLUMN IF NOT EXISTS muted_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    ADD COLUMN IF NOT EXISTS unmuted_at TIMESTAMP DEFAULT NULL;

ALTER TABLE IF EXISTS muted_users
    ALTER COLUMN operator_id DROP NOT NULL;

-- 历史数据回填（仅在旧版数据存在时生效）
UPDATE muted_users
SET muted_by = operator_id
WHERE muted_by IS NULL
  AND operator_id IS NOT NULL;

UPDATE muted_users
SET muted_at = COALESCE(created_at, CURRENT_TIMESTAMP)
WHERE muted_at IS NULL;

-- 屏蔽词表
CREATE TABLE IF NOT EXISTS blocked_words (
    id BIGSERIAL PRIMARY KEY,
    word VARCHAR(100) NOT NULL,
    scope VARCHAR(20) DEFAULT 'global',
    course_id BIGINT DEFAULT NULL,
    created_by BIGINT DEFAULT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_word_scope UNIQUE (word, scope, course_id)
);
COMMENT ON TABLE blocked_words IS '屏蔽词表';
COMMENT ON COLUMN blocked_words.id IS '屏蔽词ID';
COMMENT ON COLUMN blocked_words.word IS '屏蔽词';
COMMENT ON COLUMN blocked_words.scope IS '作用范围：global/course';
COMMENT ON COLUMN blocked_words.course_id IS '课程ID（scope=course时有效）';
COMMENT ON COLUMN blocked_words.created_by IS '创建人ID';
COMMENT ON COLUMN blocked_words.created_at IS '创建时间';

CREATE INDEX idx_blocked_words_scope ON blocked_words(scope);
CREATE INDEX idx_blocked_words_course ON blocked_words(course_id);
-- 屏蔽词初始化数据
INSERT INTO blocked_words (word, scope)
VALUES ('广告', 'global'),
    ('代写', 'global'),
    ('作弊', 'global'),
    ('答案', 'global'),
    ('枪手', 'global'),
    ('代考', 'global'),
    ('刷题', 'global'),
    ('买卖', 'global'),
    ('联系方式', 'global'),
    ('微信', 'global'),
    ('QQ', 'global')
ON CONFLICT (word, scope, course_id) DO NOTHING;
