-- LangChain4j default embedding table (COMBINED_JSON metadata) + hybrid STORED tsvector + GIN.
-- Placeholders: spring.flyway.placeholders.* in application.yaml (must stay aligned with app.pgvector.*).

CREATE EXTENSION IF NOT EXISTS vector;

CREATE TABLE IF NOT EXISTS ${pgvector_table} (
    embedding_id UUID PRIMARY KEY,
    embedding vector(${pgvector_dimension}),
    text TEXT NULL,
    metadata JSON NULL
);

ALTER TABLE ${pgvector_table}
    ADD COLUMN IF NOT EXISTS ${pgvector_tsv_column} tsvector
    GENERATED ALWAYS AS (to_tsvector('${pgvector_textsearch}', coalesce(text, ''))) STORED;

CREATE INDEX IF NOT EXISTS ${pgvector_gin_index}
    ON ${pgvector_table} USING gin (${pgvector_tsv_column});
