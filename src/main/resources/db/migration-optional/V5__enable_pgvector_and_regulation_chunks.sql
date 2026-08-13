-- PROVISIONAL — 참고용 migration. src/main/resources/db/migration/ (Flyway 활성 위치)에는
-- 없다 — 이 환경(로컬 Windows PostgreSQL)에 pgvector extension이 설치돼 있지 않아서다.
-- 실제 적용 방법은 docs/PROVISIONAL_BACKEND_DECISIONS.md §9(pgvector optional 활성화 방식) 참고:
--   1) pgvector extension을 설치한다.
--   2) 이 파일을 src/main/resources/db/migration/V5__enable_pgvector_and_regulation_chunks.sql로 옮긴다.
--   3) RAG_ENABLED=true 환경변수를 설정한다.
-- 세 단계를 함께 하지 않으면 PgVectorRagRetriever가 활성화되지 않거나(RAG_ENABLED만 켠 경우)
-- Flyway가 기동 시점에 실패한다(extension 없이 이 파일만 활성 위치로 옮긴 경우).

CREATE EXTENSION IF NOT EXISTS vector;

-- DB_IMPLEMENTATION_GUIDE.md §33 컬럼 정의 그대로. embedding 차원 768은 03_API계약.md §6
-- "임베딩은 768차원으로 충분하다"를 따른다.
CREATE TABLE regulation_chunks (
    chunk_key    VARCHAR(50)     NOT NULL,
    celex        VARCHAR(30)     NOT NULL,
    doc_short    VARCHAR(50)     NOT NULL,
    cite         VARCHAR(255)    NOT NULL,
    article_no   INTEGER         NOT NULL,
    title        VARCHAR(255)    NOT NULL,
    body         TEXT            NOT NULL,
    embedding    VECTOR(768)     NOT NULL,
    built_at     TIMESTAMPTZ     NOT NULL DEFAULT now(),
    CONSTRAINT pk_regulation_chunks PRIMARY KEY (chunk_key)
);

CREATE INDEX idx_regulation_chunks_embedding
    ON regulation_chunks USING ivfflat (embedding vector_cosine_ops) WITH (lists = 100);
