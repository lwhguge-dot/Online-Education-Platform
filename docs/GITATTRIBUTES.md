# Git 属性配置说明

本文件定义了项目的 Git 属性配置，确保跨平台协作时的文件处理一致性。

## 配置说明

### 基本配置

```gitattributes
# 统一默认行为：文本文件走规范化，优先按下方规则决定行尾
* text=auto
```

### 源码与配置（统一 LF）

```gitattributes
# Java/Kotlin/Groovy
*.java text eol=lf
*.kt text eol=lf
*.groovy text eol=lf

# Vue/TypeScript/JavaScript
*.vue text eol=lf
*.ts text eol=lf
*.tsx text eol=lf
*.js text eol=lf
*.jsx text eol=lf

# CSS/SCSS/Less
*.css text eol=lf
*.scss text eol=lf
*.less text eol=lf

# 配置文件
*.yml text eol=lf
*.yaml text eol=lf
*.json text eol=lf
*.xml text eol=lf
*.properties text eol=lf
*.sql text eol=lf

# 文档
*.md text eol=lf
*.txt text eol=lf

# 脚本
*.sh text eol=lf
*.dockerfile text eol=lf
Dockerfile text eol=lf
```

### Windows 脚本（统一 CRLF）

```gitattributes
# Windows 批处理脚本
*.bat text eol=crlf
*.cmd text eol=crlf

# PowerShell 脚本
*.ps1 text eol=crlf
```

### 常见二进制（禁止文本规范化）

```gitattributes
# 图片
*.png binary
*.jpg binary
*.jpeg binary
*.gif binary
*.ico binary

# 文档
*.pdf binary

# 压缩文件
*.zip binary
*.gz binary

# Java 归档
*.jar binary
*.war binary

# 字体
*.woff binary
*.woff2 binary
*.ttf binary
*.eot binary
```

## 配置目的

### 1. 跨平台行尾符统一

- **LF (Line Feed)**: Unix/Linux/macOS 行尾符
- **CRLF (Carriage Return + Line Feed)**: Windows 行尾符

通过配置，确保：
- 源码文件在仓库中统一使用 LF
- Windows 脚本在仓库中统一使用 CRLF
- 开发者在本地看到适合其操作系统的行尾符

### 2. 二进制文件保护

标记为 `binary` 的文件不会被 Git 进行文本规范化，避免：
- 图片文件损坏
- 压缩文件损坏
- 字体文件损坏

### 3. 文件类型识别

Git 使用 `.gitattributes` 配置来：
- 识别文件类型
- 决定是否进行文本规范化
- 应用正确的行尾符

## 常见问题

### 1. 行尾符不一致

**问题**: 不同开发者提交的代码行尾符不一致。

**解决方案**:
```bash
# 重新规范化行尾符
git add --renormalize .
git commit -m "chore: 规范化行尾符"
```

### 2. 二进制文件被修改

**问题**: 二进制文件被 Git 当作文本文件处理。

**解决方案**:
```gitattributes
# 在 .gitattributes 中添加
*.ext binary
```

### 3. 特定文件例外

**问题**: 某些文件需要特殊处理。

**解决方案**:
```gitattributes
# 为特定文件设置例外
path/to/file.ext binary
path/to/file.ext text eol=crlf
```

## 自动处理

### 克隆后

```bash
# 重新应用行尾符配置
git add --renormalize .
```

### 提交前

```bash
# 检查文件状态
git status

# 添加所有文件
git add .

# 提交
git commit -m "chore: 更新文件"
```

## 相关链接

- [Git 文档 - .gitattributes](https://git-scm.com/docs/gitattributes)
- [Git 文档 - 换行符处理](https://git-scm.com/book/en/v2/Customizing-Git-Git-Attributes)
