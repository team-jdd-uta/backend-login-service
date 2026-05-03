CREATE TABLE IF NOT EXISTS users (
    user_id VARCHAR(36) PRIMARY KEY,
    email VARCHAR(255) NOT NULL UNIQUE,
    cognito_sub VARCHAR(64) NULL,
    name VARCHAR(255) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    created_at TIMESTAMP NOT NULL
);

ALTER TABLE users ADD COLUMN IF NOT EXISTS cognito_sub VARCHAR(64) NULL;
CREATE UNIQUE INDEX IF NOT EXISTS idx_users_cognito_sub ON users (cognito_sub);
CREATE UNIQUE INDEX IF NOT EXISTS idx_users_name ON users (name);

CREATE TABLE IF NOT EXISTS outbox_event (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    event_id VARCHAR(36) NOT NULL UNIQUE,
    aggregate_type VARCHAR(100) NOT NULL,
    aggregate_id VARCHAR(36) NOT NULL,
    event_type VARCHAR(100) NOT NULL,
    payload JSON NOT NULL,
    status VARCHAR(20) NOT NULL,
    created_at BIGINT NOT NULL,
    published_at BIGINT NULL,
    INDEX idx_outbox_status_created_at (status, created_at)
);
