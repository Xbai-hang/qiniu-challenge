CREATE TABLE ai_task_states (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  conversation_id BIGINT NOT NULL,
  user_id BIGINT NOT NULL,
  calendar_space_id BIGINT NOT NULL,
  task_type VARCHAR(64) NOT NULL,
  status VARCHAR(64) NOT NULL,
  draft_payload JSON NULL,
  missing_fields JSON NULL,
  risk_level VARCHAR(32) NULL,
  expires_at DATETIME(3) NULL,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  INDEX idx_task_conversation_status (conversation_id, status),
  INDEX idx_task_user_status (user_id, status, updated_at),
  CONSTRAINT fk_task_conversation FOREIGN KEY (conversation_id) REFERENCES ai_conversations (id),
  CONSTRAINT fk_task_user FOREIGN KEY (user_id) REFERENCES users (id),
  CONSTRAINT fk_task_space FOREIGN KEY (calendar_space_id) REFERENCES calendar_spaces (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE pending_confirmations (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  conversation_id BIGINT NOT NULL,
  user_id BIGINT NOT NULL,
  calendar_space_id BIGINT NOT NULL,
  action_type VARCHAR(64) NOT NULL,
  risk_level VARCHAR(32) NOT NULL,
  summary TEXT NOT NULL,
  payload JSON NOT NULL,
  status VARCHAR(32) NOT NULL DEFAULT 'pending',
  expires_at DATETIME(3) NOT NULL,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  INDEX idx_confirmations_user_status (user_id, status, expires_at),
  INDEX idx_confirmations_conversation (conversation_id),
  CONSTRAINT fk_confirmations_conversation FOREIGN KEY (conversation_id) REFERENCES ai_conversations (id),
  CONSTRAINT fk_confirmations_user FOREIGN KEY (user_id) REFERENCES users (id),
  CONSTRAINT fk_confirmations_space FOREIGN KEY (calendar_space_id) REFERENCES calendar_spaces (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
