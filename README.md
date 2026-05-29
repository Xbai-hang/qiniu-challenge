# 语音日历 AI Native

语音日历 AI Native 是一个以语音和自然语言为入口的日历管理 Web App。当前仓库包含前端、后端、数据库迁移脚本和 Docker Compose 演示骨架。

## 技术栈

- 前端：Vue 3 + TypeScript + Vite + Element Plus
- 后端：Java 17 + Spring Boot 3 + Maven
- 数据库：MySQL 8
- 数据库迁移：Flyway 风格 SQL 脚本，位于 `db/migration`

## 目录结构

```text
.
├── backend/              # Spring Boot 后端
├── db/                   # 数据库迁移脚本和说明
├── docs/                 # PRD、API、数据库设计和提交规范
├── frontend/             # Vue 前端
├── docker-compose.yml    # 本地演示编排
└── README.md
```

## 本地开发启动

### 启动后端

```bash
cd backend
mvn spring-boot:run
```

后端默认监听：

```text
http://localhost:8080
```

### 启动前端

```bash
cd frontend
npm install
npm run dev
```

前端默认监听：

```text
http://localhost:5173
```

### 数据库

推荐本地数据库名：

```sql
CREATE DATABASE qiniu_challenge
  DEFAULT CHARACTER SET utf8mb4
  DEFAULT COLLATE utf8mb4_unicode_ci;
```

迁移脚本位于：

```text
db/migration
```

## Docker Compose 启动

在仓库根目录执行：

```bash
docker compose up --build
```

服务端口：

| 服务 | 地址 |
|---|---|
| 前端 | `http://localhost:5173` |
| 后端 | `http://localhost:8080` |
| MySQL | `localhost:3306` |

Compose 默认数据库连接信息：

| 配置 | 值 |
|---|---|
| 数据库 | `qiniu_challenge` |
| 用户名 | `qiniu` |
| 密码 | `qiniu_password` |
| root 密码 | `root_password` |

停止服务：

```bash
docker compose down
```

如需同时删除 MySQL 本地数据卷：

```bash
docker compose down -v
```

## 验证命令

前端构建：

```bash
cd frontend
npm run build
```

后端测试：

```bash
cd backend
mvn test
```

Docker Compose 配置检查：

```bash
docker compose config
```

