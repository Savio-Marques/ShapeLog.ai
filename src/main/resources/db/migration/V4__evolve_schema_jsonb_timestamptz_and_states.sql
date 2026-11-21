ALTER TABLE users_telegram
    ALTER COLUMN registered_at TYPE TIMESTAMPTZ USING registered_at AT TIME ZONE 'America/Sao_Paulo';

ALTER TABLE meals
    ALTER COLUMN created_at TYPE TIMESTAMPTZ USING created_at AT TIME ZONE 'America/Sao_Paulo';

ALTER TABLE workout_sessions
    ALTER COLUMN created_at TYPE TIMESTAMPTZ USING created_at AT TIME ZONE 'America/Sao_Paulo';

ALTER TABLE workout_sessions
    ALTER COLUMN exercises_json TYPE JSONB USING exercises_json::jsonb;

CREATE TABLE IF NOT EXISTS user_states (
    chat_id BIGINT NOT NULL PRIMARY KEY,
    state VARCHAR(255) NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_user_states_expires ON user_states (expires_at);

CREATE TABLE IF NOT EXISTS pending_inputs (
    chat_id BIGINT NOT NULL PRIMARY KEY,
    token VARCHAR(32) NOT NULL,
    text_content TEXT,
    user_message_id INTEGER,
    created_at TIMESTAMPTZ NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_pending_inputs_expires ON pending_inputs (expires_at);
