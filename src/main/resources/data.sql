-- ============================================================
-- 실습용 샘플 데이터 (테이블이 비어있을 때만 삽입)
-- ============================================================
INSERT INTO customers (name, email, phone, age, status)
SELECT *
FROM (VALUES
         -- ✅ 정상 데이터 (처리 성공 예정)
         ('  홍길동  ', 'hong@EXAMPLE.COM', '010-1234-5678', 30, 'PENDING'),   -- 이름 공백, 이메일 대문자 → 정규화
         ('김철수', 'kim.cs@example.com', '011 9876 5432', 25, 'PENDING'),    -- 전화번호 공백 → 정규화
         ('최수진', 'choi@example.com', '010-3333-4444', 28, 'PENDING'),
         ('정도현', 'jung@example.com', '010-6666-7777', 32, 'PENDING'),
         ('강민서', 'kang@example.com', '010-8888-9999', 27, 'PENDING'),
         ('조예린', 'jo@example.com', '010-2222-3333', 31, 'PENDING'),
         ('윤서준', 'yoon@example.com', '010-4444-5555', 29, 'PENDING'),
         ('임지아', 'lim@example.com', '010-7777-8888', 26, 'PENDING'),

         -- ❌ 오류 데이터 (Skip 예정)
         ('이영희', 'INVALID_EMAIL', '010-5555-5555', 35, 'PENDING'),         -- 이메일 형식 오류 → Skip
         ('박민준', 'park@example.com', '010-1111-2222', -5, 'PENDING')        -- 나이 음수 → Skip
     ) AS v(name, email, phone, age, status)
WHERE NOT EXISTS (SELECT 1 FROM customers);
