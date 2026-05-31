DROP TABLE IF EXISTS event_participants;
DROP TABLE IF EXISTS operation_logs;
DROP TABLE IF EXISTS ai_tool_call_logs;
DROP TABLE IF EXISTS pending_confirmations;
DROP TABLE IF EXISTS ai_task_states;
DROP TABLE IF EXISTS tts_cache;
DROP TABLE IF EXISTS ai_messages;
DROP TABLE IF EXISTS speech_transcriptions;
DROP TABLE IF EXISTS ai_conversations;
DROP TABLE IF EXISTS notifications;
DROP TABLE IF EXISTS event_reminders;
DROP TABLE IF EXISTS calendar_events;
DROP TABLE IF EXISTS calendar_spaces;
DROP TABLE IF EXISTS organization_members;
DROP TABLE IF EXISTS organizations;
DROP TABLE IF EXISTS users;

CREATE TABLE users (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  username VARCHAR(64) NOT NULL,
  email VARCHAR(128) NOT NULL,
  display_name VARCHAR(64) NOT NULL,
  password_hash VARCHAR(255) NOT NULL,
  avatar_url VARCHAR(512) NULL,
  status VARCHAR(32) NOT NULL DEFAULT 'active',
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  deleted_at DATETIME(3) NULL,
  CONSTRAINT uk_users_username UNIQUE (username),
  CONSTRAINT uk_users_email UNIQUE (email)
);

CREATE TABLE organizations (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  name VARCHAR(128) NOT NULL,
  invite_code VARCHAR(64) NOT NULL,
  created_by BIGINT NOT NULL,
  status VARCHAR(32) NOT NULL DEFAULT 'active',
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  deleted_at DATETIME(3) NULL,
  CONSTRAINT uk_organizations_invite_code UNIQUE (invite_code),
  CONSTRAINT fk_organizations_created_by FOREIGN KEY (created_by) REFERENCES users (id)
);

CREATE TABLE organization_members (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  organization_id BIGINT NOT NULL,
  user_id BIGINT NOT NULL,
  role VARCHAR(32) NOT NULL,
  nickname VARCHAR(64) NULL,
  title VARCHAR(64) NULL,
  status VARCHAR(32) NOT NULL DEFAULT 'active',
  joined_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  CONSTRAINT uk_org_member UNIQUE (organization_id, user_id),
  CONSTRAINT fk_org_members_organization FOREIGN KEY (organization_id) REFERENCES organizations (id),
  CONSTRAINT fk_org_members_user FOREIGN KEY (user_id) REFERENCES users (id)
);

CREATE TABLE calendar_spaces (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  type VARCHAR(32) NOT NULL,
  name VARCHAR(128) NOT NULL,
  owner_user_id BIGINT NULL,
  organization_id BIGINT NULL,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  deleted_at DATETIME(3) NULL,
  CONSTRAINT fk_spaces_owner_user FOREIGN KEY (owner_user_id) REFERENCES users (id),
  CONSTRAINT fk_spaces_organization FOREIGN KEY (organization_id) REFERENCES organizations (id)
);

CREATE TABLE calendar_events (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  calendar_space_id BIGINT NOT NULL,
  organization_id BIGINT NULL,
  created_by BIGINT NOT NULL,
  title VARCHAR(200) NOT NULL,
  description CLOB NULL,
  location VARCHAR(200) NULL,
  start_time TIMESTAMP(3) NOT NULL,
  end_time TIMESTAMP(3) NOT NULL,
  timezone VARCHAR(64) NOT NULL DEFAULT 'Asia/Shanghai',
  all_day BOOLEAN NOT NULL DEFAULT FALSE,
  visibility VARCHAR(32) NOT NULL DEFAULT 'space',
  source VARCHAR(32) NOT NULL DEFAULT 'manual',
  repeat_type VARCHAR(32) NOT NULL DEFAULT 'none',
  repeat_until TIMESTAMP(3) NULL,
  repeat_count INT NULL,
  repeat_rule_text CLOB NULL,
  project VARCHAR(128) NULL,
  owner_user_id BIGINT NULL,
  status VARCHAR(32) NULL,
  priority VARCHAR(32) NULL,
  tags CLOB NULL,
  event_type VARCHAR(64) NULL,
  notes CLOB NULL,
  custom_fields CLOB NULL,
  version INT NOT NULL DEFAULT 0,
  created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  deleted_at TIMESTAMP(3) NULL,
  CONSTRAINT fk_events_space FOREIGN KEY (calendar_space_id) REFERENCES calendar_spaces (id),
  CONSTRAINT fk_events_organization FOREIGN KEY (organization_id) REFERENCES organizations (id),
  CONSTRAINT fk_events_created_by FOREIGN KEY (created_by) REFERENCES users (id),
  CONSTRAINT fk_events_owner_user FOREIGN KEY (owner_user_id) REFERENCES users (id),
  CONSTRAINT ck_events_time_range CHECK (end_time > start_time)
);

CREATE INDEX idx_events_space_time ON calendar_events (calendar_space_id, start_time, end_time);
CREATE INDEX idx_events_created_by ON calendar_events (created_by);
CREATE INDEX idx_events_org_time ON calendar_events (organization_id, start_time);
CREATE INDEX idx_events_owner_user ON calendar_events (owner_user_id);
CREATE INDEX idx_events_project ON calendar_events (calendar_space_id, project);
CREATE INDEX idx_events_status ON calendar_events (calendar_space_id, status);
CREATE INDEX idx_events_priority ON calendar_events (calendar_space_id, priority);

CREATE TABLE event_participants (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  event_id BIGINT NOT NULL,
  user_id BIGINT NOT NULL,
  role VARCHAR(32) NOT NULL DEFAULT 'attendee',
  response_status VARCHAR(32) NOT NULL DEFAULT 'needs_action',
  created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  CONSTRAINT uk_event_participant UNIQUE (event_id, user_id),
  CONSTRAINT fk_participants_event FOREIGN KEY (event_id) REFERENCES calendar_events (id),
  CONSTRAINT fk_participants_user FOREIGN KEY (user_id) REFERENCES users (id)
);

CREATE INDEX idx_participants_user ON event_participants (user_id);
CREATE INDEX idx_participants_event_role ON event_participants (event_id, role);

CREATE TABLE ai_conversations (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  user_id BIGINT NOT NULL,
  calendar_space_id BIGINT NOT NULL,
  title VARCHAR(200) NULL,
  channel VARCHAR(32) NOT NULL,
  ai_prompt_version VARCHAR(64) NOT NULL,
  tool_schema_version VARCHAR(64) NOT NULL,
  model_provider VARCHAR(64) NULL,
  model_name VARCHAR(128) NULL,
  status VARCHAR(32) NOT NULL DEFAULT 'active',
  created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  CONSTRAINT fk_conversations_user FOREIGN KEY (user_id) REFERENCES users (id),
  CONSTRAINT fk_conversations_space FOREIGN KEY (calendar_space_id) REFERENCES calendar_spaces (id)
);

CREATE INDEX idx_conversations_user_time ON ai_conversations (user_id, created_at);
CREATE INDEX idx_conversations_space_time ON ai_conversations (calendar_space_id, created_at);

CREATE TABLE speech_transcriptions (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  user_id BIGINT NOT NULL,
  calendar_space_id BIGINT NOT NULL,
  conversation_id BIGINT NULL,
  provider VARCHAR(64) NOT NULL,
  model_name VARCHAR(128) NULL,
  transcript_text CLOB NOT NULL,
  confidence DECIMAL(5,4) NULL,
  audio_format VARCHAR(32) NULL,
  audio_duration_ms INT NULL,
  status VARCHAR(32) NOT NULL,
  error_code VARCHAR(64) NULL,
  error_message CLOB NULL,
  created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  CONSTRAINT fk_transcriptions_user FOREIGN KEY (user_id) REFERENCES users (id),
  CONSTRAINT fk_transcriptions_space FOREIGN KEY (calendar_space_id) REFERENCES calendar_spaces (id),
  CONSTRAINT fk_transcriptions_conversation FOREIGN KEY (conversation_id) REFERENCES ai_conversations (id)
);

CREATE INDEX idx_transcriptions_user_time ON speech_transcriptions (user_id, created_at);
CREATE INDEX idx_transcriptions_conversation ON speech_transcriptions (conversation_id);

CREATE TABLE ai_messages (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  conversation_id BIGINT NOT NULL,
  user_id BIGINT NOT NULL,
  role VARCHAR(32) NOT NULL,
  input_mode VARCHAR(32) NULL,
  content CLOB NOT NULL,
  structured_payload CLOB NULL,
  transcription_id BIGINT NULL,
  ai_prompt_version VARCHAR(64) NULL,
  tool_schema_version VARCHAR(64) NULL,
  model_provider VARCHAR(64) NULL,
  model_name VARCHAR(128) NULL,
  created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  CONSTRAINT fk_messages_conversation FOREIGN KEY (conversation_id) REFERENCES ai_conversations (id),
  CONSTRAINT fk_messages_user FOREIGN KEY (user_id) REFERENCES users (id),
  CONSTRAINT fk_messages_transcription FOREIGN KEY (transcription_id) REFERENCES speech_transcriptions (id)
);

CREATE INDEX idx_messages_conversation_time ON ai_messages (conversation_id, created_at);
CREATE INDEX idx_messages_user_time ON ai_messages (user_id, created_at);
CREATE INDEX idx_messages_transcription ON ai_messages (transcription_id);

CREATE TABLE tts_cache (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  user_id BIGINT NOT NULL,
  message_id BIGINT NULL,
  provider VARCHAR(64) NOT NULL,
  voice VARCHAR(64) NULL,
  text_hash VARCHAR(128) NOT NULL,
  audio_url VARCHAR(512) NULL,
  storage_key VARCHAR(512) NULL,
  status VARCHAR(32) NOT NULL,
  expires_at TIMESTAMP(3) NOT NULL,
  created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  CONSTRAINT fk_tts_user FOREIGN KEY (user_id) REFERENCES users (id),
  CONSTRAINT fk_tts_message FOREIGN KEY (message_id) REFERENCES ai_messages (id)
);

CREATE INDEX idx_tts_user_time ON tts_cache (user_id, created_at);
CREATE INDEX idx_tts_expire ON tts_cache (expires_at);
CREATE INDEX idx_tts_text_hash ON tts_cache (text_hash);

CREATE TABLE ai_task_states (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  conversation_id BIGINT NOT NULL,
  user_id BIGINT NOT NULL,
  calendar_space_id BIGINT NOT NULL,
  task_type VARCHAR(64) NOT NULL,
  status VARCHAR(64) NOT NULL,
  draft_payload CLOB NULL,
  missing_fields CLOB NULL,
  risk_level VARCHAR(32) NULL,
  expires_at TIMESTAMP(3) NULL,
  created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  CONSTRAINT fk_task_conversation FOREIGN KEY (conversation_id) REFERENCES ai_conversations (id),
  CONSTRAINT fk_task_user FOREIGN KEY (user_id) REFERENCES users (id),
  CONSTRAINT fk_task_space FOREIGN KEY (calendar_space_id) REFERENCES calendar_spaces (id)
);

CREATE INDEX idx_task_conversation_status ON ai_task_states (conversation_id, status);
CREATE INDEX idx_task_user_status ON ai_task_states (user_id, status, updated_at);

CREATE TABLE pending_confirmations (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  conversation_id BIGINT NOT NULL,
  user_id BIGINT NOT NULL,
  calendar_space_id BIGINT NOT NULL,
  action_type VARCHAR(64) NOT NULL,
  risk_level VARCHAR(32) NOT NULL,
  summary CLOB NOT NULL,
  payload CLOB NOT NULL,
  status VARCHAR(32) NOT NULL DEFAULT 'pending',
  expires_at TIMESTAMP(3) NOT NULL,
  created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  CONSTRAINT fk_confirmations_conversation FOREIGN KEY (conversation_id) REFERENCES ai_conversations (id),
  CONSTRAINT fk_confirmations_user FOREIGN KEY (user_id) REFERENCES users (id),
  CONSTRAINT fk_confirmations_space FOREIGN KEY (calendar_space_id) REFERENCES calendar_spaces (id)
);

CREATE INDEX idx_confirmations_user_status ON pending_confirmations (user_id, status, expires_at);
CREATE INDEX idx_confirmations_conversation ON pending_confirmations (conversation_id);

CREATE TABLE ai_tool_call_logs (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  conversation_id BIGINT NOT NULL,
  message_id BIGINT NULL,
  user_id BIGINT NOT NULL,
  calendar_space_id BIGINT NOT NULL,
  tool_name VARCHAR(128) NOT NULL,
  risk_level VARCHAR(32) NOT NULL,
  required_permission VARCHAR(128) NULL,
  input_payload CLOB NULL,
  output_payload CLOB NULL,
  status VARCHAR(32) NOT NULL,
  error_code VARCHAR(64) NULL,
  error_message CLOB NULL,
  started_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  finished_at TIMESTAMP(3) NULL,
  CONSTRAINT fk_tool_logs_conversation FOREIGN KEY (conversation_id) REFERENCES ai_conversations (id),
  CONSTRAINT fk_tool_logs_message FOREIGN KEY (message_id) REFERENCES ai_messages (id),
  CONSTRAINT fk_tool_logs_user FOREIGN KEY (user_id) REFERENCES users (id),
  CONSTRAINT fk_tool_logs_space FOREIGN KEY (calendar_space_id) REFERENCES calendar_spaces (id)
);

CREATE INDEX idx_tool_logs_conversation_time ON ai_tool_call_logs (conversation_id, started_at);
CREATE INDEX idx_tool_logs_user_time ON ai_tool_call_logs (user_id, started_at);
CREATE INDEX idx_tool_logs_tool_name ON ai_tool_call_logs (tool_name);

CREATE TABLE operation_logs (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  user_id BIGINT NOT NULL,
  calendar_space_id BIGINT NOT NULL,
  conversation_id BIGINT NULL,
  tool_call_id BIGINT NULL,
  operation_source VARCHAR(32) NOT NULL,
  operation_type VARCHAR(64) NOT NULL,
  target_type VARCHAR(64) NOT NULL,
  target_id BIGINT NULL,
  before_snapshot CLOB NULL,
  after_snapshot CLOB NULL,
  undoable BOOLEAN NOT NULL DEFAULT FALSE,
  undone BOOLEAN NOT NULL DEFAULT FALSE,
  undo_expires_at TIMESTAMP(3) NULL,
  created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  CONSTRAINT fk_operation_user FOREIGN KEY (user_id) REFERENCES users (id),
  CONSTRAINT fk_operation_space FOREIGN KEY (calendar_space_id) REFERENCES calendar_spaces (id),
  CONSTRAINT fk_operation_conversation FOREIGN KEY (conversation_id) REFERENCES ai_conversations (id),
  CONSTRAINT fk_operation_tool_call FOREIGN KEY (tool_call_id) REFERENCES ai_tool_call_logs (id)
);

CREATE INDEX idx_operation_user_time ON operation_logs (user_id, created_at);
CREATE INDEX idx_operation_space_time ON operation_logs (calendar_space_id, created_at);
CREATE INDEX idx_operation_undo ON operation_logs (user_id, operation_source, undoable, undone, undo_expires_at);
CREATE INDEX idx_operation_target ON operation_logs (target_type, target_id);

CREATE TABLE event_reminders (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  event_id BIGINT NOT NULL,
  calendar_space_id BIGINT NOT NULL,
  user_id BIGINT NOT NULL,
  offset_minutes INT NULL,
  trigger_at TIMESTAMP(3) NOT NULL,
  status VARCHAR(32) NOT NULL DEFAULT 'pending',
  snoozed_from_id BIGINT NULL,
  created_by BIGINT NOT NULL,
  created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  cancelled_at TIMESTAMP(3) NULL,
  CONSTRAINT fk_reminders_event FOREIGN KEY (event_id) REFERENCES calendar_events (id),
  CONSTRAINT fk_reminders_space FOREIGN KEY (calendar_space_id) REFERENCES calendar_spaces (id),
  CONSTRAINT fk_reminders_user FOREIGN KEY (user_id) REFERENCES users (id),
  CONSTRAINT fk_reminders_created_by FOREIGN KEY (created_by) REFERENCES users (id),
  CONSTRAINT fk_reminders_snoozed_from FOREIGN KEY (snoozed_from_id) REFERENCES event_reminders (id)
);

CREATE INDEX idx_reminders_trigger ON event_reminders (status, trigger_at);
CREATE INDEX idx_reminders_user_status ON event_reminders (user_id, status);
CREATE INDEX idx_reminders_event ON event_reminders (event_id);

CREATE TABLE notifications (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  user_id BIGINT NOT NULL,
  calendar_space_id BIGINT NOT NULL,
  reminder_id BIGINT NULL,
  type VARCHAR(64) NOT NULL,
  title VARCHAR(200) NOT NULL,
  content CLOB NULL,
  payload CLOB NULL,
  status VARCHAR(32) NOT NULL DEFAULT 'unread',
  pushed_at TIMESTAMP(3) NULL,
  read_at TIMESTAMP(3) NULL,
  created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  CONSTRAINT fk_notifications_user FOREIGN KEY (user_id) REFERENCES users (id),
  CONSTRAINT fk_notifications_space FOREIGN KEY (calendar_space_id) REFERENCES calendar_spaces (id),
  CONSTRAINT fk_notifications_reminder FOREIGN KEY (reminder_id) REFERENCES event_reminders (id)
);

CREATE INDEX idx_notifications_user_status ON notifications (user_id, status, created_at);
CREATE INDEX idx_notifications_reminder ON notifications (reminder_id);
