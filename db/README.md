# Database Migrations

This directory contains the MySQL schema implementation for the Voice Calendar AI Native project.

## Layout

```text
db/
└── migration/
    ├── V1__init_users_and_auth.sql
    ├── V2__create_spaces_and_organizations.sql
    ├── V3__create_calendar_events.sql
    ├── V4__create_event_participants.sql
    ├── V5__create_reminders_and_notifications.sql
    ├── V6__create_ai_conversations.sql
    ├── V7__create_ai_task_and_confirmations.sql
    ├── V8__create_tool_and_operation_logs.sql
    ├── V9__create_speech_and_tts_tables.sql
    └── V10__create_view_state_and_suggestions.sql
```

The files are named with Flyway conventions and can be moved directly to a Spring Boot resource path such as:

```text
backend/src/main/resources/db/migration
```

## Recommended Database

```sql
CREATE DATABASE qiniu_challenge
  DEFAULT CHARACTER SET utf8mb4
  DEFAULT COLLATE utf8mb4_unicode_ci;
```

## Local Verification

When Flyway is available:

```bash
flyway \
  -url="jdbc:mysql://localhost:3306/qiniu_challenge?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai" \
  -user="root" \
  -password="<your-password>" \
  -locations="filesystem:db/migration" \
  migrate
```

Do not commit local database passwords or `.env` files.
