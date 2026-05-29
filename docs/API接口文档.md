# 语音日历 AI Native API 接口文档

## 1. 通用约定

### 1.1 Base URL

```text
/api
```

### 1.2 鉴权

除注册、登录、健康检查外，所有接口默认需要 JWT。

```http
Authorization: Bearer <access_token>
```

后端必须从 JWT 认证上下文获取当前用户，不能信任前端传入的 `userId`。

### 1.3 时间格式

- 请求和响应使用 ISO 8601 字符串。
- 第一版业务时区固定为 `Asia/Shanghai`。
- 示例：`2026-05-29T10:00:00+08:00`。

### 1.4 通用成功响应

```json
{
  "success": true,
  "data": {},
  "requestId": "req_123"
}
```

### 1.5 通用错误响应

```json
{
  "success": false,
  "error": {
    "code": "FORBIDDEN",
    "message": "无权访问该资源",
    "details": {}
  },
  "requestId": "req_123"
}
```

### 1.6 常用错误码

| code | 含义 |
|---|---|
| BAD_REQUEST | 参数错误 |
| UNAUTHORIZED | 未登录或 token 无效 |
| FORBIDDEN | 权限不足 |
| NOT_FOUND | 资源不存在 |
| CONFLICT | 业务冲突 |
| CONFIRMATION_REQUIRED | 需要确认 |
| AI_SERVICE_UNAVAILABLE | AI 服务不可用 |
| SPEECH_SERVICE_UNAVAILABLE | 语音识别不可用 |
| TTS_SERVICE_UNAVAILABLE | TTS 不可用 |

## 2. 健康检查

### GET /api/health

返回后端服务状态。

响应：

```json
{
  "success": true,
  "data": {
    "status": "UP",
    "version": "0.1.0",
    "time": "2026-05-29T10:00:00+08:00"
  }
}
```

## 3. 认证与用户

### POST /api/auth/register

注册用户，并自动创建个人日历空间。

请求：

```json
{
  "username": "alice",
  "email": "alice@example.com",
  "displayName": "Alice",
  "password": "Password123!"
}
```

响应：

```json
{
  "success": true,
  "data": {
    "user": {
      "id": 1,
      "username": "alice",
      "displayName": "Alice"
    },
    "accessToken": "jwt_token",
    "defaultSpaceId": 1001
  }
}
```

### POST /api/auth/login

登录并签发 JWT。

请求：

```json
{
  "account": "alice",
  "password": "Password123!"
}
```

响应：

```json
{
  "success": true,
  "data": {
    "accessToken": "jwt_token",
    "user": {
      "id": 1,
      "username": "alice",
      "displayName": "Alice"
    }
  }
}
```

### POST /api/auth/logout

退出登录。JWT 第一版可由前端删除，后端保留接口用于兼容。

响应：

```json
{
  "success": true,
  "data": true
}
```

### GET /api/users/me

获取当前用户信息。

响应：

```json
{
  "success": true,
  "data": {
    "id": 1,
    "username": "alice",
    "email": "alice@example.com",
    "displayName": "Alice"
  }
}
```

## 4. 日历空间

### GET /api/spaces

查询当前用户可访问的日历空间。

响应：

```json
{
  "success": true,
  "data": [
    {
      "id": 1001,
      "type": "personal",
      "name": "Alice 的个人日历",
      "role": "owner"
    },
    {
      "id": 2001,
      "type": "organization",
      "name": "Alpha 团队",
      "organizationId": 3001,
      "role": "admin"
    }
  ]
}
```

## 5. 组织与成员

### POST /api/organizations

创建组织，并自动创建企业日历空间。

请求：

```json
{
  "name": "Alpha 团队"
}
```

响应：

```json
{
  "success": true,
  "data": {
    "organizationId": 3001,
    "spaceId": 2001,
    "name": "Alpha 团队",
    "role": "owner",
    "inviteCode": "ALPHA123"
  }
}
```

### GET /api/organizations

查询我的组织。

响应：

```json
{
  "success": true,
  "data": [
    {
      "id": 3001,
      "name": "Alpha 团队",
      "role": "owner",
      "spaceId": 2001
    }
  ]
}
```

### GET /api/organizations/{organizationId}/members

查询组织成员。

响应：

```json
{
  "success": true,
  "data": [
    {
      "userId": 1,
      "displayName": "Alice",
      "nickname": "Alice",
      "title": "产品负责人",
      "role": "owner",
      "status": "active"
    }
  ]
}
```

### POST /api/organizations/{organizationId}/invite-code/refresh

刷新邀请码。需要 Owner 或 Admin。

响应：

```json
{
  "success": true,
  "data": {
    "inviteCode": "NEW123"
  }
}
```

### POST /api/organizations/join

通过邀请码加入组织。

请求：

```json
{
  "inviteCode": "ALPHA123"
}
```

响应：

```json
{
  "success": true,
  "data": {
    "organizationId": 3001,
    "spaceId": 2001,
    "role": "member"
  }
}
```

### PATCH /api/organizations/{organizationId}/members/{userId}/role

调整成员角色。第一版仅 Owner 可操作。

请求：

```json
{
  "role": "admin"
}
```

响应：

```json
{
  "success": true,
  "data": true
}
```

### DELETE /api/organizations/{organizationId}/members/{userId}

移除成员。

响应：

```json
{
  "success": true,
  "data": true
}
```

## 6. 日历事件

### POST /api/events

创建事件。

请求：

```json
{
  "calendarSpaceId": 1001,
  "title": "项目复盘",
  "startTime": "2026-05-30T10:00:00+08:00",
  "endTime": "2026-05-30T11:00:00+08:00",
  "location": "会议室 A",
  "description": "复盘本周进度",
  "participantUserIds": [1],
  "reminders": [
    {
      "offsetMinutes": 15
    }
  ],
  "repeat": {
    "repeatType": "none"
  },
  "enterpriseFields": {
    "project": "Alpha",
    "ownerUserId": 1,
    "status": "todo",
    "priority": "high",
    "tags": ["评审"],
    "eventType": "meeting"
  },
  "forceCreateOnConflict": false
}
```

成功响应：

```json
{
  "success": true,
  "data": {
    "event": {
      "id": 5001,
      "title": "项目复盘",
      "startTime": "2026-05-30T10:00:00+08:00",
      "endTime": "2026-05-30T11:00:00+08:00"
    },
    "conflicts": []
  }
}
```

存在冲突响应：

```json
{
  "success": false,
  "error": {
    "code": "CONFLICT",
    "message": "该时间段存在日程冲突",
    "details": {
      "conflicts": [
        {
          "eventId": 4999,
          "title": "客户沟通",
          "participantUserId": 1,
          "startTime": "2026-05-30T10:30:00+08:00",
          "endTime": "2026-05-30T11:30:00+08:00"
        }
      ]
    }
  }
}
```

### GET /api/events

按空间和时间范围查询事件。

查询参数：

| 参数 | 必填 | 说明 |
|---|---|---|
| calendarSpaceId | 是 | 空间 ID |
| start | 是 | 范围开始 |
| end | 是 | 范围结束 |
| keyword | 否 | 搜索关键词 |
| project | 否 | 企业字段筛选 |
| ownerUserId | 否 | 负责人筛选 |
| status | 否 | 状态筛选 |
| priority | 否 | 优先级筛选 |
| tag | 否 | 标签筛选 |
| sortBy | 否 | 排序字段 |
| groupBy | 否 | 分组字段 |

响应：

```json
{
  "success": true,
  "data": {
    "items": [
      {
        "id": 5001,
        "calendarSpaceId": 1001,
        "title": "项目复盘",
        "startTime": "2026-05-30T10:00:00+08:00",
        "endTime": "2026-05-30T11:00:00+08:00",
        "participants": [
          {
            "userId": 1,
            "displayName": "Alice",
            "role": "organizer"
          }
        ],
        "conflict": false,
        "enterpriseFields": {
          "project": "Alpha",
          "status": "todo",
          "priority": "high",
          "tags": ["评审"]
        }
      }
    ]
  }
}
```

### GET /api/events/{eventId}

查询事件详情。

### PATCH /api/events/{eventId}

更新事件。

请求：

```json
{
  "title": "项目复盘会",
  "startTime": "2026-05-30T10:30:00+08:00",
  "endTime": "2026-05-30T11:30:00+08:00",
  "participantUserIds": [1, 2],
  "enterpriseFields": {
    "status": "in_progress"
  },
  "forceUpdateOnConflict": false
}
```

响应同创建事件。

### DELETE /api/events/{eventId}

删除事件。手动删除可直接执行；AI 删除必须走确认机制。

响应：

```json
{
  "success": true,
  "data": true
}
```

### GET /api/events/search

搜索事件。

查询参数：

| 参数 | 必填 | 说明 |
|---|---|---|
| calendarSpaceId | 是 | 空间 ID |
| keyword | 是 | 关键词 |
| start | 否 | 范围开始 |
| end | 否 | 范围结束 |

## 7. 冲突检测

### POST /api/events/conflicts/check

检查事件冲突。

请求：

```json
{
  "calendarSpaceId": 2001,
  "eventId": 5001,
  "participantUserIds": [1, 2],
  "startTime": "2026-05-30T15:00:00+08:00",
  "endTime": "2026-05-30T16:00:00+08:00"
}
```

响应：

```json
{
  "success": true,
  "data": {
    "hasConflict": true,
    "conflicts": [
      {
        "eventId": 6001,
        "title": "客户沟通",
        "participantUserId": 2,
        "participantName": "张三",
        "startTime": "2026-05-30T15:30:00+08:00",
        "endTime": "2026-05-30T16:30:00+08:00"
      }
    ],
    "suggestedSlots": [
      {
        "startTime": "2026-05-30T16:00:00+08:00",
        "endTime": "2026-05-30T17:00:00+08:00",
        "reason": "所有参与人该时间段空闲"
      }
    ]
  }
}
```

## 8. 提醒与通知

### POST /api/events/{eventId}/reminders

创建事件提醒。

请求：

```json
{
  "offsetMinutes": 15,
  "userId": 1
}
```

响应：

```json
{
  "success": true,
  "data": {
    "id": 7001,
    "eventId": 5001,
    "triggerAt": "2026-05-30T09:45:00+08:00",
    "status": "pending"
  }
}
```

### GET /api/events/{eventId}/reminders

查询事件提醒。

### PATCH /api/reminders/{reminderId}

修改提醒。

请求：

```json
{
  "offsetMinutes": 30
}
```

### POST /api/reminders/{reminderId}/cancel

取消提醒。

### POST /api/reminders/{reminderId}/snooze

稍后提醒。

请求：

```json
{
  "minutes": 10
}
```

响应：

```json
{
  "success": true,
  "data": {
    "oldReminderId": 7001,
    "newReminderId": 7002,
    "triggerAt": "2026-05-30T10:10:00+08:00",
    "status": "pending"
  }
}
```

### GET /api/notifications

查询站内通知。

查询参数：

| 参数 | 必填 | 说明 |
|---|---|---|
| status | 否 | unread/read |
| page | 否 | 页码 |
| size | 否 | 每页数量 |

### POST /api/notifications/{notificationId}/read

标记通知已读。

## 9. AI 对话与工具调用

### POST /api/ai/chat

文本 AI 对话。非流式基础接口，可用于简单调用和测试。

请求：

```json
{
  "calendarSpaceId": 1001,
  "conversationId": 9001,
  "inputMode": "text",
  "message": "明天上午十点提醒我做项目复盘"
}
```

响应：

```json
{
  "success": true,
  "data": {
    "conversationId": 9001,
    "messageId": 9101,
    "reply": "已为你创建“项目复盘”，时间是明天上午 10:00。",
    "resultCard": {
      "type": "event_created",
      "eventId": 5001,
      "title": "项目复盘",
      "startTime": "2026-05-30T10:00:00+08:00",
      "actions": ["undo", "view_event"]
    },
    "toolCalls": [
      {
        "toolName": "create_event",
        "status": "succeeded"
      }
    ]
  }
}
```

### GET /api/ai/chat/stream

SSE 流式 AI 响应。

查询参数：

| 参数 | 必填 | 说明 |
|---|---|---|
| calendarSpaceId | 是 | 当前空间 |
| conversationId | 否 | 会话 ID |
| message | 是 | 用户输入文本 |
| inputMode | 是 | text/voice |

SSE 事件：

```text
message_delta
transcription_done
tool_call_started
tool_call_result
confirmation_required
view_action
final_result
error
```

事件示例：

```text
event: tool_call_started
data: {"toolName":"check_conflict","summary":"正在检查参与人时间冲突"}

event: final_result
data: {"reply":"已创建日程","resultCard":{"type":"event_created","eventId":5001}}
```

### GET /api/ai/conversations

查询当前用户 AI 会话列表。

### GET /api/ai/conversations/{conversationId}/messages

查询会话消息。

### GET /api/ai/tool-call-logs

查询工具调用日志。

查询参数：

| 参数 | 必填 | 说明 |
|---|---|---|
| conversationId | 否 | 会话 ID |
| calendarSpaceId | 否 | 空间 ID |
| page | 否 | 页码 |
| size | 否 | 每页数量 |

## 10. 待确认动作

### GET /api/ai/confirmations

查询当前用户待确认动作。

响应：

```json
{
  "success": true,
  "data": [
    {
      "id": 10001,
      "actionType": "delete_event",
      "riskLevel": "high",
      "summary": "确认删除明天的“产品评审”吗？",
      "expiresAt": "2026-05-29T10:10:00+08:00"
    }
  ]
}
```

### POST /api/ai/confirmations/{confirmationId}/confirm

确认执行。

响应：

```json
{
  "success": true,
  "data": {
    "status": "confirmed",
    "resultCard": {
      "type": "event_deleted",
      "eventId": 5001
    }
  }
}
```

### POST /api/ai/confirmations/{confirmationId}/reject

拒绝执行。

响应：

```json
{
  "success": true,
  "data": {
    "status": "rejected"
  }
}
```

## 11. AI 撤销

### POST /api/ai/undo-last

撤销最近一次可撤销 AI 写操作。

请求：

```json
{
  "calendarSpaceId": 1001
}
```

响应：

```json
{
  "success": true,
  "data": {
    "undone": true,
    "operationId": 12001,
    "summary": "已撤销刚才创建的“项目复盘”。"
  }
}
```

失败示例：

```json
{
  "success": false,
  "error": {
    "code": "CONFLICT",
    "message": "该事件已被其他操作修改，无法安全撤销"
  }
}
```

## 12. 语音识别

### POST /api/speech/transcribe

上传音频并转写文本。

请求：

```http
Content-Type: multipart/form-data

file: audio.webm
calendarSpaceId: 1001
conversationId: 9001
```

响应：

```json
{
  "success": true,
  "data": {
    "transcriptionId": 11001,
    "text": "明天上午十点提醒我做项目复盘",
    "provider": "openai-compatible",
    "confidence": 0.96,
    "durationMs": 3200
  }
}
```

### POST /api/speech/transcribe-and-chat

上传音频、转写并进入 AI Agent。

请求：

```http
Content-Type: multipart/form-data

file: audio.webm
calendarSpaceId: 1001
conversationId: 9001
```

响应与 `/api/ai/chat` 类似，额外包含转写结果。

## 13. TTS 语音合成

### POST /api/tts/synthesize

生成 AI 回复语音。

请求：

```json
{
  "messageId": 9101,
  "text": "已为你创建项目复盘，时间是明天上午十点。",
  "voice": "default"
}
```

响应：

```json
{
  "success": true,
  "data": {
    "ttsId": 13001,
    "audioUrl": "/api/tts/audio/13001",
    "expiresAt": "2026-05-29T10:30:00+08:00"
  }
}
```

### GET /api/tts/audio/{ttsId}

获取临时 TTS 音频流。

## 14. 前端视图状态与 AI 视图操作

### GET /api/view-state

查询当前用户在某空间的视图状态。

查询参数：

| 参数 | 必填 | 说明 |
|---|---|---|
| calendarSpaceId | 是 | 空间 ID |

### PUT /api/view-state

保存当前视图状态。第一版只保存最近状态，不做命名视图。

请求：

```json
{
  "calendarSpaceId": 2001,
  "viewMode": "table",
  "filters": {
    "project": "Alpha",
    "priority": "high"
  },
  "groupBy": "ownerUserId",
  "sortBy": [
    {
      "field": "startTime",
      "direction": "asc"
    }
  ],
  "highlightedEventIds": [5001, 5002]
}
```

响应：

```json
{
  "success": true,
  "data": true
}
```

AI 视图操作也可以通过 SSE `view_action` 下发给前端，不必每次落库。

## 15. 日志与审计

### GET /api/operation-logs

查询操作日志。

查询参数：

| 参数 | 必填 | 说明 |
|---|---|---|
| calendarSpaceId | 否 | 空间 ID |
| operationSource | 否 | ai/manual/system |
| targetType | 否 | event/reminder |
| page | 否 | 页码 |
| size | 否 | 每页数量 |

响应：

```json
{
  "success": true,
  "data": {
    "items": [
      {
        "id": 12001,
        "operationSource": "ai",
        "operationType": "create",
        "targetType": "event",
        "targetId": 5001,
        "undoable": true,
        "undone": false,
        "createdAt": "2026-05-29T10:00:00+08:00"
      }
    ],
    "page": 1,
    "size": 20,
    "total": 1
  }
}
```

## 16. WebSocket

### 连接地址

```text
/ws/notifications?token=<jwt>
```

### 服务端推送事件

#### reminder_notification

```json
{
  "type": "reminder_notification",
  "notificationId": 8001,
  "reminderId": 7001,
  "eventId": 5001,
  "title": "项目复盘即将开始",
  "content": "项目复盘将在 15 分钟后开始",
  "createdAt": "2026-05-30T09:45:00+08:00"
}
```

#### notification_read

```json
{
  "type": "notification_read",
  "notificationId": 8001
}
```

## 17. API 验收重点

- 所有受保护接口无 token 返回 401。
- 跨空间访问返回 403 或 404，不能泄露数据。
- Member 删除他人企业事件返回 403。
- AI 删除事件必须返回 confirmation_required 或生成待确认动作。
- 语音转写失败不影响文本输入接口。
- TTS 失败不影响 AI 文字结果。
- SSE 事件顺序可表达 AI 执行过程。
- WebSocket 只向目标用户推送提醒。

