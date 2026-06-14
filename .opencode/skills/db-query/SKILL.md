---
name: db-query
description: Use when user says "查数据库", "看下数据", "SQL", "schema", "表结构". Query PostgreSQL, check schema, verify data, debug SQL issues.
---

# 数据库查询

> 触发条件：用户说"查数据库"、"看下数据"、"SQL"、"schema"、"表结构"等。

## 连接信息

```bash
# Docker 容器内执行
docker compose exec postgres psql -U postgres -d edu_platform

# 本机连接（如果端口映射）
psql -h 127.0.0.1 -p 5432 -U postgres -d edu_platform
```

## 常用查询

### 查看表结构
```sql
-- 所有表
SELECT tablename FROM pg_tables WHERE schemaname = 'public';

-- 表结构
\d table_name

-- 列信息
SELECT column_name, data_type, is_nullable 
FROM information_schema.columns 
WHERE table_name = 'table_name';
```

### 查看数据
```sql
-- 前 10 行
SELECT * FROM table_name LIMIT 10;

-- 行数
SELECT COUNT(*) FROM table_name;

-- 条件查询
SELECT * FROM table_name WHERE status = 1;
```

### 查看索引
```sql
SELECT indexname, indexdef 
FROM pg_indexes 
WHERE tablename = 'table_name';
```

### 查看外键
```sql
SELECT 
  tc.table_name, 
  kcu.column_name, 
  ccu.table_name AS foreign_table_name,
  ccu.column_name AS foreign_column_name
FROM information_schema.table_constraints AS tc
JOIN information_schema.key_column_usage AS kcu
  ON tc.constraint_name = kcu.constraint_name
JOIN information_schema.constraint_column_usage AS ccu
  ON ccu.constraint_name = tc.constraint_name
WHERE tc.constraint_type = 'FOREIGN KEY';
```

## DB 分区

本项目使用 schema 分区：
- `gateway_schema` (0) - Gateway 配置
- `user_schema` (1) - 用户服务
- `course_schema` (2) - 课程服务
- `homework_schema` (3) - 作业服务
- `progress_schema` (4) - 进度服务

```sql
-- 切换 schema
SET search_path TO user_schema;

-- 查看当前 schema
SHOW search_path;
```

## 常见问题

| 问题 | 排查 |
|------|------|
| 连接拒绝 | `docker compose ps postgres` 检查状态 |
| 表不存在 | 检查 schema 是否正确 |
| 权限错误 | `GRANT ALL ON ALL TABLES IN SCHEMA public TO postgres;` |
| 慢查询 | `EXPLAIN ANALYZE SELECT ...` |

## 行为准则

- 查询前先确认表名和 schema
- 生产环境禁止 `DELETE`/`UPDATE` 不带 `WHERE`
- 大表查询加 `LIMIT`
- 使用 `hindsight_retain` 记录重要数据发现
