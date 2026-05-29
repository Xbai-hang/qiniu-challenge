CREATE TABLE event_participants (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  event_id BIGINT NOT NULL,
  user_id BIGINT NOT NULL,
  role VARCHAR(32) NOT NULL DEFAULT 'attendee',
  response_status VARCHAR(32) NOT NULL DEFAULT 'needs_action',
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  CONSTRAINT uk_event_participant UNIQUE (event_id, user_id),
  INDEX idx_participants_user (user_id),
  INDEX idx_participants_event_role (event_id, role),
  CONSTRAINT fk_participants_event FOREIGN KEY (event_id) REFERENCES calendar_events (id),
  CONSTRAINT fk_participants_user FOREIGN KEY (user_id) REFERENCES users (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
