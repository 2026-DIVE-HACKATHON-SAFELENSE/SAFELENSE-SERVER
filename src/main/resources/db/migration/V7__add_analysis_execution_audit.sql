-- 분석 실행의 멱등 키와 입력 스냅샷을 저장하는 감사 필드
ALTER TABLE analysis_results
    ADD COLUMN idempotency_key VARCHAR(100) NULL,
    ADD COLUMN input_snapshot TEXT NULL;
