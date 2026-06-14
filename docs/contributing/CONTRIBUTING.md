# 贡献指南

感谢您对智慧课堂在线教育平台项目的关注！我们欢迎任何形式的贡献。

## 如何贡献

### 1. 报告问题

如果您发现了 bug 或有功能建议，请通过以下方式提交：

1. 访问 [GitHub Issues](https://github.com/your-username/edu-platform/issues)
2. 点击 "New Issue" 按钮
3. 选择合适的模板（Bug 报告或功能请求）
4. 填写详细信息

### 2. 提交代码

#### 步骤 1: Fork 项目

```bash
# 在 GitHub 上 Fork 项目到您的账号
git clone https://github.com/your-username/edu-platform.git
cd edu-platform
```

#### 步骤 2: 创建分支

```bash
# 创建并切换到新分支
git checkout -b feature/your-feature-name

# 或者修复 bug
git checkout -b fix/your-bug-fix
```

#### 步骤 3: 修改代码

- 遵循项目的代码规范
- 添加必要的测试
- 更新相关文档

#### 步骤 4: 提交更改

```bash
# 添加修改的文件
git add .

# 提交更改
git commit -m "feat: 添加新功能描述"

# 推送到远程分支
git push origin feature/your-feature-name
```

#### 步骤 5: 创建 Pull Request

1. 访问您的 Fork 项目页面
2. 点击 "New Pull Request" 按钮
3. 选择目标分支（通常是 `main` 或 `develop`）
4. 填写 PR 描述
5. 等待代码审查

## 代码规范

### 前端规范

- 使用 TypeScript 严格模式
- 遵循 Vue 3 Composition API 规范
- 使用 Tailwind CSS 进行样式设计
- 运行 `npm run lint` 检查代码风格
- 运行 `npm run type-check` 检查类型

### 后端规范

- 使用 Java 21 特性
- 遵循 Spring Boot 最佳实践
- 使用 MyBatis-Plus 进行数据库操作
- 运行 `mvn compile` 检查编译
- 运行 `mvn test` 运行测试

### 提交信息规范

使用语义化提交信息：

- `feat: 新功能`
- `fix: 修复 bug`
- `docs: 文档更新`
- `style: 代码格式（不影响代码运行的变动）`
- `refactor: 重构（既不是增加功能，也不是修改bug的代码变动）`
- `perf: 性能优化`
- `test: 增加测试`
- `chore: 构建过程或辅助工具的变动`

## 开发环境

### 前端开发

```bash
cd frontend
npm install
npm run dev
```

### 后端开发

```bash
cd backend
mvn -T 1C clean test -s settings.xml
```

### Docker 开发

```bash
docker compose up -d --force-recreate
```

## 代码审查

所有提交的代码都需要经过审查：

1. 至少需要一名维护者审查
2. 通过所有 CI 检查
3. 没有合并冲突
4. 代码符合项目规范

## 行为准则

- 尊重他人
- 保持专业
- 欢迎新手
- 建设性反馈

## 联系方式

- 项目主页: https://github.com/your-username/edu-platform
- 问题反馈: https://github.com/your-username/edu-platform/issues
- 邮箱: your-email@example.com

## 许可证

本项目采用 MIT 许可证 - 详见 [LICENSE](../../LICENSE) 文件
