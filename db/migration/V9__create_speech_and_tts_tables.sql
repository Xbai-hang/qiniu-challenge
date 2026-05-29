CREATE TABLE speech_transcriptions (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  user_id BIGINT NOT NULL,
  calendar_space_id BIGINT NOT NULL,
  conversation_id BIGINT NULL,
  provider VARCHAR(64) NOT NULL,
  model_name VARCHAR(128) NULL,
  transcript_text TEXT NOT NULL,
  confidence DECIMAL(5,4) NULL,
  audio_format VARCHAR(32) NULL,
  audio_duration_ms INT NULL,
  status VARCHAR(32) NOT NULL,
  error_code VARCHAR(64) NULL,
  error_message TEXT NULL,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  INDEX idx_transcriptions_user_time (user_id, created_at),
  INDEX idx_transcriptions_conversation (conversation_id),
  CONSTRAINT fk_transcriptions_user FOREIGN KEY (user_id) REFERENCES users (id),
  CONSTRAINT fk_transcriptions_space FOREIGN KEY (calendar_space_id) REFERENCES calendar_spaces (id),
  CONSTRAINT fk_transcriptions_conversation FOREIGN KEY (conversation_id) REFERENCES ai_conversations (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

ALTER TABLE ai_messages
  ADD CONSTRAINT fk_messages_transcription
  FOREIGN KEY (transcription_id) REFERENCES speech_transcriptions (id);

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
  expires_at DATETIME(3) NOT NULL,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  INDEX idx_tts_user_time (user_id, created_at),
  INDEX idx_tts_expire (expires_at),
  INDEX idx_tts_text_hash (text_hash),
  CONSTRAINT fk_tts_user FOREIGN KEY (user_id) REFERENCES users (id),
  CONSTRAINT fk_tts_message FOREIGN KEY (message_id) REFERENCES ai_messages (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
