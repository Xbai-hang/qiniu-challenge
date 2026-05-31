# 语音日历 AI Native

语音日历 AI Native 是一个以语音和自然语言为入口的智能日历管理 Web App。项目支持个人/组织日历空间、日程管理、AI 助手、语音输入、提醒通知、操作日志和企业协作字段，适合作为 AI Native 日历产品原型或课程/挑战赛项目基础。

## 技术栈

| 模块 | 技术/工具 | 说明 |
|---|---|---|
| 前端框架 | Vue 3、TypeScript、Vite | 单页应用开发、类型约束和本地开发构建 |
| 前端 UI | Element Plus、@element-plus/icons-vue | 弹窗、消息提示、图标和基础交互组件 |
| 前端路由 | Vue Router | 页面路由、登录态访问控制 |
| 后端框架 | Java 17、Spring Boot 3.3 | REST API、业务服务、配置管理 |
| 后端安全 | Spring Security、JWT | 登录认证、接口鉴权、会话令牌 |
| 数据访问 | Spring JDBC | 轻量 SQL 数据访问 |
| 数据库 | MySQL 8.x | 用户、日程、组织、AI 会话、提醒等数据存储 |
| 数据库迁移 | Flyway | 启动后自动执行 `db/migration` 下的迁移脚本 |
| AI 能力 | OpenAI-compatible API、Mock/扩展客户端 | 自然语言日历助手、工具调用、确认与撤销流程 |
| 语音能力 | Speech-to-Text、TTS、浏览器录音 API | 语音转文字、语音对话、文本转语音播放 |
| 实时通知 | WebSocket | 日程提醒消息实时推送 |
| 构建工具 | Maven、npm | 后端打包测试、前端依赖安装和构建 |
| 容器化 | Docker、Docker Compose | 本地演示环境编排 |

## 已实现功能

### 账号与空间

- 用户注册、登录、退出和 JWT 鉴权。
- 个人日历空间自动加载。
- 企业/组织创建、加入、成员管理和角色调整。
- 不同日历空间之间切换查看和管理日程。

### 日历与日程管理

- 日程创建、编辑、删除、查询和搜索。
- 月历、列表、表格、甘特视图多模式展示。
- 月历日期拖拽改期，保留原始开始时间和持续时长。
- 月份缩略图和单日 24h 时间轴，点击某一天后展示当天安排。
- 右侧单日安排面板支持展开/收起。
- 支持项目、负责人、状态、优先级、标签等企业字段。
- 支持参与人、地点、描述、备注等基础日程信息。
- 支持日程冲突检测，保存前提示并允许确认继续。
- 支持未来 7 天聚合、冲突洞察和重点日程提示。

### AI 助手

- AI 对话入口，可通过自然语言查询、创建和调整日程。
- 支持 AI 工具调用，例如创建事件、搜索事件、检查冲突等。
- 高风险操作支持确认/拒绝流程。
- 支持撤销最近一次 AI 操作。
- 支持 AI 会话列表、消息历史和工具调用日志。
- 未配置真实模型时，部分语音/文本能力可使用 mock 流程辅助演示；真实 AI 日历助手建议配置 OpenAI-compatible 模型服务。

### 语音能力

- 浏览器录音输入。
- 语音转文字后可直接进入 AI 日历助手流程。
- 支持语音消息展示和播放。
- 支持 TTS 合成接口和缓存表。

### 提醒与通知

- 日程提醒创建、查询、修改、取消和稍后提醒。
- 通知中心展示提醒消息。
- WebSocket 实时推送未读通知。
- 通知已读、未读统计和提醒稍后处理。

### 审计与运维

- 操作日志查询。
- 操作日志导出。
- 健康检查接口。
- 前后端构建和测试命令。

## 目录结构

```text
.
├── backend/              # Spring Boot 后端
├── db/                   # Flyway 数据库迁移脚本和说明
├── docs/                 # PRD、API、数据库设计、前端设计和提交规范
├── frontend/             # Vue 3 前端
├── docker-compose.yml    # 本地 Docker Compose 编排
└── README.md
```

## 本地开发启动

### 1. 准备环境

需要提前安装：

| 工具 | 建议版本 |
|---|---|
| JDK | 17+ |
| Maven | 3.9+ |
| Node.js | 22+ |
| npm | 随 Node.js 安装 |
| MySQL | 8.x |

### 2. 创建数据库

```sql
CREATE DATABASE qiniu_challenge
  DEFAULT CHARACTER SET utf8mb4
  DEFAULT COLLATE utf8mb4_unicode_ci;
```

后端启动时会通过 Flyway 自动执行 `db/migration` 下的迁移脚本。

### 3. 配置环境变量

在仓库根目录创建 `.env` 文件：

```properties
DB_URL=jdbc:mysql://localhost:3306/qiniu_challenge?useUnicode=true&characterEncoding=UTF8&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true&useSSL=false
DB_USERNAME=你的数据库用户名
DB_PASSWORD=你的数据库密码

JWT_SECRET=qiniu-challenge-dev-secret-change-me
ACCESS_TOKEN_TTL_SECONDS=86400

AI_PROVIDER=openai-compatible
AI_BASE_URL=https://your-relay.example.com/v1
AI_API_KEY=your_api_key
AI_MODEL=your_model_name

SPEECH_PROVIDER=mock
TTS_PROVIDER=mock
```

如果暂时不接真实语音服务，可以保留 `SPEECH_PROVIDER=mock` 和 `TTS_PROVIDER=mock`。

如果暂时不接真实 AI 模型，可以将 `AI_PROVIDER=mock`，但 mock AI 客户端会返回 AI 服务不可用提示；完整 AI 日历助手能力建议配置 OpenAI-compatible 模型服务。

### 4. 启动后端

```bash
cd backend
mvn spring-boot:run
```

后端默认地址：

```text
http://localhost:8080
```

健康检查：

```text
http://localhost:8080/api/health
```

### 5. 启动前端

另开一个终端：

```bash
cd frontend
npm install
npm run dev
```

前端默认地址：

```text
http://localhost:5173
```

前端默认通过 `/api` 访问后端。若需要指定后端地址，可以在 `frontend/.env.local` 中配置：

```properties
VITE_API_BASE_URL=http://localhost:8080/api
```

## Docker Compose 启动

仓库提供了 `docker-compose.yml`，包含 MySQL、后端和前端服务。使用 Compose 启动前，请确认后端容器能拿到数据库连接配置。

可以在 `docker-compose.yml` 的 `backend.environment` 下补充：

```yaml
DB_URL: jdbc:mysql://mysql:3306/qiniu_challenge?useUnicode=true&characterEncoding=UTF8&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true&useSSL=false
DB_USERNAME: qiniu
DB_PASSWORD: qiniu_password
AI_PROVIDER: openai-compatible
AI_BASE_URL: https://your-relay.example.com/v1
AI_API_KEY: your_api_key
AI_MODEL: your_model_name
SPEECH_PROVIDER: mock
TTS_PROVIDER: mock
```

然后在仓库根目录执行：

```bash
docker compose up --build
```

服务端口：

| 服务 | 地址 |
|---|---|
| 前端 | `http://localhost:5173` |
| 后端 | `http://localhost:8080` |
| MySQL | `127.0.0.1:3306` |

Compose 默认数据库信息：

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

停止并删除 MySQL 数据卷：

```bash
docker compose down -v
```

## 常用验证命令

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

## 主要接口入口

| 能力 | 接口前缀 |
|---|---|
| 健康检查 | `/api/health` |
| 登录注册 | `/api/auth` |
| 当前用户 | `/api/users` |
| 日历空间 | `/api/spaces` |
| 组织管理 | `/api/organizations` |
| 日程管理 | `/api/events` |
| 提醒与通知 | `/api/events/{eventId}/reminders`、`/api/reminders`、`/api/notifications` |
| AI 助手 | `/api/ai` |
| 语音识别 | `/api/speech` |
| 文本转语音 | `/api/tts` |
| 操作日志 | `/api/operation-logs` |
| 通知 WebSocket | `/ws/notifications?token=<jwt>` |

更完整的接口说明见 [docs/API接口文档.md](docs/API接口文档.md)。
