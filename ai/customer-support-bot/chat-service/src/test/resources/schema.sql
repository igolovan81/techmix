CREATE TABLE IF NOT EXISTS conversations (
    id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    session_id VARCHAR(255) NOT NULL,
    outcome    VARCHAR(50)  NOT NULL,
    turn_count INT          NOT NULL,
    started_at TIMESTAMP    NOT NULL,
    ended_at   TIMESTAMP    NOT NULL
);
