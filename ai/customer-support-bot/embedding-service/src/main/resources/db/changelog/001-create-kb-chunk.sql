--liquibase formatted sql
--changeset embedding:1
CREATE EXTENSION IF NOT EXISTS vector;

CREATE TABLE IF NOT EXISTS kb_chunk (
    id        BIGSERIAL PRIMARY KEY,
    title     TEXT NOT NULL,
    content   TEXT NOT NULL,
    embedding VECTOR(1536)
);

CREATE INDEX IF NOT EXISTS kb_chunk_embedding_idx
    ON kb_chunk USING ivfflat (embedding vector_cosine_ops)
    WITH (lists = 10);
