-- Create Post table if it does not exist
CREATE TABLE IF NOT EXISTS POST
(
    id         BIGSERIAL PRIMARY KEY,
    title      VARCHAR(255),
    content    TEXT,
    created_at TIMESTAMP,
    updated_at TIMESTAMP
);
