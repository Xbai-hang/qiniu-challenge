DROP TABLE IF EXISTS event_participants;
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
