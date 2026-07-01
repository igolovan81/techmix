--liquibase formatted sql
--changeset chat:1
CREATE TABLE IF NOT EXISTS conversations (
    id          BIGSERIAL    PRIMARY KEY,
    session_id  TEXT         NOT NULL,
    outcome     TEXT         NOT NULL,
    turn_count  INT          NOT NULL,
    started_at  TIMESTAMPTZ  NOT NULL,
    ended_at    TIMESTAMPTZ  NOT NULL
);
