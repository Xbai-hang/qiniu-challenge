CREATE TABLE user_view_states (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  user_id BIGINT NOT NULL,
  calendar_space_id BIGINT NOT NULL,
  view_mode VARCHAR(32) NOT NULL,
  filters JSON NULL,
  group_by VARCHAR(64) NULL,
  sort_by JSON NULL,
  highlighted_event_ids JSON NULL,
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  CONSTRAINT uk_view_state_user_space UNIQUE (user_id, calendar_space_id),
  CONSTRAINT fk_view_states_user FOREIGN KEY (user_id) REFERENCES users (id),
  CONSTRAINT fk_view_states_space FOREIGN KEY (calendar_space_id) REFERENCES calendar_spaces (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE ai_suggestions (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  user_id BIGINT NOT NULL,
  calendar_space_id BIGINT NOT NULL,
  type VARCHAR(64) NOT NULL,
  title VARCHAR(200) NOT NULL,
  content TEXT NOT NULL,
  payload JSON NULL,
  status VARCHAR(32) NOT NULL DEFAULT 'active',
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  expires_at DATETIME(3) NULL,
  INDEX idx_suggestions_user_status (user_id, status, created_at),
  CONSTRAINT fk_suggestions_user FOREIGN KEY (user_id) REFERENCES users (id),
  CONSTRAINT fk_suggestions_space FOREIGN KEY (calendar_space_id) REFERENCES calendar_spaces (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
