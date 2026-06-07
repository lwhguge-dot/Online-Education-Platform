# 编辑器配置说明

本文件定义了项目的编辑器配置规范，确保所有开发者使用一致的代码风格。

## 配置说明

### 基本配置

```ini
root = true

[*]
indent_style = space
indent_size = 2
end_of_line = lf
charset = utf-8
trim_trailing_whitespace = true
insert_final_newline = true
```

### 文件类型特定配置

```ini
# Java/XML/YAML/SQL 文件使用 4 空格缩进
[*.{java,xml,yml,yaml,properties,sql}]
indent_size = 4

# Markdown 文件不删除尾部空格（用于换行）
[*.md]
trim_trailing_whitespace = false
```

## 支持的编辑器

以下编辑器支持 `.editorconfig` 配置：

- **Visual Studio Code**: 安装 [EditorConfig for VS Code](https://marketplace.visualstudio.com/items?itemName=EditorConfig.EditorConfig) 插件
- **IntelliJ IDEA**: 内置支持
- **WebStorm**: 内置支持
- **Sublime Text**: 安装 [EditorConfig](https://packagecontrol.io/packages/EditorConfig) 插件
- **Atom**: 安装 [editorconfig](https://atom.io/packages/editorconfig) 插件
- **Vim**: 安装 [editorconfig-vim](https://github.com/editorconfig/editorconfig-vim) 插件
- **Emacs**: 安装 [editorconfig-emacs](https://github.com/editorconfig/editorconfig-emacs) 插件

## 代码风格

### 前端 (Vue/TypeScript/CSS)

- 缩进：2 空格
- 换行符：LF
- 字符编码：UTF-8
- 尾部空格：删除
- 文件末尾：添加换行符

### 后端 (Java/XML/YAML)

- 缩进：4 空格
- 换行符：LF
- 字符编码：UTF-8
- 尾部空格：删除
- 文件末尾：添加换行符

### 文档 (Markdown)

- 缩进：2 空格
- 换行符：LF
- 字符编码：UTF-8
- 尾部空格：保留（用于换行）
- 文件末尾：添加换行符

## 自动格式化

### 前端

```bash
# ESLint 自动修复
npm run lint:fix

# Prettier 格式化
npx prettier --write .
```

### 后端

```bash
# Maven 格式化
mvn spotless:apply
```

## 常见问题

### 1. 编辑器不生效

确保安装了对应的 EditorConfig 插件，并重启编辑器。

### 2. 格式化不一致

检查编辑器的格式化设置，确保优先使用 EditorConfig 配置。

### 3. 特定文件例外

如果需要为特定文件设置例外，可以在文件中添加注释：

```java
// editorconfig-off
// 特殊格式化代码
// editorconfig-on
```

## 相关链接

- [EditorConfig 官网](https://editorconfig.org/)
- [EditorConfig 规范](https://editorconfig.org/#file-format-details)
