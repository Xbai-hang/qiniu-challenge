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
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  INDEX idx_conversations_user_time (user_id, created_at),
  INDEX idx_conversations_space_time (calendar_space_id, created_at),
  CONSTRAINT fk_conversations_user FOREIGN KEY (user_id) REFERENCES users (id),
  CONSTRAINT fk_conversations_space FOREIGN KEY (calendar_space_id) REFERENCES calendar_spaces (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE ai_messages (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  conversation_id BIGINT NOT NULL,
  user_id BIGINT NOT NULL,
  role VARCHAR(32) NOT NULL,
  input_mode VARCHAR(32) NULL,
  content TEXT NOT NULL,
  structured_payload JSON NULL,
  transcription_id BIGINT NULL,
  ai_prompt_version VARCHAR(64) NULL,
  tool_schema_version VARCHAR(64) NULL,
  model_provider VARCHAR(64) NULL,
  model_name VARCHAR(128) NULL,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  INDEX idx_messages_conversation_time (conversation_id, created_at),
  INDEX idx_messages_user_time (user_id, created_at),
  INDEX idx_messages_transcription (transcription_id),
  CONSTRAINT fk_messages_conversation FOREIGN KEY (conversation_id) REFERENCES ai_conversations (id),
  CONSTRAINT fk_messages_user FOREIGN KEY (user_id) REFERENCES users (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
