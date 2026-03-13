-- ============================================================
-- 고객 원본 데이터 테이블 (INPUT)
-- Batch가 읽어서 처리할 소스 테이블
-- ============================================================
CREATE TABLE IF NOT EXISTS customers
(
    id         BIGSERIAL PRIMARY KEY,
    name       VARCHAR(100) NOT NULL,
    email      VARCHAR(200),
    phone      VARCHAR(20),
    age        INTEGER,
    status     VARCHAR(20) NOT NULL DEFAULT 'PENDING', -- PENDING | PROCESSED | SKIPPED
    created_at TIMESTAMP            DEFAULT NOW()
);

-- ============================================================
-- 정제된 고객 데이터 테이블 (OUTPUT)
-- Batch 처리 결과가 저장되는 타겟 테이블
-- ============================================================
CREATE TABLE IF NOT EXISTS customers_processed
(
    id           BIGSERIAL PRIMARY KEY,
    customer_id  BIGINT       NOT NULL, -- 원본 customers.id 참조
    name         VARCHAR(100) NOT NULL,
    email        VARCHAR(200),
    phone        VARCHAR(20),
    age          INTEGER,
    processed_at TIMESTAMP DEFAULT NOW()
);
